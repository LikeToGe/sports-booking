#!/usr/bin/env zsh
set -euo pipefail

backend_dir=${0:A:h:h}
base='http://127.0.0.1:8091/api'
mysql_container="sports-booking-p4-runtime-mysql-$$"
redis_container="sports-booking-p4-runtime-redis-$$"
server_log=$(mktemp /tmp/sports-booking-p4-runtime.XXXXXX)
server_pid=''
media_root=$(mktemp -d /tmp/sports-booking-p4-media.XXXXXX)
profiles='demo,runtime-test'
ugc_enabled="${P4_UGC_ENABLED:-true}"
[[ "${P4_DEMO_PROOF:-false}" == true ]] && profiles='demo'

cleanup() {
  [[ -n "$server_pid" ]] && kill "$server_pid" >/dev/null 2>&1 || true
  [[ -n "$server_pid" ]] && wait "$server_pid" >/dev/null 2>&1 || true
  docker rm -f "$mysql_container" "$redis_container" >/dev/null 2>&1 || true
  rm -rf "$media_root"
}
trap cleanup EXIT INT TERM

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
  APP_UGC_MOCK_DETECTION="${P4_DETECTION_OUTCOME:-PASS}" \
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
print 'P4 runtime: platform login passed'
if [[ "$ugc_enabled" == false ]]; then
  closed_token=$(curl -fsS -X POST "$base/auth/mini/mock-login" -H 'Content-Type: application/json' \
    -d '{"code":"p4-closed-gate"}' | jq -er '.data.token')
  curl -sS -H "X-Mini-Token: $closed_token" "$base/mini/posts/list" \
    | jq -e '.msg == "内容功能暂未开放"' >/dev/null
  curl -sS -X POST "$base/mini/posts/create" -H "X-Mini-Token: $closed_token" \
    -H 'Content-Type: application/json' -d '{"body":"不可发布","sportTypeId":1}' \
    | jq -e '.msg == "内容功能暂未开放"' >/dev/null
  curl -sS "$base/public/venues/1/reviews" | jq -e '.msg == "内容功能暂未开放"' >/dev/null
  curl -fsS -H "X-Admin-Token: $platform_token" "$base/platform/content-moderation/posts/list" \
    | jq -e '.code == 0' >/dev/null
  print 'P4 closed gate: public reads/writes denied and platform moderation remains accessible'
  exit 0
fi
if [[ "${P4_DEMO_PROOF:-false}" == true ]]; then
  for attempt in {1..30}; do
    seeded=$(docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
      -e "SELECT COUNT(*) FROM s_post WHERE id BETWEEN 910001 AND 910003")
    [[ "$seeded" == 3 ]] && break
    sleep 1
  done
  [[ "$seeded" == 3 ]] || { print -u2 'P4 Demo 未生成三家场馆动态'; exit 1; }
  docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
    -e "SELECT COUNT(*) FROM s_venue_review WHERE id BETWEEN 930001 AND 930003" | rg -q '^3$'
  docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
    -e "SELECT COUNT(*) FROM s_post_recruitment WHERE id BETWEEN 960001 AND 960003" | rg -q '^3$'
  curl -fsS -H "X-Admin-Token: $platform_token" "$base/platform/content-governance/reports" >/dev/null
  print 'P4 Demo: venue reviews, user/official posts, comments, governance cases, and recruitment generated'
  if [[ "${P4_PREVIEW:-false}" == true ]]; then
    print 'P4 preview backend: http://127.0.0.1:8091/api'
    while true; do sleep 30; done
  fi
  exit 0
fi
review_sport_id=$(docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
  -e 'SELECT cs.sport_type_id FROM s_venue v JOIN s_court c ON c.venue_id=v.id JOIN s_court_sport cs ON cs.court_id=c.id ORDER BY v.id,c.id,cs.sport_type_id LIMIT 1')
tag_id=$(curl -fsS -X POST http://127.0.0.1:8091/api/platform/review-tags \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"name\":\"场地整洁\",\"sportTypeIds\":[$review_sport_id],\"status\":\"ACTIVE\"}" | jq -er '.data.id')
curl -fsS "http://127.0.0.1:8091/api/public/review-tags?sportTypeId=$review_sport_id" \
  | jq -e '.data | any(.name == "场地整洁")' >/dev/null
curl -fsS -H "X-Admin-Token: $platform_token" \
  http://127.0.0.1:8091/api/platform/content-moderation/reviews | jq -e '.data == []' >/dev/null
print 'P4 runtime: tags and empty queue passed'

mini_login=$(curl -fsS -X POST http://127.0.0.1:8091/api/auth/mini/mock-login \
  -H 'Content-Type: application/json' -d '{"code":"p4-runtime-reviewer"}')
mini_token=$(print -r -- "$mini_login" | jq -er '.data.token')
mini_user_id=$(print -r -- "$mini_login" | jq -er '.data.profile.id')
print 'P4 runtime: mini login passed'
curl -sS -X POST http://127.0.0.1:8091/api/mini/posts/create \
  -H "X-Mini-Token: $mini_token" -H 'Content-Type: application/json' \
  -d '{"body":"尚未接受协议","sportTypeId":1}' \
  | jq -e '.msg == "请先接受当前社区规则与隐私说明"' >/dev/null
curl -fsS -H "X-Mini-Token: $mini_token" http://127.0.0.1:8091/api/mini/content/agreement \
  | jq -e '.data.accepted == false and .data.rulesVersion == "1" and .data.privacyVersion == "1"' >/dev/null
curl -fsS -X POST http://127.0.0.1:8091/api/mini/content/agreement \
  -H "X-Mini-Token: $mini_token" -H 'Content-Type: application/json' \
  -d '{"rulesVersion":"1","privacyVersion":"1"}' \
  | jq -e '.data.accepted == true' >/dev/null
if [[ "${P4_DETECTION_OUTCOME:-PASS}" != PASS ]]; then
  source "${backend_dir}/scripts/verify-p4-detection-checks.zsh"
  exit 0
fi
post_response=$(curl -fsS -X POST http://127.0.0.1:8091/api/mini/posts/create \
  -H "X-Mini-Token: $mini_token" -H 'Content-Type: application/json' \
  -d '{"body":"今天来打球","sportTypeId":1}')
post_id=$(print -r -- "$post_response" | jq -er '.data.id')
post_revision_id=$(print -r -- "$post_response" | jq -er '.data.revisionId')
old_post_revision_id=$post_revision_id
curl -fsS -H "X-Mini-Token: $mini_token" \
  http://127.0.0.1:8091/api/mini/posts/list \
  | jq -e --argjson id "$post_id" '.data | all(.id != $id)' >/dev/null
curl -fsS -H "X-Admin-Token: $platform_token" http://127.0.0.1:8091/api/platform/content-moderation/posts/list \
  | jq -e --argjson id "$post_id" --argjson rev "$post_revision_id" '.data | any(.reviewId == $id and .revisionId == $rev)' >/dev/null
curl -fsS -X PUT http://127.0.0.1:8091/api/platform/content-settings/update \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d '{"postEnabled":false,"commentEnabled":true,"reviewEnabled":true}' \
  | jq -e '.data.postEnabled == false' >/dev/null
curl -fsS -H "X-Mini-Token: $mini_token" http://127.0.0.1:8091/api/mini/posts/list \
  | jq -e --argjson id "$post_id" '.data | all(.id != $id)' >/dev/null
auto_post_id=$(curl -fsS -X POST http://127.0.0.1:8091/api/mini/posts/create \
  -H "X-Mini-Token: $mini_token" -H 'Content-Type: application/json' \
  -d '{"body":"关闭审核后的新动态","sportTypeId":1}' \
  | jq -er '.data | select(.moderationStatus == "APPROVED") | .id')
curl -fsS -H "X-Mini-Token: $mini_token" http://127.0.0.1:8091/api/mini/posts/list \
  | jq -e --argjson id "$auto_post_id" '.data | any(.id == $id)' >/dev/null
curl -fsS -X DELETE "http://127.0.0.1:8091/api/mini/posts/$auto_post_id" \
  -H "X-Mini-Token: $mini_token" | jq -e '.data == true' >/dev/null
curl -fsS -X PUT http://127.0.0.1:8091/api/platform/content-settings/update \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d '{"postEnabled":true,"commentEnabled":true,"reviewEnabled":true}' \
  | jq -e '.data.postEnabled == true' >/dev/null
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/posts/$post_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$post_revision_id,\"action\":\"APPROVE\",\"reason\":\"动态审核通过\"}" | jq -e '.data == true' >/dev/null
curl -fsS -H "X-Mini-Token: $mini_token" http://127.0.0.1:8091/api/mini/posts/list \
  | jq -e --argjson id "$post_id" '.data | any(.id == $id and .body == "今天来打球")' >/dev/null
post_update_response=$(curl -fsS -X PUT "http://127.0.0.1:8091/api/mini/posts/$post_id" \
  -H "X-Mini-Token: $mini_token" -H 'Content-Type: application/json' \
  -d '{"body":"今天来打球，场地很舒服","sportTypeId":1}')
post_revision_id=$(print -r -- "$post_update_response" | jq -er '.data.revisionId')
print -r -- "$post_update_response" | jq -e '.data.body == "今天来打球，场地很舒服"' >/dev/null
curl -fsS -H "X-Admin-Token: $platform_token" http://127.0.0.1:8091/api/platform/content-moderation/posts/list \
  | jq -e --argjson id "$post_id" '.data | any(.reviewId == $id and .publicBody == "今天来打球")' >/dev/null
curl -sS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/posts/$post_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$old_post_revision_id,\"action\":\"APPROVE\",\"reason\":\"过期版本\"}" \
  | jq -e '.code != 0' >/dev/null
curl -fsS -H "X-Mini-Token: $mini_token" "http://127.0.0.1:8091/api/mini/posts/$post_id" \
  | jq -e '.data.body == "今天来打球"' >/dev/null
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/posts/$post_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$post_revision_id,\"action\":\"HIDE\",\"reason\":\"新版待审期间隐藏\"}" \
  | jq -e '.data == true' >/dev/null
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/posts/$post_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$post_revision_id,\"action\":\"APPROVE\",\"reason\":\"动态修改审核通过\"}" | jq -e '.data == true' >/dev/null
curl -fsS -H "X-Mini-Token: $mini_token" http://127.0.0.1:8091/api/mini/posts/list \
  | jq -e --argjson id "$post_id" '.data | all(.id != $id)' >/dev/null
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/posts/$post_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$post_revision_id,\"action\":\"RESTORE\",\"reason\":\"新版通过后恢复\"}" \
  | jq -e '.data == true' >/dev/null
curl -fsS -H "X-Mini-Token: $mini_token" \
  "http://127.0.0.1:8091/api/mini/posts/$post_id" \
  | jq -e '.data.body == "今天来打球，场地很舒服"' >/dev/null
curl -sS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/posts/$post_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$post_revision_id,\"action\":\"APPROVE\",\"reason\":\"重复审核\"}" \
  | jq -e '.code != 0' >/dev/null
for action in HIDE RESTORE; do
  curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/posts/$post_id" \
    -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
    -d "{\"revisionId\":$post_revision_id,\"action\":\"$action\",\"reason\":\"运行态验证\"}" \
    | jq -e '.data == true' >/dev/null
  expected_visible=$([[ "$action" == HIDE ]] && print false || print true)
  curl -fsS -H "X-Mini-Token: $mini_token" http://127.0.0.1:8091/api/mini/posts/list \
    | jq -e --argjson id "$post_id" --argjson visible "$expected_visible" \
      '(.data | any(.id == $id)) == $visible' >/dev/null
done
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/posts/$post_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$post_revision_id,\"action\":\"DELETE\",\"reason\":\"运行态验证\"}" \
  | jq -e '.data == true' >/dev/null
curl -fsS -H "X-Mini-Token: $mini_token" \
  http://127.0.0.1:8091/api/mini/posts/list \
  | jq -e --argjson id "$post_id" '.data | all(.id != $id)' >/dev/null
print 'P4 runtime: text post create, edit, list, and delete passed'
docker exec -i "$mysql_container" mysql -uroot -preview_root sports_booking <<SQL
INSERT INTO s_session_slot (id,venue_id,court_id,sport_type_id,slot_date,start_at,end_at,price_fen,status)
SELECT 999999, v.id, c.id, cs.sport_type_id, '2026-01-01',
       '2026-01-01 10:00:00', '2026-01-01 11:00:00', 6000, 'BOOKED'
FROM s_venue v JOIN s_court c ON c.venue_id=v.id
JOIN s_court_sport cs ON cs.court_id=c.id ORDER BY v.id,c.id,cs.sport_type_id LIMIT 1;
INSERT INTO s_booking (id,booking_no,user_id,venue_id,court_id,sport_type_id,session_slot_id,source,
    contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,
    external_amount_fen,service_fee_rate_basis_points,status,payment_status,completed_at)
SELECT 999999,'P4-RUNTIME-REVIEW',${mini_user_id},s.venue_id,s.court_id,s.sport_type_id,s.id,'ONLINE',
       '本地测试','00000000000',v.name,c.name,st.name,s.start_at,s.end_at,6000,6000,0,
       'COMPLETED','PAID',UTC_TIMESTAMP(3)
FROM s_session_slot s JOIN s_venue v ON v.id=s.venue_id
JOIN s_court c ON c.id=s.court_id JOIN s_sport_type st ON st.id=s.sport_type_id WHERE s.id=999999;
SQL
print 'P4 runtime: completed booking fixture inserted'

review_id=$(curl -fsS -X POST http://127.0.0.1:8091/api/mini/bookings/999999/review \
  -H "X-Mini-Token: $mini_token" -H 'Content-Type: application/json' \
  -d "{\"body\":\"首次评价\",\"overallScore\":5,\"courtScore\":5,\"environmentScore\":4,\"serviceScore\":5,\"tagIds\":[${tag_id}]}" \
  | jq -er '.data | select(.moderationStatus == "PENDING") | .id')
review_revision_id=$(curl -fsS -H "X-Admin-Token: $platform_token" http://127.0.0.1:8091/api/platform/content-moderation/reviews \
  | jq -er --argjson id "$review_id" '.data[] | select(.reviewId == $id) | .revisionId')
print 'P4 runtime: review created'
curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews | jq -e '.data == []' >/dev/null
curl -fsS -H "X-Admin-Token: $platform_token" \
  http://127.0.0.1:8091/api/platform/content-moderation/reviews \
  | jq -e --argjson id "$review_id" '.data | any(.reviewId == $id)' >/dev/null
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/reviews/$review_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$review_revision_id,\"action\":\"APPROVE\",\"reason\":\"测试审核通过\"}" | jq -e '.data == true' >/dev/null
print 'P4 runtime: first revision approved'
curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews \
  | jq -e --argjson id "$review_id" '.data | any(.id == $id and .body == "首次评价")' >/dev/null

venue_token=$(curl -fsS -X POST http://127.0.0.1:8091/api/auth/admin/login \
  -H 'Content-Type: application/json' -d '{"username":"venue1","password":"venue123"}' | jq -er '.data.token')
reply_id=$(curl -fsS -X PUT "http://127.0.0.1:8091/api/venue-admin/reviews/$review_id/reply" \
  -H "X-Admin-Token: $venue_token" -H 'Content-Type: application/json' \
  -d '{"body":"感谢你的到场反馈"}' | jq -er '.data.id')
reply_revision_id=$(curl -fsS -H "X-Admin-Token: $platform_token" http://127.0.0.1:8091/api/platform/content-moderation/reviews \
  | jq -er --argjson id "$reply_id" '.data[] | select(.replyId == $id) | .revisionId')
curl -fsS -H "X-Admin-Token: $platform_token" \
  http://127.0.0.1:8091/api/platform/content-moderation/reviews \
  | jq -e --argjson id "$reply_id" '.data | any(.contentType == "REVIEW_REPLY" and .replyId == $id)' >/dev/null
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/replies/$reply_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$reply_revision_id,\"action\":\"APPROVE\",\"reason\":\"官方回复通过\"}" | jq -e '.data == true' >/dev/null
curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews \
  | jq -e --argjson id "$review_id" '.data | any(.id == $id and .reply.body == "感谢你的到场反馈")' >/dev/null
curl -fsS -H "X-Admin-Token: $platform_token" http://127.0.0.1:8091/api/platform/content-moderation/reviews \
  | jq -e --argjson id "$reply_id" '.data | any(.replyId == $id and .publicBody == "感谢你的到场反馈")' >/dev/null
curl -fsS http://127.0.0.1:8091/api/public/venues/1/review-summary \
  | jq -e --argjson id "$tag_id" '.data.tagCounts | any(.tagId == $id and .reviewCount >= 1)' >/dev/null
curl -fsS -X PUT "http://127.0.0.1:8091/api/venue-admin/reviews/$review_id/reply" \
  -H "X-Admin-Token: $venue_token" -H 'Content-Type: application/json' \
  -d '{"body":"第二次官方回复"}' | jq -e '.data.moderationStatus == "PENDING"' >/dev/null
reply_revision_id=$(curl -fsS -H "X-Admin-Token: $platform_token" http://127.0.0.1:8091/api/platform/content-moderation/reviews \
  | jq -er --argjson id "$reply_id" '.data[] | select(.replyId == $id) | .revisionId')
curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews \
  | jq -e --argjson id "$review_id" '.data | any(.id == $id and .reply.body == "感谢你的到场反馈")' >/dev/null
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/replies/$reply_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$reply_revision_id,\"action\":\"REJECT\",\"reason\":\"测试保留旧回复\"}" | jq -e '.data == true' >/dev/null
curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews \
  | jq -e --argjson id "$review_id" '.data | any(.id == $id and .reply.body == "感谢你的到场反馈")' >/dev/null
for action in HIDE RESTORE; do
  curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/replies/$reply_id" \
    -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
    -d "{\"revisionId\":$reply_revision_id,\"action\":\"$action\",\"reason\":\"运行态验证\"}" \
    | jq -e '.data == true' >/dev/null
  expected_visible=$([[ "$action" == HIDE ]] && print false || print true)
  curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews \
    | jq -e --argjson id "$review_id" --argjson visible "$expected_visible" \
      '(.data | any(.id == $id and .reply != null)) == $visible' >/dev/null
done
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/replies/$reply_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$reply_revision_id,\"action\":\"DELETE\",\"reason\":\"运行态验证\"}" \
  | jq -e '.data == true' >/dev/null
curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews \
  | jq -e --argjson id "$review_id" '.data | any(.id == $id and .reply == null)' >/dev/null
curl -sS -X PUT "http://127.0.0.1:8091/api/venue-admin/reviews/$review_id/reply" \
  -H "X-Admin-Token: $venue_token" -H 'Content-Type: application/json' \
  -d '{"body":"删除后再次编辑"}' | jq -e '.code != 0' >/dev/null
print 'P4 runtime: venue reply, reply moderation, and tag distribution passed'

curl -fsS -X PUT "http://127.0.0.1:8091/api/mini/reviews/$review_id" \
  -H "X-Mini-Token: $mini_token" -H 'Content-Type: application/json' \
  -d "{\"body\":\"修改后的评价\",\"overallScore\":4,\"courtScore\":4,\"environmentScore\":4,\"serviceScore\":4,\"tagIds\":[${tag_id}]}" \
  | jq -e '.data.moderationStatus == "PENDING"' >/dev/null
review_revision_id=$(curl -fsS -H "X-Admin-Token: $platform_token" http://127.0.0.1:8091/api/platform/content-moderation/reviews \
  | jq -er --argjson id "$review_id" '.data[] | select(.reviewId == $id) | .revisionId')
print 'P4 runtime: second revision created'
curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews \
  | jq -e '.data[0].body == "首次评价"' >/dev/null
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/reviews/$review_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$review_revision_id,\"action\":\"APPROVE\",\"reason\":\"修改审核通过\"}" | jq -e '.data == true' >/dev/null
print 'P4 runtime: second revision approved'
curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews \
  | jq -e '.data[0].body == "修改后的评价"' >/dev/null
for action in HIDE RESTORE; do
  curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/reviews/$review_id" \
    -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
    -d "{\"revisionId\":$review_revision_id,\"action\":\"$action\",\"reason\":\"运行态验证\"}" \
    | jq -e '.data == true' >/dev/null
  expected_visible=$([[ "$action" == HIDE ]] && print false || print true)
  curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews \
    | jq -e --argjson id "$review_id" --argjson visible "$expected_visible" \
      '(.data | any(.id == $id)) == $visible' >/dev/null
done
curl -fsS -X POST "http://127.0.0.1:8091/api/platform/content-moderation/reviews/$review_id" \
  -H "X-Admin-Token: $platform_token" -H 'Content-Type: application/json' \
  -d "{\"revisionId\":$review_revision_id,\"action\":\"DELETE\",\"reason\":\"运行态验证\"}" \
  | jq -e '.data == true' >/dev/null
curl -fsS http://127.0.0.1:8091/api/public/venues/1/reviews | jq -e '.data == []' >/dev/null
curl -sS -X POST http://127.0.0.1:8091/api/mini/bookings/999999/review \
  -H "X-Mini-Token: $mini_token" -H 'Content-Type: application/json' \
  -d '{"body":"重复评价","overallScore":5,"courtScore":5,"environmentScore":5,"serviceScore":5}' \
  | jq -e '.code != 0' >/dev/null
audit_count=$(docker exec "$mysql_container" mysql -uroot -preview_root -N -s sports_booking \
  -e "SELECT COUNT(*) FROM s_content_moderation_log WHERE content_type='REVIEW' AND content_id=$review_id")
[[ "$audit_count" == 5 ]] || { print -u2 "审核审计记录不完整: $audit_count"; exit 1; }

source "${backend_dir}/scripts/verify-p4-community-checks.zsh"

if [[ -n "${P4_OPENAPI_OUTPUT:-}" ]]; then
  curl -fsS -H 'Host: 127.0.0.1:8089' http://127.0.0.1:8091/api/v3/api-docs.yaml \
    > "$P4_OPENAPI_OUTPUT"
fi

print 'P4 V8 runtime: Flyway, tags, review publication, version switch, deletion, and audit passed'

if [[ "${P4_PREVIEW:-false}" == true ]]; then
  print 'P4 preview backend: http://127.0.0.1:8091/api'
  while true; do sleep 30; done
fi
