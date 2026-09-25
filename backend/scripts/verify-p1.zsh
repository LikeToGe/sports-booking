#!/bin/zsh
set -euo pipefail

verify_api_base="${VERIFY_API_BASE:-http://127.0.0.1:8089/api}"
verify_suffix="$(date +%s)"

request_json() {
  local method="$1" request_path="$2" header="$3" token="$4" body="${5:-}"
  local args=(-sS -X "$method" "$verify_api_base$request_path")
  [[ -n "$token" ]] && args+=(-H "$header: $token")
  [[ -n "$body" ]] && args+=(-H "Content-Type: application/json" -d "$body")
  curl "${args[@]}"
}

platform_token="$(request_json POST /auth/admin/login '' '' '{"username":"admin","password":"admin123"}' | jq -er '.data.token')"
venue_token="$(request_json POST /auth/admin/login '' '' '{"username":"venue1","password":"venue123"}' | jq -er '.data.token')"
mini_token="$(request_json POST /auth/mini/mock-login '' '' "{\"code\":\"p1-$verify_suffix\"}" | jq -er '.data.token')"
venue_id="$(request_json GET /venue-admin/profile/detail X-Admin-Token "$venue_token" | jq -er '.data.id')"
sport_id="$(request_json GET /venue-admin/sports/list X-Admin-Token "$venue_token" | jq -er '.data[0].id')"
court_id="$(request_json POST /venue-admin/courts/create X-Admin-Token "$venue_token" \
  "{\"name\":\"P1验证场地$verify_suffix\",\"sportIds\":[$sport_id]}" | jq -er '.data.id')"

create_slot() {
  request_json POST /venue-admin/sessions/create X-Admin-Token "$venue_token" \
    "{\"courtId\":$court_id,\"sportTypeId\":$sport_id,\"date\":\"$1\",\"startTime\":\"$2\",\"endTime\":\"$3\",\"priceFen\":$4}" \
    | jq -er '.data.id'
}

d1="$(TZ=Asia/Shanghai date -v+1d +%F)"
d2="$(TZ=Asia/Shanghai date -v+2d +%F)"
d3="$(TZ=Asia/Shanghai date -v+3d +%F)"
d4="$(TZ=Asia/Shanghai date -v+4d +%F)"
d5="$(TZ=Asia/Shanghai date -v+5d +%F)"
d6="$(TZ=Asia/Shanghai date -v+6d +%F)"

online_slot="$(create_slot "$d1" 10:00 11:00 6100)"
online_phone="139$verify_suffix"
online_booking="$(request_json POST /mini/bookings/create X-Mini-Token "$mini_token" \
  "{\"sessionSlotId\":$online_slot,\"contactName\":\"P1线上顾客\",\"contactPhone\":\"$online_phone\"}")"
online_booking_id="$(printf '%s' "$online_booking" | jq -er '.data.id')"
request_json POST "/mini/bookings/$online_booking_id/mock-pay" X-Mini-Token "$mini_token" \
  | jq -e '.data.paymentStatus == "PAID"' >/dev/null
membership="$(request_json GET /mini/memberships/list X-Mini-Token "$mini_token")"
printf '%s' "$membership" | jq -e --argjson venueId "$venue_id" \
  'any(.data[]; .venueId == $venueId and .status == "CUSTOMER" and .memberQrDataUrl == null and .paidBalanceFen == 0 and .giftBalanceFen == 0)' >/dev/null
member_code="$(printf '%s' "$membership" | jq -er --argjson venueId "$venue_id" '.data[] | select(.venueId == $venueId) | .memberCode')"
request_json GET "/mini/memberships/$venue_id" X-Mini-Token "$mini_token" \
  | jq -e '.data.memberQrDataUrl | startswith("data:image/png;base64,")' >/dev/null
customer_id="$(request_json GET "/venue-admin/customers/list?keyword=$online_phone" X-Admin-Token "$venue_token" | jq -er '.data[0].customerId')"
employee="$(request_json POST /venue-admin/employees/create X-Admin-Token "$venue_token" "{\"name\":\"P1会员识别$verify_suffix\"}")"
employee_id="$(printf '%s' "$employee" | jq -er '.data.employeeId')"
binding_code="$(printf '%s' "$employee" | jq -er '.data.bindingCode')"
request_json POST /mini/staff/bind X-Mini-Token "$mini_token" "{\"bindingCode\":\"$binding_code\"}" >/dev/null
customer_role_id="$(request_json POST /venue-admin/roles X-Admin-Token "$venue_token" \
  "{\"name\":\"P1会员识别$verify_suffix\",\"status\":\"ACTIVE\",\"permissions\":[\"CUSTOMER_VIEW\"]}" \
  | jq -er '.data.id')"
request_json PUT "/venue-admin/employees/$employee_id/roles" X-Admin-Token "$venue_token" \
  "{\"roleIds\":[$customer_role_id]}" | jq -e '.data == true' >/dev/null
request_json GET "/staff/customers/identify?venueId=$venue_id&memberCode=$member_code" X-Mini-Token "$mini_token" \
  | jq -e --argjson customerId "$customer_id" '.data.customerId == $customerId' >/dev/null
request_json GET /platform/customer-summaries X-Admin-Token "$platform_token" \
  | jq -e 'all(.data[]; has("venueId") and has("venueName") and has("customerCount") and has("memberCount") and (has("phone") | not) and (has("memberCode") | not))' >/dev/null
request_json GET "/venue-admin/sessions/list?date=$d1&sportTypeId=$sport_id" X-Admin-Token "$venue_token" \
  | jq -e --argjson bookingId "$online_booking_id" 'any(.data[]; .bookingId == $bookingId and .bookingSource == "ONLINE" and .paymentStatus == "PAID")' >/dev/null
print "[1/4] 顾客建档、员工会员识别、平台脱敏汇总与日视图通过"

offline_slot="$(create_slot "$d2" 14:00 15:00 6200)"
offline_booking_id="$(request_json POST /venue-admin/bookings/offline X-Admin-Token "$venue_token" \
  "{\"sessionSlotId\":$offline_slot,\"contactName\":\"P1线下顾客\",\"contactPhone\":\"13800000000\",\"paymentStatus\":\"UNPAID\",\"venueCustomerId\":$customer_id}" \
  | jq -er --argjson customerId "$customer_id" '.data | select(.paymentStatus == "UNPAID" and .venueCustomerId == $customerId) | .id')"
request_json PUT "/venue-admin/bookings/$offline_booking_id/payment-status" X-Admin-Token "$venue_token" \
  '{"paymentStatus":"PAID","reason":"顾客到店付款"}' | jq -e '.data.paymentStatus == "PAID"' >/dev/null
request_json POST "/venue-admin/bookings/$offline_booking_id/cancel" X-Admin-Token "$venue_token" \
  '{"reason":"顾客调整计划"}' | jq -e '.data.status == "CANCELLED" and .data.paymentStatus == "PAID"' >/dev/null
request_json PUT "/venue-admin/bookings/$offline_booking_id/payment-status" X-Admin-Token "$venue_token" \
  '{"paymentStatus":"REFUNDED","reason":"线下退款已完成"}' | jq -e '.data.paymentStatus == "REFUNDED"' >/dev/null
print "[2/4] 线下登记收款与取消后退款登记通过"

batch_slot="$(create_slot "$d3" 12:00 13:00 6300)"
create_slot "$d4" 12:00 13:00 6300 >/dev/null
create_slot "$d5" 12:00 13:00 6300 >/dev/null
batch_body="{\"sessionSlotId\":$batch_slot,\"dates\":[\"$d3\",\"$d4\",\"$d3\",\"$d5\"],\"contactName\":\"P1批量顾客\",\"contactPhone\":\"13800000001\",\"paymentStatus\":\"PAID\"}"
request_json POST /venue-admin/bookings/offline/batch X-Admin-Token "$venue_token" "$batch_body" \
  | jq -e '.data | length == 3' >/dev/null
request_json POST /venue-admin/bookings/offline/batch X-Admin-Token "$venue_token" "$batch_body" \
  | jq -e --arg d3 "$d3" --arg d4 "$d4" --arg d5 "$d5" \
    '.code == 1 and (.data == [$d3, $d4, $d5])' >/dev/null
print "[3/4] 批量去重、整批创建与全冲突日期返回通过"

reschedule_from="$(create_slot "$d5" 16:00 17:00 6400)"
reschedule_to="$(create_slot "$d6" 16:00 17:00 6400)"
reschedule_booking_id="$(request_json POST /venue-admin/bookings/offline X-Admin-Token "$venue_token" \
  "{\"sessionSlotId\":$reschedule_from,\"contactName\":\"P1改期顾客\",\"contactPhone\":\"13800000002\",\"paymentStatus\":\"PAID\"}" | jq -er '.data.id')"
request_json POST "/venue-admin/bookings/$reschedule_booking_id/reschedule" X-Admin-Token "$venue_token" \
  "{\"sessionSlotId\":$reschedule_to,\"reason\":\"顾客调整日期\"}" \
  | jq -e --argjson slotId "$reschedule_to" '.data.sessionSlotId == $slotId and .data.paymentStatus == "PAID"' >/dev/null
print "[4/4] 线下同价改期与收款状态保持通过"

print "P1 runtime verification passed: online=$online_booking_id offline=$offline_booking_id reschedule=$reschedule_booking_id"
