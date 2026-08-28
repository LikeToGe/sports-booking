#!/bin/zsh
set -euo pipefail

script_dir="${0:A:h}"
backend_dir="${script_dir:h}"
project_dir="${backend_dir:h}"
cd "$backend_dir"

set -a
source "$project_dir/infra/.env"
set +a

verify_dir=$(mktemp -d)
project_java_home=$(awk -F= '/^export JAVA_HOME=/{print $2; exit}' ./mvn-project)
platform_user="t4_platform_$(date +%s)"
platform_password=$(openssl rand -hex 24)
venue_user="t4_venue_$(date +%s)"
venue_password=$(openssl rand -hex 24)
other_venue_user="t4_other_$(date +%s)"
other_venue_password=$(openssl rand -hex 24)

APP_BOOTSTRAP_ADMIN_USERNAME="$platform_user" APP_BOOTSTRAP_ADMIN_PASSWORD="$platform_password" APP_SESSION_SECURE_COOKIE=false SERVER_PORT=18082 \
  "$project_java_home/bin/java" -jar target/sports-booking-admin-0.1.0-SNAPSHOT.jar > "$verify_dir/application.log" 2>&1 &
app_pid=$!

cleanup() {
    if kill -0 "$app_pid" 2>/dev/null; then
        kill "$app_pid"
        wait "$app_pid" || true
    fi
}
finish() {
    exit_code=$?
    if [[ "$exit_code" != "0" ]]; then
        grep -E 'ERROR|Exception|Caused by|Application run failed' "$verify_dir/application.log" || true
    fi
    cleanup
    exit "$exit_code"
}
trap finish EXIT

step() {
    print -u2 "T-04 check: $1"
}

ready=false
for attempt in {1..60}; do
    http_status=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18082/api/admin/auth/session || true)
    if [[ "$http_status" == "401" ]]; then
        ready=true
        break
    fi
    sleep 1
done
if [[ "$ready" != true ]]; then
    tail -n 100 "$verify_dir/application.log"
    exit 1
fi
step 'application ready with V4 migration'

curl -sS -D "$verify_dir/platform.headers" -o "$verify_dir/platform.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$platform_user\",\"password\":\"$platform_password\"}" http://127.0.0.1:18082/api/admin/auth/login
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/platform.json"
platform_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/platform.headers")
platform_csrf=$(awk -F ': ' 'tolower($1) == "x-csrf-token" {gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/platform.headers")

test_tag=$(date +%s)
org_payload=$(printf '{"organizationName":"T4 Verify Org %s","storeName":"T4 Verify Store %s","adminDisplayName":"T4 Venue","adminUsername":"%s","initialPassword":"%s","serviceRateBasisPoints":100}' \
    "$test_tag" "$test_tag" "$venue_user" "$venue_password")
curl -sS -o "$verify_dir/org.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t4-org-1' --data "$org_payload" http://127.0.0.1:18082/api/admin/organizations
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/org.json"

root_payload=$(printf '{"parentId":null,"code":"T4ROOT%s","name":"T4根%s","icon":"circle","sortOrder":1}' "$test_tag" "$test_tag")
curl -sS -o "$verify_dir/root.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t4-root-1' --data "$root_payload" http://127.0.0.1:18082/api/admin/catalog/sport-categories
root_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/root.json")
child_payload=$(printf '{"parentId":"%s","code":"T4ITEM%s","name":"T4项目%s","icon":"circle","sortOrder":1}' "$root_id" "$test_tag" "$test_tag")
curl -sS -o "$verify_dir/child.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t4-child-1' --data "$child_payload" http://127.0.0.1:18082/api/admin/catalog/sport-categories
child_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/child.json")
full_payload=$(printf '{"sportCategoryId":"%s","name":"T4全场%s"}' "$child_id" "$test_tag")
curl -sS -o "$verify_dir/full.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t4-full-1' --data "$full_payload" http://127.0.0.1:18082/api/admin/catalog/rental-types
full_type_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/full.json")
half_payload=$(printf '{"sportCategoryId":"%s","name":"T4半场%s"}' "$child_id" "$test_tag")
curl -sS -o "$verify_dir/half.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t4-half-1' --data "$half_payload" http://127.0.0.1:18082/api/admin/catalog/rental-types
half_type_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/half.json")

curl -sS -D "$verify_dir/venue.headers" -o "$verify_dir/venue.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$venue_user\",\"password\":\"$venue_password\"}" http://127.0.0.1:18082/api/admin/auth/login
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/venue.json"
venue_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/venue.headers")
venue_csrf=$(awk -F ': ' 'tolower($1) == "x-csrf-token" {gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/venue.headers")

court_payload=$(printf '{"sportCategoryId":"%s","fullRentalTypeId":"%s","halfRentalTypeId":"%s","name":"T4篮球场%s","sortOrder":1}' \
    "$child_id" "$full_type_id" "$half_type_id" "$test_tag")
curl -sS -o "$verify_dir/court.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-court-1' --data "$court_payload" http://127.0.0.1:18082/api/venue/resources/courts
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("sellableUnits").length == 3 ? 0 : 1)' "$verify_dir/court.json"
full_version_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("sellableUnits")[0].fetch("currentVersion").fetch("id")' "$verify_dir/court.json")
half_a_version_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("sellableUnits")[1].fetch("currentVersion").fetch("id")' "$verify_dir/court.json")
half_b_version_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("sellableUnits")[2].fetch("currentVersion").fetch("id")' "$verify_dir/court.json")
step 'T-03 supply prepared'

today=$(TZ=Asia/Shanghai ruby -rdate -e 'puts Date.today')
today_weekday=$(TZ=Asia/Shanghai ruby -rdate -e 'puts Date.today.cwday')
tomorrow=$(TZ=Asia/Shanghai ruby -rdate -e 'puts Date.today + 1')
closed_day=$(TZ=Asia/Shanghai ruby -rdate -e 'puts Date.today + 2')
future_day=$(TZ=Asia/Shanghai ruby -rdate -e 'puts Date.today + 3')
future_weekday=$(TZ=Asia/Shanghai ruby -rdate -e 'puts (Date.today + 3).cwday')

conflict_payload=$(printf '{"effectiveFrom":"%s","weeklySlots":[{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"18:00","endTime":"19:00","priceFen":10000},{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"18:30","endTime":"19:30","priceFen":6000}],"specialDates":[]}' \
    "$today" "$today_weekday" "$full_version_id" "$today_weekday" "$full_version_id")
conflict_status=$(curl -sS -o "$verify_dir/conflict.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-conflict-1' --data "$conflict_payload" http://127.0.0.1:18082/api/venue/schedules/versions)
[[ "$conflict_status" == "409" ]]

schedule_payload=$(printf '{"effectiveFrom":"%s","weeklySlots":[{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"18:00","endTime":"19:00","priceFen":10000},{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"19:00","endTime":"20:00","priceFen":6000},{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"19:00","endTime":"20:00","priceFen":6000},{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"20:00","endTime":"21:00","priceFen":12000}],"specialDates":[{"date":"%s","specialType":"OVERRIDE","slots":[{"sellableUnitVersionId":"%s","startTime":"17:00","endTime":"18:00","priceFen":7000}]},{"date":"%s","specialType":"CLOSED","slots":[]}]}' \
    "$today" "$today_weekday" "$full_version_id" "$today_weekday" "$half_a_version_id" "$today_weekday" "$half_b_version_id" "$future_weekday" "$full_version_id" "$tomorrow" "$half_a_version_id" "$closed_day")
curl -sS -o "$verify_dir/draft-first.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-draft-1' --data "$schedule_payload" http://127.0.0.1:18082/api/venue/schedules/versions
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); data=body.fetch("data"); exit(body.fetch("code") == 1 && data.fetch("versionStatus") == "DRAFT" && data.fetch("weeklySlots").length == 4 && data.fetch("specialDates").length == 2 ? 0 : 1)' "$verify_dir/draft-first.json"
curl -sS -o "$verify_dir/draft-replay.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-draft-1' --data "$schedule_payload" http://127.0.0.1:18082/api/venue/schedules/versions
ruby -rjson -e 'first=JSON.parse(File.read(ARGV[0])); replay=JSON.parse(File.read(ARGV[1])); exit(first.fetch("data") == replay.fetch("data") ? 0 : 1)' "$verify_dir/draft-first.json" "$verify_dir/draft-replay.json"
schedule_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/draft-first.json")
step 'resource overlap rejection and draft idempotency validated'

curl -sS -o "$verify_dir/publish.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-publish-1' -X POST "http://127.0.0.1:18082/api/venue/schedules/versions/$schedule_id/publish"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("versionStatus") == "PUBLISHED" ? 0 : 1)' "$verify_dir/publish.json"
curl -sS -o "$verify_dir/today-inventory.json" -H "Cookie: $venue_cookie" "http://127.0.0.1:18082/api/venue/schedules/inventory?date=$today&page=1&pageSize=20"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); records=body.fetch("data").fetch("records"); exit(body.fetch("code") == 1 && records.length == 3 && records.all? { |slot| slot.fetch("inventoryStatus") == "AVAILABLE" } && records[0].fetch("atoms").length == 4 && records[1].fetch("atoms").length == 2 && records[2].fetch("atoms").length == 2 ? 0 : 1)' "$verify_dir/today-inventory.json"
curl -sS -o "$verify_dir/override-inventory.json" -H "Cookie: $venue_cookie" "http://127.0.0.1:18082/api/venue/schedules/inventory?date=$tomorrow&page=1&pageSize=20"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); records=body.fetch("data").fetch("records"); exit(body.fetch("code") == 1 && records.length == 1 && records[0].fetch("startTime") == "17:00" && records[0].fetch("priceFen") == 7000 ? 0 : 1)' "$verify_dir/override-inventory.json"
curl -sS -o "$verify_dir/closed-inventory.json" -H "Cookie: $venue_cookie" "http://127.0.0.1:18082/api/venue/schedules/inventory?date=$closed_day&page=1&pageSize=20"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("records") == [] ? 0 : 1)' "$verify_dir/closed-inventory.json"
step 'published weekly, special override and closed-date inventory validated'

curl -sS -o "$verify_dir/future-inventory.json" -H "Cookie: $venue_cookie" "http://127.0.0.1:18082/api/venue/schedules/inventory?date=$future_day&page=1&pageSize=20"
future_slot_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); records=body.fetch("data").fetch("records"); exit 1 unless body.fetch("code") == 1 && records.length == 1; puts records[0].fetch("id")' "$verify_dir/future-inventory.json")
block_payload=$(printf '{"inventorySlotId":"%s","blockType":"MAINTENANCE","remark":"T4 runtime maintenance"}' "$future_slot_id")
curl -sS -o "$verify_dir/block-first.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-block-1' --data "$block_payload" http://127.0.0.1:18082/api/venue/blocks
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("blockStatus") == "ACTIVE" ? 0 : 1)' "$verify_dir/block-first.json"
block_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/block-first.json")
block_again_status=$(curl -sS -o "$verify_dir/block-again.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-block-2' --data "$block_payload" http://127.0.0.1:18082/api/venue/blocks)
[[ "$block_again_status" == "409" ]]
step 'manual block atom locking validated'

next_version_payload=$(printf '{"effectiveFrom":"%s","weeklySlots":[{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"20:00","endTime":"21:00","priceFen":13000}],"specialDates":[]}' \
    "$future_day" "$future_weekday" "$full_version_id")
curl -sS -o "$verify_dir/next-draft.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-next-draft-1' --data "$next_version_payload" http://127.0.0.1:18082/api/venue/schedules/versions
next_schedule_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/next-draft.json")
curl -sS -o "$verify_dir/next-publish.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-next-publish-1' -X POST "http://127.0.0.1:18082/api/venue/schedules/versions/$next_schedule_id/publish"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("versionStatus") == "PUBLISHED" ? 0 : 1)' "$verify_dir/next-publish.json"
curl -sS -o "$verify_dir/cancel-block.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t4-cancel-block-1' -X POST "http://127.0.0.1:18082/api/venue/blocks/$block_id/cancel"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/cancel-block.json"
curl -sS -o "$verify_dir/reconcile-inventory.json" -H "Cookie: $venue_cookie" "http://127.0.0.1:18082/api/venue/schedules/inventory?date=$future_day&page=1&pageSize=20"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); records=body.fetch("data").fetch("records"); exit(body.fetch("code") == 1 && records.any? { |slot| slot.fetch("id") == ARGV[1] && slot.fetch("inventoryStatus") == "RECONCILE_PENDING" } ? 0 : 1)' "$verify_dir/reconcile-inventory.json" "$future_slot_id"
step 'version change and reconcile-pending release validated'

forbidden_status=$(curl -sS -o "$verify_dir/platform-forbidden.json" -w '%{http_code}' -H "Cookie: $platform_cookie" \
    'http://127.0.0.1:18082/api/venue/schedules/versions?page=1&pageSize=20')
[[ "$forbidden_status" == "403" ]]
other_org_payload=$(printf '{"organizationName":"T4 Other Org %s","storeName":"T4 Other Store %s","adminDisplayName":"T4 Other Venue","adminUsername":"%s","initialPassword":"%s","serviceRateBasisPoints":100}' \
    "$test_tag" "$test_tag" "$other_venue_user" "$other_venue_password")
curl -sS -o "$verify_dir/other-org.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t4-other-org-1' --data "$other_org_payload" http://127.0.0.1:18082/api/admin/organizations
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/other-org.json"
curl -sS -D "$verify_dir/other-venue.headers" -o "$verify_dir/other-venue.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$other_venue_user\",\"password\":\"$other_venue_password\"}" http://127.0.0.1:18082/api/admin/auth/login
other_venue_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/other-venue.headers")
cross_store_status=$(curl -sS -o "$verify_dir/cross-store.json" -w '%{http_code}' -H "Cookie: $other_venue_cookie" \
    "http://127.0.0.1:18082/api/venue/schedules/versions/$schedule_id")
[[ "$cross_store_status" == "404" ]]
step 'role and store scope rejection validated'

printf 'T-04 runtime verification passed: V4 migration, schedule draft/publish, 30-minute resource conflict, special-date coverage, atomic inventory, manual blocks, reconcile release, idempotency and access scope\n'
