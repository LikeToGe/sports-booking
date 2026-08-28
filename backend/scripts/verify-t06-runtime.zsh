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
redis_stopped=false
project_java_home=$(awk -F= '/^export JAVA_HOME=/{print $2; exit}' ./mvn-project)
platform_user="t6_platform_$(date +%s)"
platform_password=$(openssl rand -hex 24)
venue_user="t6_venue_$(date +%s)"
venue_password=$(openssl rand -hex 24)

APP_BOOTSTRAP_ADMIN_USERNAME="$platform_user" APP_BOOTSTRAP_ADMIN_PASSWORD="$platform_password" APP_SESSION_SECURE_COOKIE=false \
  APP_BOOKING_COMPLETION_FIXED_DELAY_MS=1000 APP_BILLING_ACCRUAL_FIXED_DELAY_MS=1000 SERVER_PORT=18084 \
  "$project_java_home/bin/java" -jar target/sports-booking-admin-0.1.0-SNAPSHOT.jar > "$verify_dir/application.log" 2>&1 &
app_pid=$!

cleanup() {
    if [[ "$redis_stopped" == true ]]; then
        docker start sports-booking-redis >/dev/null 2>&1 || true
    fi
    if kill -0 "$app_pid" 2>/dev/null; then
        kill "$app_pid"
        wait "$app_pid" || true
    fi
}
finish() {
    exit_code=$?
    if [[ "$exit_code" != "0" ]]; then
        print -u2 "T-06 verification artifacts: $verify_dir"
        grep -E 'ERROR|Exception|Caused by|Application run failed' "$verify_dir/application.log" || true
    fi
    cleanup
    exit "$exit_code"
}
trap finish EXIT

step() {
    print -u2 "T-06 check: $1"
}

ready=false
for attempt in {1..60}; do
    http_status=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18084/api/admin/auth/session || true)
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
step 'application ready with V11 migration'

curl -sS -D "$verify_dir/platform.headers" -o "$verify_dir/platform.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$platform_user\",\"password\":\"$platform_password\"}" http://127.0.0.1:18084/api/admin/auth/login
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/platform.json"
platform_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/platform.headers")
platform_csrf=$(awk -F ': ' 'tolower($1) == "x-csrf-token" {gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/platform.headers")

test_tag=$(date +%s)
payment_full_event="t7-full-success-$test_tag"
payment_half_a_event="t7-half-a-unknown-$test_tag"
payment_half_b_event="t7-half-b-failed-$test_tag"
venue_payment_event="t8-venue-success-$test_tag"
late_payment_event="t8-late-success-$test_tag"
auto_complete_event="t9-auto-complete-success-$test_tag"
org_payload=$(printf '{"organizationName":"T6 Verify Org %s","storeName":"T6 Verify Store %s","adminDisplayName":"T6 Venue","adminUsername":"%s","initialPassword":"%s","serviceRateBasisPoints":100}' \
    "$test_tag" "$test_tag" "$venue_user" "$venue_password")
curl -sS -o "$verify_dir/org.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t6-org-1' --data "$org_payload" http://127.0.0.1:18084/api/admin/organizations
organization_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("organizationId")' "$verify_dir/org.json")
store_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("storeId")' "$verify_dir/org.json")

root_payload=$(printf '{"parentId":null,"code":"T6ROOT%s","name":"T6根%s","icon":"circle","sortOrder":1}' "$test_tag" "$test_tag")
curl -sS -o "$verify_dir/root.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t6-root-1' --data "$root_payload" http://127.0.0.1:18084/api/admin/catalog/sport-categories
root_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/root.json")
child_payload=$(printf '{"parentId":"%s","code":"T6ITEM%s","name":"T6项目%s","icon":"circle","sortOrder":1}' "$root_id" "$test_tag" "$test_tag")
curl -sS -o "$verify_dir/child.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t6-child-1' --data "$child_payload" http://127.0.0.1:18084/api/admin/catalog/sport-categories
child_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/child.json")
full_payload=$(printf '{"sportCategoryId":"%s","name":"T6全场%s"}' "$child_id" "$test_tag")
curl -sS -o "$verify_dir/full.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t6-full-1' --data "$full_payload" http://127.0.0.1:18084/api/admin/catalog/rental-types
full_type_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/full.json")
half_payload=$(printf '{"sportCategoryId":"%s","name":"T6半场%s"}' "$child_id" "$test_tag")
curl -sS -o "$verify_dir/half.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t6-half-1' --data "$half_payload" http://127.0.0.1:18084/api/admin/catalog/rental-types
half_type_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/half.json")

curl -sS -D "$verify_dir/venue.headers" -o "$verify_dir/venue.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$venue_user\",\"password\":\"$venue_password\"}" http://127.0.0.1:18084/api/admin/auth/login
venue_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/venue.headers")
venue_csrf=$(awk -F ': ' 'tolower($1) == "x-csrf-token" {gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/venue.headers")
court_payload=$(printf '{"sportCategoryId":"%s","fullRentalTypeId":"%s","halfRentalTypeId":"%s","name":"T6篮球场%s","sortOrder":1}' \
    "$child_id" "$full_type_id" "$half_type_id" "$test_tag")
curl -sS -o "$verify_dir/court.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t6-court-1' --data "$court_payload" http://127.0.0.1:18084/api/venue/resources/courts
full_version_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("sellableUnits")[0].fetch("currentVersion").fetch("id")' "$verify_dir/court.json")
half_a_version_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("sellableUnits")[1].fetch("currentVersion").fetch("id")' "$verify_dir/court.json")
half_b_version_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("sellableUnits")[2].fetch("currentVersion").fetch("id")' "$verify_dir/court.json")

today=$(TZ=Asia/Shanghai ruby -rdate -e 'puts Date.today')
future_day=$(TZ=Asia/Shanghai ruby -rdate -e 'puts Date.today + 1')
future_weekday=$(TZ=Asia/Shanghai ruby -rdate -e 'puts (Date.today + 1).cwday')
schedule_payload=$(printf '{"effectiveFrom":"%s","weeklySlots":[{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"20:00","endTime":"21:00","priceFen":10000},{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"20:00","endTime":"21:00","priceFen":6000},{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"20:00","endTime":"21:00","priceFen":6000},{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"21:00","endTime":"22:00","priceFen":6000},{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"21:00","endTime":"22:00","priceFen":6000},{"weekDay":%s,"sellableUnitVersionId":"%s","startTime":"22:00","endTime":"23:00","priceFen":12000}],"specialDates":[]}' \
    "$today" "$future_weekday" "$full_version_id" "$future_weekday" "$half_a_version_id" "$future_weekday" "$half_b_version_id" "$future_weekday" "$half_a_version_id" "$future_weekday" "$half_b_version_id" "$future_weekday" "$full_version_id")
curl -sS -o "$verify_dir/draft.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t6-draft-1' --data "$schedule_payload" http://127.0.0.1:18084/api/venue/schedules/versions
schedule_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/draft.json")
curl -sS -o "$verify_dir/publish.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t6-publish-1' -X POST "http://127.0.0.1:18084/api/venue/schedules/versions/$schedule_id/publish"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/publish.json"
step 'shared full-court and half-court inventory published'

curl -sS -o "$verify_dir/mini-login-a.json" -H 'Content-Type: application/json' --data "{\"code\":\"mock:t6-user-a-$test_tag\"}" http://127.0.0.1:18084/api/mini/auth/login
mini_access_a=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("accessToken")' "$verify_dir/mini-login-a.json")
mini_refresh_a=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("refreshToken")' "$verify_dir/mini-login-a.json")
curl -sS -o "$verify_dir/mini-login-b.json" -H 'Content-Type: application/json' --data "{\"code\":\"mock:t6-user-b-$test_tag\"}" http://127.0.0.1:18084/api/mini/auth/login
mini_access_b=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("accessToken")' "$verify_dir/mini-login-b.json")
curl -sS -o "$verify_dir/venues.json" "http://127.0.0.1:18084/api/mini/venues?date=$future_day&page=1&pageSize=20"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("records").any? { |item| item.fetch("storeId") == ARGV[1] } ? 0 : 1)' "$verify_dir/venues.json" "$store_id"
curl -sS -o "$verify_dir/venue-detail.json" "http://127.0.0.1:18084/api/mini/venues/$store_id?date=$future_day"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("slots").length == 6 ? 0 : 1)' "$verify_dir/venue-detail.json"
full_slot_id=$(ruby -rjson -e 'slots=JSON.parse(File.read(ARGV[0])).fetch("data").fetch("slots"); puts slots.find { |slot| slot.fetch("sellableUnitVersionId") == ARGV[1] && slot.fetch("startTime") == "20:00" }.fetch("id")' "$verify_dir/venue-detail.json" "$full_version_id")
half_a_20_slot_id=$(ruby -rjson -e 'slots=JSON.parse(File.read(ARGV[0])).fetch("data").fetch("slots"); puts slots.find { |slot| slot.fetch("sellableUnitVersionId") == ARGV[1] && slot.fetch("startTime") == "20:00" }.fetch("id")' "$verify_dir/venue-detail.json" "$half_a_version_id")
half_a_21_slot_id=$(ruby -rjson -e 'slots=JSON.parse(File.read(ARGV[0])).fetch("data").fetch("slots"); puts slots.find { |slot| slot.fetch("sellableUnitVersionId") == ARGV[1] && slot.fetch("startTime") == "21:00" }.fetch("id")' "$verify_dir/venue-detail.json" "$half_a_version_id")
half_b_21_slot_id=$(ruby -rjson -e 'slots=JSON.parse(File.read(ARGV[0])).fetch("data").fetch("slots"); puts slots.find { |slot| slot.fetch("sellableUnitVersionId") == ARGV[1] && slot.fetch("startTime") == "21:00" }.fetch("id")' "$verify_dir/venue-detail.json" "$half_b_version_id")
full_22_slot_id=$(ruby -rjson -e 'slots=JSON.parse(File.read(ARGV[0])).fetch("data").fetch("slots"); puts slots.find { |slot| slot.fetch("sellableUnitVersionId") == ARGV[1] && slot.fetch("startTime") == "22:00" }.fetch("id")' "$verify_dir/venue-detail.json" "$full_version_id")
step 'public discovery returns six current, available fixed slots'

booking_full_payload=$(printf '{"inventorySlotId":"%s","contactName":"T6 User A","contactPhone":"13800138000","privacyPolicyVersion":"v1","privacyConsentScene":"booking"}' "$full_slot_id")
curl -sS -o "$verify_dir/booking-full-first.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" -H 'X-Device-Id: t6-device-a' \
    -H 'Idempotency-Key: t6-booking-full-1' --data "$booking_full_payload" http://127.0.0.1:18084/api/mini/bookings
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("orderStatus") == "PENDING_PAYMENT" ? 0 : 1)' "$verify_dir/booking-full-first.json"
full_order_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/booking-full-first.json")
curl -sS -o "$verify_dir/booking-full-replay.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" -H 'X-Device-Id: t6-device-a' \
    -H 'Idempotency-Key: t6-booking-full-1' --data "$booking_full_payload" http://127.0.0.1:18084/api/mini/bookings
ruby -rjson -e 'first=JSON.parse(File.read(ARGV[0])); replay=JSON.parse(File.read(ARGV[1])); exit(first.fetch("data") == replay.fetch("data") ? 0 : 1)' "$verify_dir/booking-full-first.json" "$verify_dir/booking-full-replay.json"
shared_conflict_payload=$(printf '{"inventorySlotId":"%s","contactName":"T6 User B","contactPhone":"13900139000","privacyPolicyVersion":"v1","privacyConsentScene":"booking"}' "$half_a_20_slot_id")
shared_conflict_status=$(curl -sS -o "$verify_dir/shared-conflict.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" -H 'X-Device-Id: t6-device-b' \
    -H 'Idempotency-Key: t6-shared-conflict-1' --data "$shared_conflict_payload" http://127.0.0.1:18084/api/mini/bookings)
[[ "$shared_conflict_status" == "409" ]]
booking_half_conflict_payload=$(printf '{"inventorySlotId":"%s","contactName":"T6 User B","contactPhone":"13900139000","privacyPolicyVersion":"v1","privacyConsentScene":"booking"}' "$half_a_21_slot_id")
pause_payload='{"reason":"T6 pause contention"}'
curl -sS -o "$verify_dir/pause.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t6-pause-1' --data "$pause_payload" "http://127.0.0.1:18084/api/admin/stores/$store_id/transaction-blockers"
blocker_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); puts body.fetch("data").fetch("storeGate").fetch("blockers").find { |item| item.fetch("causeType") == "MANUAL" && item.fetch("blockerStatus") == "ACTIVE" }.fetch("id")' "$verify_dir/pause.json")
gate_reject_status=$(curl -sS -o "$verify_dir/gate-reject.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" -H 'X-Device-Id: t6-device-b' \
    -H 'Idempotency-Key: t6-gate-reject-1' --data "$booking_half_conflict_payload" http://127.0.0.1:18084/api/mini/bookings)
[[ "$gate_reject_status" == "409" ]]
curl -sS -o "$verify_dir/pause-release.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t6-pause-release-1' -X POST "http://127.0.0.1:18084/api/admin/stores/$store_id/transaction-blockers/$blocker_id/release"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/pause-release.json"
curl -sS -o "$verify_dir/booking-half-a.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" -H 'X-Device-Id: t6-device-b' \
    -H 'Idempotency-Key: t6-booking-half-a-1' --data "$booking_half_conflict_payload" http://127.0.0.1:18084/api/mini/bookings
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("orderStatus") == "PENDING_PAYMENT" ? 0 : 1)' "$verify_dir/booking-half-a.json"
half_a_order_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/booking-half-a.json")
booking_half_b_payload=$(printf '{"inventorySlotId":"%s","contactName":"T6 User A","contactPhone":"13800138000","privacyPolicyVersion":"v1","privacyConsentScene":"booking"}' "$half_b_21_slot_id")
curl -sS -o "$verify_dir/booking-half-b.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" -H 'X-Device-Id: t6-device-a' \
    -H 'Idempotency-Key: t6-booking-half-b-1' --data "$booking_half_b_payload" http://127.0.0.1:18084/api/mini/bookings
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/booking-half-b.json"
half_b_order_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/booking-half-b.json")
quota_payload=$(printf '{"inventorySlotId":"%s","contactName":"T6 User A","contactPhone":"13800138000","privacyPolicyVersion":"v1","privacyConsentScene":"booking"}' "$full_22_slot_id")
quota_reject_status=$(curl -sS -o "$verify_dir/quota-reject.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" -H 'X-Device-Id: t6-device-a' \
    -H 'Idempotency-Key: t6-quota-reject-1' --data "$quota_payload" http://127.0.0.1:18084/api/mini/bookings)
[[ "$quota_reject_status" == "429" ]]
step 'gate, shared full/half conflict, independent A/B booking, idempotency and user quota validated'

curl -sS -o "$verify_dir/payment-full.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t7-payment-full-1' --data "{\"orderId\":\"$full_order_id\"}" http://127.0.0.1:18084/api/mini/payments
payment_full_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1 && body.fetch("data").fetch("paymentStatus") == "CREATED"; puts body.fetch("data").fetch("id")' "$verify_dir/payment-full.json")
curl -sS -o "$verify_dir/payment-full-success.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t7-payment-full-success-1' --data "{\"outcome\":\"SUCCESS\",\"providerEventNo\":\"$payment_full_event\"}" "http://127.0.0.1:18084/api/mini/payments/$payment_full_id/mock-result"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("paymentStatus") == "SUCCEEDED" ? 0 : 1)' "$verify_dir/payment-full-success.json"
curl -sS -o "$verify_dir/full-order-confirmed.json" -H "Authorization: Bearer $mini_access_a" "http://127.0.0.1:18084/api/mini/bookings/$full_order_id"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("data").fetch("orderStatus") == "CONFIRMED" ? 0 : 1)' "$verify_dir/full-order-confirmed.json"
curl -sS -o "$verify_dir/payment-full-duplicate-event.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t7-payment-full-success-2' --data "{\"outcome\":\"SUCCESS\",\"providerEventNo\":\"$payment_full_event\"}" "http://127.0.0.1:18084/api/mini/payments/$payment_full_id/mock-result"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("data").fetch("paymentStatus") == "SUCCEEDED" ? 0 : 1)' "$verify_dir/payment-full-duplicate-event.json"

curl -sS -o "$verify_dir/payment-half-a.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" \
    -H 'Idempotency-Key: t7-payment-half-a-1' --data "{\"orderId\":\"$half_a_order_id\"}" http://127.0.0.1:18084/api/mini/payments
payment_half_a_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/payment-half-a.json")
curl -sS -o "$verify_dir/payment-half-a-unknown.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" \
    -H 'Idempotency-Key: t7-payment-half-a-unknown-1' --data "{\"outcome\":\"UNKNOWN\",\"providerEventNo\":\"$payment_half_a_event\"}" "http://127.0.0.1:18084/api/mini/payments/$payment_half_a_id/mock-result"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("data").fetch("paymentStatus") == "UNKNOWN" ? 0 : 1)' "$verify_dir/payment-half-a-unknown.json"
curl -sS -o "$verify_dir/half-a-order-unknown.json" -H "Authorization: Bearer $mini_access_b" "http://127.0.0.1:18084/api/mini/bookings/$half_a_order_id"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("data").fetch("orderStatus") == "PAYMENT_UNKNOWN" ? 0 : 1)' "$verify_dir/half-a-order-unknown.json"

curl -sS -o "$verify_dir/payment-half-b.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t7-payment-half-b-1' --data "{\"orderId\":\"$half_b_order_id\"}" http://127.0.0.1:18084/api/mini/payments
payment_half_b_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/payment-half-b.json")
curl -sS -o "$verify_dir/payment-half-b-failed.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t7-payment-half-b-failed-1' --data "{\"outcome\":\"FAILED\",\"providerEventNo\":\"$payment_half_b_event\"}" "http://127.0.0.1:18084/api/mini/payments/$payment_half_b_id/mock-result"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("data").fetch("paymentStatus") == "FAILED" ? 0 : 1)' "$verify_dir/payment-half-b-failed.json"
curl -sS -o "$verify_dir/half-b-order-cancelled.json" -H "Authorization: Bearer $mini_access_a" "http://127.0.0.1:18084/api/mini/bookings/$half_b_order_id"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("data").fetch("orderStatus") == "CANCELLED" ? 0 : 1)' "$verify_dir/half-b-order-cancelled.json"
step 'mock payment success, failed release, unknown retention and duplicate event inbox validated'

curl -sS -o "$verify_dir/user-cancel-full.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t8-user-cancel-full-1' -X POST "http://127.0.0.1:18084/api/mini/bookings/$full_order_id/cancel"
full_refund_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); data=body.fetch("data"); exit 1 unless body.fetch("code") == 1 && data.fetch("orderStatus") == "CANCELLED" && data.fetch("refundId"); puts data.fetch("refundId")' "$verify_dir/user-cancel-full.json")
curl -sS -o "$verify_dir/full-refund-success.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t8-full-refund-success-1' --data '{"outcome":"SUCCESS"}' "http://127.0.0.1:18084/api/mini/refunds/$full_refund_id/mock-result"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("data").fetch("refundStatus") == "REFUND_SUCCEEDED" ? 0 : 1)' "$verify_dir/full-refund-success.json"
unknown_cancel_status=$(curl -sS -o "$verify_dir/unknown-cancel.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" \
    -H 'Idempotency-Key: t8-unknown-cancel-1' -X POST "http://127.0.0.1:18084/api/mini/bookings/$half_a_order_id/cancel")
[[ "$unknown_cancel_status" == "409" ]]

venue_booking_payload=$(printf '{"inventorySlotId":"%s","contactName":"T6 User B","contactPhone":"13900139000","privacyPolicyVersion":"v1","privacyConsentScene":"booking"}' "$half_b_21_slot_id")
curl -sS -o "$verify_dir/venue-booking.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" -H 'X-Device-Id: t6-device-b' \
    -H 'Idempotency-Key: t8-venue-booking-1' --data "$venue_booking_payload" http://127.0.0.1:18084/api/mini/bookings
venue_order_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/venue-booking.json")
curl -sS -o "$verify_dir/venue-payment.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" \
    -H 'Idempotency-Key: t8-venue-payment-1' --data "{\"orderId\":\"$venue_order_id\"}" http://127.0.0.1:18084/api/mini/payments
venue_payment_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/venue-payment.json")
curl -sS -o "$verify_dir/venue-payment-success.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" \
    -H 'Idempotency-Key: t8-venue-payment-success-1' --data "{\"outcome\":\"SUCCESS\",\"providerEventNo\":\"$venue_payment_event\"}" "http://127.0.0.1:18084/api/mini/payments/$venue_payment_id/mock-result"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("data").fetch("paymentStatus") == "SUCCEEDED" ? 0 : 1)' "$verify_dir/venue-payment-success.json"

curl -sS -o "$verify_dir/employee.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t9-employee-1' --data '{"employeeName":"T9 核销店员"}' http://127.0.0.1:18084/api/venue/employees
employee_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1 && body.fetch("data").fetch("bindingStatus") == "UNBOUND"; puts body.fetch("data").fetch("id")' "$verify_dir/employee.json")
curl -sS -o "$verify_dir/employee-binding-code.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t9-employee-code-1' -X POST "http://127.0.0.1:18084/api/venue/employees/$employee_id/binding-code"
binding_code=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("bindingCode")' "$verify_dir/employee-binding-code.json")
staff_mock_code="mock:t9-staff-$test_tag"
curl -sS -o "$verify_dir/staff-binding.json" -H 'Content-Type: application/json' \
    --data "{\"bindingCode\":\"$binding_code\",\"code\":\"$staff_mock_code\"}" http://127.0.0.1:18084/api/staff/binding
staff_access=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("accessToken")' "$verify_dir/staff-binding.json")
staff_refresh=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("refreshToken")' "$verify_dir/staff-binding.json")
curl -sS -o "$verify_dir/staff-refresh.json" -H 'Content-Type: application/json' --data "{\"refreshToken\":\"$staff_refresh\"}" http://127.0.0.1:18084/api/staff/binding/refresh
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/staff-refresh.json"
staff_access=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("accessToken")' "$verify_dir/staff-refresh.json")
staff_refresh_rotated=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("refreshToken")' "$verify_dir/staff-refresh.json")
old_staff_refresh_status=$(curl -sS -o "$verify_dir/old-staff-refresh.json" -w '%{http_code}' -H 'Content-Type: application/json' --data "{\"refreshToken\":\"$staff_refresh\"}" http://127.0.0.1:18084/api/staff/binding/refresh)
[[ "$old_staff_refresh_status" == "401" ]]

curl -sS -o "$verify_dir/credential.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" \
    -H 'Idempotency-Key: t9-credential-1' -X POST "http://127.0.0.1:18084/api/mini/bookings/$venue_order_id/verification-credential"
qr_token=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("qrToken")' "$verify_dir/credential.json")
short_code=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("shortCode")' "$verify_dir/credential.json")
outside_window_status=$(curl -sS -o "$verify_dir/outside-window.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Authorization: Bearer $staff_access" \
    --data "{\"qrToken\":\"$qr_token\"}" http://127.0.0.1:18084/api/staff/verifications/qr/precheck)
[[ "$outside_window_status" == "409" ]]
docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" sports-booking-mysql mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" \
    -e "UPDATE booking_verification_credential SET valid_from = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 MINUTE), valid_to = DATE_ADD(UTC_TIMESTAMP(), INTERVAL 1 MINUTE) WHERE booking_order_id = $venue_order_id AND is_deleted = 0"
curl -sS -o "$verify_dir/qr-precheck.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $staff_access" \
    --data "{\"qrToken\":\"$qr_token\"}" http://127.0.0.1:18084/api/staff/verifications/qr/precheck
credential_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1 && body.fetch("data").fetch("verificationMethod") == "QR"; puts body.fetch("data").fetch("credentialId")' "$verify_dir/qr-precheck.json")
curl -sS -o "$verify_dir/short-code-precheck.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $staff_access" \
    --data "{\"shortCode\":\"$short_code\"}" http://127.0.0.1:18084/api/staff/verifications/short-code/precheck
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("verificationMethod") == "SHORT_CODE" ? 0 : 1)' "$verify_dir/short-code-precheck.json"
docker stop sports-booking-redis >/dev/null
redis_stopped=true
redis_down_admin_status=$(curl -sS -o "$verify_dir/redis-down-admin-login.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    --data "{\"username\":\"$platform_user\",\"password\":\"$platform_password\"}" http://127.0.0.1:18084/api/admin/auth/login)
[[ "$redis_down_admin_status" == "503" ]]
redis_down_mini_login_status=$(curl -sS -o "$verify_dir/redis-down-mini-login.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    --data "{\"code\":\"mock:t12-redis-down-$test_tag\"}" http://127.0.0.1:18084/api/mini/auth/login)
[[ "$redis_down_mini_login_status" == "503" ]]
redis_down_mini_refresh_status=$(curl -sS -o "$verify_dir/redis-down-mini-refresh.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    --data "{\"refreshToken\":\"$mini_refresh_a\"}" http://127.0.0.1:18084/api/mini/auth/refresh)
[[ "$redis_down_mini_refresh_status" == "503" ]]
redis_down_staff_refresh_status=$(curl -sS -o "$verify_dir/redis-down-staff-refresh.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    --data "{\"refreshToken\":\"$staff_refresh_rotated\"}" http://127.0.0.1:18084/api/staff/binding/refresh)
[[ "$redis_down_staff_refresh_status" == "503" ]]
redis_down_binding_status=$(curl -sS -o "$verify_dir/redis-down-staff-binding.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    --data "{\"bindingCode\":\"$binding_code\",\"code\":\"$staff_mock_code\"}" http://127.0.0.1:18084/api/staff/binding)
[[ "$redis_down_binding_status" == "503" ]]
redis_down_short_code_status=$(curl -sS -o "$verify_dir/redis-down-short-code.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Authorization: Bearer $staff_access" \
    --data "{\"shortCode\":\"$short_code\"}" http://127.0.0.1:18084/api/staff/verifications/short-code/precheck)
[[ "$redis_down_short_code_status" == "503" ]]
docker start sports-booking-redis >/dev/null
redis_stopped=false
for attempt in {1..20}; do
    if docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" sports-booking-redis redis-cli ping | grep -q PONG; then
        break
    fi
    sleep 1
done
docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" sports-booking-redis redis-cli ping | grep -q PONG
redis_recovered=false
for attempt in {1..20}; do
    redis_recovered_status=$(curl -sS -o "$verify_dir/redis-recovered-admin-login.json" -w '%{http_code}' -H 'Content-Type: application/json' \
        --data "{\"username\":\"$platform_user\",\"password\":\"$platform_password\"}" http://127.0.0.1:18084/api/admin/auth/login)
    if [[ "$redis_recovered_status" == "200" ]]; then
        redis_recovered=true
        break
    fi
    sleep 1
done
[[ "$redis_recovered" == true ]]
step 'Redis interruption fails closed for login, mini/staff refresh, staff binding and short-code verification'
curl -sS -o "$verify_dir/verification-confirm.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $staff_access" \
    --data '{"verificationMethod":"QR"}' "http://127.0.0.1:18084/api/staff/verifications/$credential_id/confirm"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("verificationStatus") == "VERIFIED" && !body.fetch("data").fetch("duplicate") ? 0 : 1)' "$verify_dir/verification-confirm.json"
curl -sS -o "$verify_dir/verification-duplicate.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $staff_access" \
    --data '{"verificationMethod":"QR"}' "http://127.0.0.1:18084/api/staff/verifications/$credential_id/confirm"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("duplicate") ? 0 : 1)' "$verify_dir/verification-duplicate.json"
user_verified_cancel_status=$(curl -sS -o "$verify_dir/user-verified-cancel.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" \
    -H 'Idempotency-Key: t9-user-verified-cancel-1' -X POST "http://127.0.0.1:18084/api/mini/bookings/$venue_order_id/cancel")
[[ "$user_verified_cancel_status" == "409" ]]
curl -sS -o "$verify_dir/employee-duplicate.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t9-employee-duplicate-1' --data '{"employeeName":"T9 重复绑定店员"}' http://127.0.0.1:18084/api/venue/employees
duplicate_employee_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/employee-duplicate.json")
curl -sS -o "$verify_dir/duplicate-binding-code.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t9-employee-duplicate-code-1' -X POST "http://127.0.0.1:18084/api/venue/employees/$duplicate_employee_id/binding-code"
duplicate_binding_code=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("bindingCode")' "$verify_dir/duplicate-binding-code.json")
duplicate_binding_status=$(curl -sS -o "$verify_dir/duplicate-binding.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    --data "{\"bindingCode\":\"$duplicate_binding_code\",\"code\":\"$staff_mock_code\"}" http://127.0.0.1:18084/api/staff/binding)
[[ "$duplicate_binding_status" == "409" ]]
curl -sS -o "$verify_dir/staff-records.json" -H "Authorization: Bearer $staff_access" http://127.0.0.1:18084/api/staff/verifications/mine
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("records").length >= 3 ? 0 : 1)' "$verify_dir/staff-records.json"
curl -sS -o "$verify_dir/employee-disabled.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t9-employee-disable-1' -X PATCH --data '{"employeeStatus":"DISABLED"}' "http://127.0.0.1:18084/api/venue/employees/$employee_id/status"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("data").fetch("employeeStatus") == "DISABLED" ? 0 : 1)' "$verify_dir/employee-disabled.json"
disabled_staff_status=$(curl -sS -o "$verify_dir/disabled-staff-session.json" -w '%{http_code}' -H "Authorization: Bearer $staff_access" http://127.0.0.1:18084/api/staff/binding/session)
[[ "$disabled_staff_status" == "401" ]]
step 'employee binding, one-time code, QR/short-code precheck, duplicate verification, cancellation boundary and session revocation validated'

curl -sS -o "$verify_dir/venue-cancel.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t8-venue-cancel-1' --data '{"reason":"T8 venue cannot fulfill"}' "http://127.0.0.1:18084/api/venue/orders/$venue_order_id/cancel"
venue_refund_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); data=body.fetch("data"); exit 1 unless body.fetch("code") == 1 && data.fetch("refundId"); puts data.fetch("refundId")' "$verify_dir/venue-cancel.json")
curl -sS -o "$verify_dir/venue-refund-success.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_b" \
    -H 'Idempotency-Key: t8-venue-refund-success-1' --data '{"outcome":"SUCCESS"}' "http://127.0.0.1:18084/api/mini/refunds/$venue_refund_id/mock-result"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("data").fetch("refundStatus") == "REFUND_SUCCEEDED" ? 0 : 1)' "$verify_dir/venue-refund-success.json"

late_booking_payload=$(printf '{"inventorySlotId":"%s","contactName":"T6 User A","contactPhone":"13800138000","privacyPolicyVersion":"v1","privacyConsentScene":"booking"}' "$full_22_slot_id")
curl -sS -o "$verify_dir/late-booking.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" -H 'X-Device-Id: t6-device-a' \
    -H 'Idempotency-Key: t8-late-booking-1' --data "$late_booking_payload" http://127.0.0.1:18084/api/mini/bookings
late_order_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/late-booking.json")
curl -sS -o "$verify_dir/late-payment.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t8-late-payment-1' --data "{\"orderId\":\"$late_order_id\"}" http://127.0.0.1:18084/api/mini/payments
late_payment_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/late-payment.json")
curl -sS -o "$verify_dir/late-cancel.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t8-late-cancel-1' -X POST "http://127.0.0.1:18084/api/mini/bookings/$late_order_id/cancel"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("refundId").nil? ? 0 : 1)' "$verify_dir/late-cancel.json"
curl -sS -o "$verify_dir/late-payment-success.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t8-late-payment-success-1' --data "{\"outcome\":\"SUCCESS\",\"providerEventNo\":\"$late_payment_event\"}" "http://127.0.0.1:18084/api/mini/payments/$late_payment_id/mock-result"
late_refund_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); data=body.fetch("data"); exit 1 unless data.fetch("paymentStatus") == "SUCCEEDED" && data.fetch("refundId"); puts data.fetch("refundId")' "$verify_dir/late-payment-success.json")
curl -sS -o "$verify_dir/late-refund-success.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t8-late-refund-success-1' --data '{"outcome":"SUCCESS"}' "http://127.0.0.1:18084/api/mini/refunds/$late_refund_id/mock-result"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("data").fetch("refundStatus") == "REFUND_SUCCEEDED" ? 0 : 1)' "$verify_dir/late-refund-success.json"
step 'user and venue cancellation, refund outbox, unknown payment retention and late-payment refund validated'

auto_booking_payload=$(printf '{"inventorySlotId":"%s","contactName":"T9 User A","contactPhone":"13800138000","privacyPolicyVersion":"v1","privacyConsentScene":"booking"}' "$full_22_slot_id")
curl -sS -o "$verify_dir/auto-booking.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" -H 'X-Device-Id: t6-device-a' \
    -H 'Idempotency-Key: t9-auto-booking-1' --data "$auto_booking_payload" http://127.0.0.1:18084/api/mini/bookings
auto_order_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/auto-booking.json")
curl -sS -o "$verify_dir/auto-payment.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t9-auto-payment-1' --data "{\"orderId\":\"$auto_order_id\"}" http://127.0.0.1:18084/api/mini/payments
auto_payment_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/auto-payment.json")
curl -sS -o "$verify_dir/auto-payment-success.json" -H 'Content-Type: application/json' -H "Authorization: Bearer $mini_access_a" \
    -H 'Idempotency-Key: t9-auto-payment-success-1' --data "{\"outcome\":\"SUCCESS\",\"providerEventNo\":\"$auto_complete_event\"}" "http://127.0.0.1:18084/api/mini/payments/$auto_payment_id/mock-result"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("data").fetch("paymentStatus") == "SUCCEEDED" ? 0 : 1)' "$verify_dir/auto-payment-success.json"
docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" sports-booking-mysql mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" \
    -e "UPDATE booking_order SET inventory_date = DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00')), start_time = TIME(DATE_SUB(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00'), INTERVAL 3 MINUTE)), end_time = TIME(DATE_SUB(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00'), INTERVAL 1 MINUTE)) WHERE id = $auto_order_id"
auto_completed=false
for attempt in {1..12}; do
    curl -sS -o "$verify_dir/auto-order.json" -H "Authorization: Bearer $mini_access_a" "http://127.0.0.1:18084/api/mini/bookings/$auto_order_id"
    if ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("data").fetch("orderStatus") == "COMPLETED" ? 0 : 1)' "$verify_dir/auto-order.json"; then
        auto_completed=true
        break
    fi
    sleep 1
done
[[ "$auto_completed" == true ]]
step 'idempotent automatic completion validates completed order state'

docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" sports-booking-mysql mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" \
    -e "UPDATE booking_order SET completed_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR) WHERE id = $auto_order_id AND order_status = 'COMPLETED'"
billing_period=$(TZ=Asia/Shanghai ruby -rdate -e 'puts Date.today.strftime("%Y-%m")')
ledger_ready=false
for attempt in {1..12}; do
    curl -sS -o "$verify_dir/fee-ledgers.json" -H "Cookie: $venue_cookie" "http://127.0.0.1:18084/api/venue/billing/fee-ledgers?billingPeriod=$billing_period"
    if ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); found=body.fetch("data").fetch("records").find { |item| item.fetch("orderId") == ARGV[1] && item.fetch("entryType") == "ACCRUAL" }; exit(found ? 0 : 1)' "$verify_dir/fee-ledgers.json" "$auto_order_id"; then
        ledger_ready=true
        break
    fi
    sleep 1
done
[[ "$ledger_ready" == true ]]
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); item=body.fetch("data").fetch("records").find { |row| row.fetch("orderId") == ARGV[1] }; exit(item.fetch("feeAmountFen") == 120 ? 0 : 1)' "$verify_dir/fee-ledgers.json" "$auto_order_id"
curl -sS -o "$verify_dir/bill-generate.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t10-bill-generate-1' --data "{\"billingPeriod\":\"$billing_period\"}" http://127.0.0.1:18084/api/admin/billing/monthly-bills/generate
bill_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); item=body.fetch("data").fetch("records").find { |row| row.fetch("storeId") == ARGV[1] }; exit 1 unless body.fetch("code") == 1 && item && item.fetch("payableFeeFen") == 120; puts item.fetch("id")' "$verify_dir/bill-generate.json" "$store_id")
due_date=$(TZ=Asia/Shanghai ruby -rdate -e 'puts (Date.today + 7).to_s')
curl -sS -o "$verify_dir/bill-issue.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t10-bill-issue-1' --data "{\"dueDate\":\"$due_date\"}" "http://127.0.0.1:18084/api/admin/billing/monthly-bills/$bill_id/issue"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("data").fetch("billStatus") == "ISSUED" ? 0 : 1)' "$verify_dir/bill-issue.json"
curl -sS -o "$verify_dir/bill-payment.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t10-bill-payment-1' --data '{"amountFen":120,"remark":"T10 offline receipt"}' "http://127.0.0.1:18084/api/admin/billing/monthly-bills/$bill_id/payments"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("data").fetch("billStatus") == "PAID" ? 0 : 1)' "$verify_dir/bill-payment.json"
curl -sS -o "$verify_dir/venue-bills.json" -H "Cookie: $venue_cookie" "http://127.0.0.1:18084/api/venue/billing/monthly-bills?billingPeriod=$billing_period"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); item=body.fetch("data").fetch("records").find { |row| row.fetch("id") == ARGV[1] }; exit(body.fetch("code") == 1 && item && item.fetch("billStatus") == "PAID" ? 0 : 1)' "$verify_dir/venue-bills.json" "$bill_id"
step 'completed-order service fee, immutable ledger, monthly bill issue and offline payment validated'

curl -sS -o "$verify_dir/mini-refresh.json" -H 'Content-Type: application/json' --data "{\"refreshToken\":\"$mini_refresh_a\"}" http://127.0.0.1:18084/api/mini/auth/refresh
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/mini-refresh.json"
old_refresh_status=$(curl -sS -o "$verify_dir/old-refresh.json" -w '%{http_code}' -H 'Content-Type: application/json' --data "{\"refreshToken\":\"$mini_refresh_a\"}" http://127.0.0.1:18084/api/mini/auth/refresh)
[[ "$old_refresh_status" == "401" ]]

curl -sS -o "$verify_dir/organizations-list.json" -H "Cookie: $platform_cookie" "http://127.0.0.1:18084/api/admin/organizations?page=1&pageSize=100"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); row=body.fetch("data").fetch("records").find { |item| item.fetch("organizationId") == ARGV[1] }; exit(body.fetch("code") == 1 && row && row.fetch("storeId") == ARGV[2] ? 0 : 1)' "$verify_dir/organizations-list.json" "$organization_id" "$store_id"
curl -sS -o "$verify_dir/platform-orders.json" -H "Cookie: $platform_cookie" "http://127.0.0.1:18084/api/admin/orders?page=1&pageSize=100"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("records").any? { |item| item.fetch("id") == ARGV[1] } ? 0 : 1)' "$verify_dir/platform-orders.json" "$auto_order_id"
curl -sS -o "$verify_dir/venue-orders.json" -H "Cookie: $venue_cookie" "http://127.0.0.1:18084/api/venue/orders?page=1&pageSize=100"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("records").any? { |item| item.fetch("id") == ARGV[1] } ? 0 : 1)' "$verify_dir/venue-orders.json" "$auto_order_id"
curl -sS -o "$verify_dir/reveal-phone.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t11-reveal-phone-1' --data '{"reason":"T11 order support verification"}' "http://127.0.0.1:18084/api/admin/orders/$auto_order_id/contact-phone/reveal"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("contactPhone") == "13800138000" ? 0 : 1)' "$verify_dir/reveal-phone.json"
curl -sS -o "$verify_dir/audit-logs.json" -H "Cookie: $platform_cookie" "http://127.0.0.1:18084/api/admin/audit-logs?page=1&pageSize=100"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("records").any? { |item| item.fetch("action") == "BOOKING_CONTACT_PHONE_REVEALED" } ? 0 : 1)' "$verify_dir/audit-logs.json"
curl -sS -o "$verify_dir/organization-pause.json" -X PATCH -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t11-organization-pause-1' --data '{"status":"PAUSED"}' "http://127.0.0.1:18084/api/admin/organizations/$organization_id/status"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/organization-pause.json"
curl -sS -o "$verify_dir/organization-gate-paused.json" -H "Cookie: $platform_cookie" "http://127.0.0.1:18084/api/admin/organizations/$organization_id/transaction-gate"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && !body.fetch("data").fetch("allowsNewTransaction") ? 0 : 1)' "$verify_dir/organization-gate-paused.json"
curl -sS -o "$verify_dir/organization-resume.json" -X PATCH -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t11-organization-resume-1' --data '{"status":"ACTIVE"}' "http://127.0.0.1:18084/api/admin/organizations/$organization_id/status"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/organization-resume.json"
reset_venue_password=$(openssl rand -hex 24)
curl -sS -o "$verify_dir/reset-venue-password.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t11-reset-venue-password-1' --data "{\"newPassword\":\"$reset_venue_password\"}" "http://127.0.0.1:18084/api/admin/organizations/$organization_id/admin-password/reset"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/reset-venue-password.json"
old_venue_session_status=$(curl -sS -o "$verify_dir/old-venue-session.json" -w '%{http_code}' -H "Cookie: $venue_cookie" http://127.0.0.1:18084/api/venue/profile)
[[ "$old_venue_session_status" == "401" ]]
curl -sS -o "$verify_dir/reset-venue-login.json" -H 'Content-Type: application/json' --data "{\"username\":\"$venue_user\",\"password\":\"$reset_venue_password\"}" http://127.0.0.1:18084/api/admin/auth/login
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("forcePasswordChange") ? 0 : 1)' "$verify_dir/reset-venue-login.json"
step 'platform venue list, order query and phone audit, organization gate and venue admin password reset validated'
printf 'T-05/T-06/T-07/T-08/T-09/T-10/T-11 runtime verification passed: V6-V11 migration, shared inventory, Mock payment/refund, employee verification, automatic completion, billing, management order audit and venue administration state transitions\n'
