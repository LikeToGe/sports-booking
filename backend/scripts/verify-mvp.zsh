#!/bin/zsh
set -euo pipefail

verify_api_base="${VERIFY_API_BASE:-http://127.0.0.1:8089/api}"
verify_platform_username="${VERIFY_PLATFORM_USERNAME:-admin}"
verify_platform_password="${VERIFY_PLATFORM_PASSWORD:-admin123}"
verify_venue_username="${VERIFY_VENUE_USERNAME:-venue1}"
verify_venue_password="${VERIFY_VENUE_PASSWORD:-venue123}"
verify_suffix="$(date +%s)"

request_json() {
  local method="$1"
  local request_path="$2"
  local token_header="$3"
  local token="$4"
  local body="${5:-}"
  local request_args=(-sS -X "$method" "$verify_api_base$request_path")
  [[ -n "$token" ]] && request_args+=(-H "$token_header: $token")
  if [[ -n "$body" ]]; then
    request_args+=(-H "Content-Type: application/json" -d "$body")
  fi
  curl "${request_args[@]}"
}

platform_token="$(request_json POST /auth/admin/login '' '' "{\"username\":\"$verify_platform_username\",\"password\":\"$verify_platform_password\"}" | jq -er '.data.token')"
venue_count="$(request_json GET /platform/venues/list X-Admin-Token "$platform_token" | jq -er '.data | length')"
[[ "$venue_count" -ge 3 ]]

venue_token="$(request_json POST /auth/admin/login '' '' "{\"username\":\"$verify_venue_username\",\"password\":\"$verify_venue_password\"}" | jq -er '.data.token')"
mini_token="$(request_json POST /auth/mini/mock-login '' '' "{\"code\":\"verify-$verify_suffix\"}" | jq -er '.data.token')"
print "[1/6] 登录与场馆列表通过"

venue_profile="$(request_json GET /venue-admin/profile/detail X-Admin-Token "$venue_token")"
venue_id="$(printf '%s' "$venue_profile" | jq -er '.data.id')"
original_min_booking="$(printf '%s' "$venue_profile" | jq -er '.data.minBookingMinutes')"
original_service_fee_rate="$(request_json GET /platform/venues/list X-Admin-Token "$platform_token" \
  | jq -er --argjson venueId "$venue_id" '.data[] | select(.venue.id == $venueId) | .serviceFeeRateBasisPoints / 100')"
profile_without_lead_time="$(printf '%s' "$venue_profile" | jq -c '.data | {
  name, city, address, latitude, longitude, phone, facilities, entryNotice,
  bookingDaysAhead, minBookingMinutes: 0, cancelCutoffMinutes
}')"
profile_restore="$(printf '%s' "$venue_profile" | jq -c --argjson minutes "$original_min_booking" '.data | {
  name, city, address, latitude, longitude, phone, facilities, entryNotice,
  bookingDaysAhead, minBookingMinutes: $minutes, cancelCutoffMinutes
}')"

restore_profile() {
  request_json PUT /venue-admin/profile/update X-Admin-Token "$venue_token" "$profile_restore" >/dev/null || true
  request_json PATCH "/platform/venues/$venue_id/service-fee-rate" X-Admin-Token "$platform_token" \
    "{\"serviceFeeRatePercent\":$original_service_fee_rate}" >/dev/null || true
}
trap restore_profile EXIT

request_json PATCH "/platform/venues/$venue_id/service-fee-rate" X-Admin-Token "$platform_token" \
  '{"serviceFeeRatePercent":5.25}' | jq -e '.data.serviceFeeRateBasisPoints == 525' >/dev/null
print "[2/6] 场馆服务费率配置通过"

employee="$(request_json POST /venue-admin/employees/create X-Admin-Token "$venue_token" "{\"name\":\"验证员工$verify_suffix\"}")"
employee_id="$(printf '%s' "$employee" | jq -er '.data.employeeId')"
binding_code="$(printf '%s' "$employee" | jq -er '.data.bindingCode')"
request_json POST /mini/staff/bind X-Mini-Token "$mini_token" "{\"bindingCode\":\"$binding_code\"}" \
  | jq -e '.data.status == "ACTIVE" and (.data | has("bindingCodeHash") | not)' >/dev/null
verify_role_id="$(request_json POST /venue-admin/roles X-Admin-Token "$venue_token" \
  "{\"name\":\"验证核销$verify_suffix\",\"status\":\"ACTIVE\",\"permissions\":[\"BOOKING_VERIFY\"]}" \
  | jq -er '.data.id')"
request_json PUT "/venue-admin/employees/$employee_id/roles" X-Admin-Token "$venue_token" \
  "{\"roleIds\":[$verify_role_id]}" | jq -e '.data == true' >/dev/null
print "[3/6] 员工创建与绑定通过"

sport_id="$(request_json GET /venue-admin/sports/list X-Admin-Token "$venue_token" | jq -er '.data[0].id')"
sport_name="$(request_json GET /venue-admin/sports/list X-Admin-Token "$venue_token" \
  | jq -er --argjson sportId "$sport_id" '.data[] | select(.id == $sportId) | .name')"
court_id="$(request_json POST /venue-admin/courts/create X-Admin-Token "$venue_token" \
  "{\"name\":\"验证场地$verify_suffix\",\"sportIds\":[$sport_id]}" | jq -er '.data.id')"
print "[4.1/6] 场地与运动类型读取通过"
request_json GET "/mini/venues?sportTypeId=$sport_id" X-Mini-Token "$mini_token" \
  | jq -e --arg sportName "$sport_name" 'all(.data[]; .sportNames == $sportName)' >/dev/null
print "[4.2/6] 小程序运动筛选通过"
slot_date="$(TZ=Asia/Shanghai date +%F)"
slot_start="$(TZ=Asia/Shanghai date -v+20M +%H:%M)"
slot_end="$(TZ=Asia/Shanghai date -v+80M +%H:%M)"
slot="$(request_json POST /venue-admin/sessions/create X-Admin-Token "$venue_token" "{\"courtId\":$court_id,\"sportTypeId\":$sport_id,\"date\":\"$slot_date\",\"startTime\":\"$slot_start\",\"endTime\":\"$slot_end\",\"priceFen\":6600}")"
slot_id="$(printf '%s' "$slot" | jq -er '.data.id')"
print "[4.3/6] 临时场次创建通过"
overlap_start="$(TZ=Asia/Shanghai date -v+40M +%H:%M)"
overlap_end="$(TZ=Asia/Shanghai date -v+70M +%H:%M)"
request_json POST /venue-admin/sessions/create X-Admin-Token "$venue_token" "{\"courtId\":$court_id,\"sportTypeId\":$sport_id,\"date\":\"$slot_date\",\"startTime\":\"$overlap_start\",\"endTime\":\"$overlap_end\",\"priceFen\":6600}" \
  | jq -e '.code == 1 and .msg == "场次时间冲突"' >/dev/null
print "[4.4/6] 场次重叠校验通过"

request_json POST /mini/bookings/create X-Mini-Token "$mini_token" "{\"sessionSlotId\":$slot_id,\"contactName\":\"截止验证\",\"contactPhone\":\"13800000003\"}" \
  | jq -e '.code == 1 and .msg == "场次不可预约"' >/dev/null
print "[4.5/6] 最晚预约规则通过"
request_json PUT /venue-admin/profile/update X-Admin-Token "$venue_token" "$profile_without_lead_time" >/dev/null
print "[4/6] 场次冲突与最晚预约规则通过"

booking="$(request_json POST /mini/bookings/create X-Mini-Token "$mini_token" "{\"sessionSlotId\":$slot_id,\"contactName\":\"运行验证\",\"contactPhone\":\"13800000003\"}")"
booking_id="$(printf '%s' "$booking" | jq -er '.data.id')"
booking_no="$(printf '%s' "$booking" | jq -er '.data.bookingNo')"
request_json POST "/mini/bookings/$booking_id/mock-pay" X-Mini-Token "$mini_token" | jq -e '.data.status == "CONFIRMED"' >/dev/null
print "[5.1/6] 在线预约支付通过"
short_code="$(request_json GET "/mini/bookings/$booking_id" X-Mini-Token "$mini_token" | jq -er '.data.credential.shortCode')"
print "[5.2/6] 核销凭证读取通过"
request_json POST "/staff/verifications/verify?venueId=$venue_id" X-Mini-Token "$mini_token" \
  "{\"credential\":\"$short_code\",\"method\":\"SHORT_CODE\"}" \
  | jq -e '.data.status == "COMPLETED" and (.data | has("contactPhone") | not)' >/dev/null
print "[5.3/6] 员工核销通过"
request_json GET "/staff/verifications/records?venueId=$venue_id" X-Mini-Token "$mini_token" \
  | jq -e '.data[0].courtName != null' >/dev/null
billing_month="$(TZ=Asia/Shanghai date +%Y-%m)"
request_json GET "/venue-admin/reports/service-fees?billingMonth=$billing_month" X-Admin-Token "$venue_token" \
  | jq -e --arg bookingNo "$booking_no" \
    'any(.data.bookings[]; .bookingNo == $bookingNo and .serviceFeeRateBasisPoints == 525 and .serviceFeeYuan == "3.465")' >/dev/null
print "[5/6] 支付、核销与服务费精度通过"

request_json POST /venue-admin/weekly-slots/copy-day X-Admin-Token "$venue_token" '{"sourceDay":1,"targetDays":[2]}' >/dev/null
request_json POST /venue-admin/weekly-slots/copy-day X-Admin-Token "$venue_token" '{"sourceDay":1,"targetDays":[2]}' >/dev/null
request_json GET /venue-admin/weekly-slots/list X-Admin-Token "$venue_token" | jq -e '[.data[] | select(.dayOfWeek == 2 and .status == "ACTIVE")] | length == 6' >/dev/null

future_date="$(TZ=Asia/Shanghai date -v+1d +%F)"
venue_detail="$(request_json GET "/mini/venues/1?date=$future_date" X-Mini-Token "$mini_token")"
cancel_slot_id="$(printf '%s' "$venue_detail" | jq -er '.data.slots[0].id')"
cancel_booking="$(request_json POST /mini/bookings/create X-Mini-Token "$mini_token" "{\"sessionSlotId\":$cancel_slot_id,\"contactName\":\"取消验证\",\"contactPhone\":\"13800000003\"}")"
cancel_booking_id="$(printf '%s' "$cancel_booking" | jq -er '.data.id')"
request_json POST "/mini/bookings/$cancel_booking_id/mock-pay" X-Mini-Token "$mini_token" | jq -e '.data.status == "CONFIRMED"' >/dev/null
request_json POST "/mini/bookings/$cancel_booking_id/cancel" X-Mini-Token "$mini_token" | jq -e '.data.status == "CANCELLED"' >/dev/null
request_json GET "/mini/bookings/$cancel_booking_id" X-Mini-Token "$mini_token" | jq -e '.data.refund.status == "SUCCESS"' >/dev/null
print "[6/6] 排期复制与取消退款通过"

restore_profile
trap - EXIT
print "MVP runtime verification passed: venues=$venue_count booking=$booking_id cancel=$cancel_booking_id"
