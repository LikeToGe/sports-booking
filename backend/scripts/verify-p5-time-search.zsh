#!/usr/bin/env zsh
set -euo pipefail

backend_dir=${0:A:h:h}
base='http://127.0.0.1:8091/api'
mysql_container="sports-booking-p5-time-search-runtime-mysql-$$"
redis_container="sports-booking-p5-time-search-runtime-redis-$$"
server_log=$(mktemp /tmp/sports-booking-p5-time-search-runtime.XXXXXX)
server_pid=''
media_root=$(mktemp -d /tmp/sports-booking-p5-time-search-media.XXXXXX)
profiles='demo,runtime-test'
ugc_enabled=true

cleanup() {
  [[ -n "$server_pid" ]] && kill "$server_pid" >/dev/null 2>&1 || true
  [[ -n "$server_pid" ]] && wait "$server_pid" >/dev/null 2>&1 || true
  docker rm -f "$mysql_container" "$redis_container" >/dev/null 2>&1 || true
  rm -rf "$media_root"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if lsof -nP -iTCP:8091 -sTCP:LISTEN >/dev/null 2>&1; then
  print -u2 '8091 端口已有服务，不能隔离验证'
  exit 1
fi

docker run --rm -d --name "$mysql_container" -p 127.0.0.1:33307:3306 \
  -e MYSQL_DATABASE=sports_booking -e MYSQL_USER=review_test \
  -e MYSQL_PASSWORD=review_test -e MYSQL_ROOT_PASSWORD=review_root mysql:8.4 >/dev/null
docker run --rm -d --name "$redis_container" -p 127.0.0.1:36379:6379 redis:7.4-alpine >/dev/null
for attempt in {1..40}; do
  docker exec "$mysql_container" mysqladmin ping -uroot -preview_root --silent >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$mysql_container" mysqladmin ping -uroot -preview_root --silent >/dev/null

env MYSQL_USER=review_test MYSQL_PASSWORD=review_test \
  APP_DB_URL='jdbc:mysql://127.0.0.1:33307/sports_booking?characterEncoding=utf8&serverTimezone=UTC' \
  REDIS_PORT=36379 APP_UGC_ENABLED="$ugc_enabled" APP_UGC_RETENTION_DAYS=30 APP_DEMO_SEED_ENABLED=true \
  APP_UGC_MOCK_DETECTION=PASS \
  APP_BOOTSTRAP_ADMIN_USERNAME=admin APP_BOOTSTRAP_ADMIN_PASSWORD=admin123 \
  APP_UPLOAD_DIRECTORY="$media_root/venue-uploads" \
  SPRING_PROFILES_ACTIVE="$profiles" \
  JAVA_TOOL_OPTIONS=-Djava.awt.headless=true \
  /Users/gjm/Library/Java/project-jdks/zulu17.66.19-ca-jdk17.0.19-macosx_aarch64/Contents/Home/bin/java \
  -jar "${backend_dir}/sports-booking-server/target/sports-booking-server.jar" --server.port=8091 \
  --app.ugc.detection-delay-ms=1000 --app.ugc.cleanup-delay-ms=1000 \
  > "$server_log" 2>&1 &
server_pid=$!

for attempt in {1..60}; do
  if curl -fsS http://127.0.0.1:8091/api/public/ugc-config 2>/dev/null \
      | jq -e --argjson expected "$ugc_enabled" '.data == $expected' >/dev/null 2>&1; then
    break
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    tail -40 "$server_log" >&2
    exit 1
  fi
  sleep 1
done
curl -fsS http://127.0.0.1:8091/api/public/ugc-config \
  | jq -e --argjson expected "$ugc_enabled" '.data == $expected' >/dev/null

# HTTP 已就绪时初始化 Runner 可能尚未建立演示账号，等待账号可登录。
platform_token=''
for attempt in {1..30}; do
  login_result=$(curl -sS -X POST http://127.0.0.1:8091/api/auth/admin/login \
    -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}')
  platform_token=$(print -r -- "$login_result" | jq -r '.data.token // empty')
  [[ -n "$platform_token" ]] && break
  sleep 1
done
[[ -n "$platform_token" ]] || { print -u2 '平台测试账号未就绪'; exit 1; }
# 平台登录早于 Demo Runner 完成；三笔演示预约建立后再写独立验收场地。
for attempt in {1..30}; do
  seeded=$(docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
    -e "SELECT COUNT(*) FROM s_booking WHERE source='OFFLINE'")
  [[ "$seeded" -ge 3 ]] && break
  sleep 1
done
[[ "$seeded" -ge 3 ]] || { print -u2 '演示初始化未完成'; exit 1; }
print 'P5 runtime: isolated HTTP server ready'
env P5_MYSQL_CONTAINER="$mysql_container" python3 "${backend_dir}/scripts/verify-p5-time-search.py"
VERIFY_API_BASE="$base" zsh "${backend_dir}/scripts/verify-p2.zsh"
VERIFY_API_BASE="$base" zsh "${backend_dir}/scripts/verify-p2-concurrency.zsh"

env P5_MYSQL_CONTAINER="$mysql_container" python3 "${backend_dir}/scripts/verify-p5-favourites.py"
env P5_MYSQL_CONTAINER="$mysql_container" python3 "${backend_dir}/scripts/verify-p5-slot-comparison.py"
env P5_MYSQL_CONTAINER="$mysql_container" python3 "${backend_dir}/scripts/verify-p5-district.py"
env P5_API_BASE="$base" P5_MYSQL_CONTAINER="$mysql_container" python3 "${backend_dir}/scripts/verify-p5-recruitment-discovery.py"
verification_status=0
if [[ "${P6_VERIFY:-false}" == true ]]; then
  for area in migrations reminders utilization customers; do
    if ! env P5_API_BASE="$base" P5_MYSQL_CONTAINER="$mysql_container" python3 "${backend_dir}/scripts/verify-p6-${area}.py"; then
      verification_status=1
      break
    fi
  done
fi
if [[ "${P5_PREVIEW:-false}" == true ]]; then
  print "Isolated preview ready: http://127.0.0.1:8091/api; verification_status=$verification_status"
  while true; do sleep 30; done
fi
exit "$verification_status"
