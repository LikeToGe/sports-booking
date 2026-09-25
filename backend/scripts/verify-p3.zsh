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

platform_token="$(request_json POST /auth/admin/login '' '' \
  "{\"username\":\"$verify_platform_username\",\"password\":\"$verify_platform_password\"}" | jq -er '.data.token')"
venue_token="$(request_json POST /auth/admin/login '' '' \
  "{\"username\":\"$verify_venue_username\",\"password\":\"$verify_venue_password\"}" | jq -er '.data.token')"
mini_token="$(request_json POST /auth/mini/mock-login '' '' \
  "{\"code\":\"p3-verify-$verify_suffix\"}" | jq -er '.data.token')"
venue_id="$(request_json GET /venue-admin/profile/detail X-Admin-Token "$venue_token" | jq -er '.data.id')"
other_venue_id="$(request_json GET /platform/venues/list X-Admin-Token "$platform_token" \
  | jq -er --argjson venueId "$venue_id" '.data[] | select(.venue.id != $venueId) | .venue.id' | head -n 1)"
print '[1/6] 管理员与小程序会话通过'

employee="$(request_json POST /venue-admin/employees/create X-Admin-Token "$venue_token" \
  "{\"name\":\"P3验收员工$verify_suffix\"}")"
employee_id="$(printf '%s' "$employee" | jq -er '.data.employeeId')"
binding_code="$(printf '%s' "$employee" | jq -er '.data.bindingCode')"
request_json POST /mini/staff/bind X-Mini-Token "$mini_token" \
  "{\"bindingCode\":\"$binding_code\"}" | jq -e '.data.status == "ACTIVE"' >/dev/null

viewer_role="$(request_json POST /venue-admin/roles X-Admin-Token "$venue_token" \
  "{\"name\":\"P3查看$verify_suffix\",\"status\":\"ACTIVE\",\"permissions\":[\"DASHBOARD_VIEW\",\"REPORT_VIEW\"]}")"
viewer_role_id="$(printf '%s' "$viewer_role" | jq -er '.data.id')"
export_role="$(request_json POST /venue-admin/roles X-Admin-Token "$venue_token" \
  "{\"name\":\"P3导出$verify_suffix\",\"status\":\"ACTIVE\",\"permissions\":[\"REPORT_EXPORT\"]}")"
export_role_id="$(printf '%s' "$export_role" | jq -er '.data.id')"
request_json PUT "/venue-admin/employees/$employee_id/roles" X-Admin-Token "$venue_token" \
  "{\"roleIds\":[$viewer_role_id,$export_role_id]}" | jq -e '.data == true' >/dev/null
request_json GET /staff/venues X-Mini-Token "$mini_token" | jq -e --argjson venueId "$venue_id" \
  'any(.data[]; .venueId == $venueId and (.permissions | index("REPORT_VIEW")) and (.permissions | index("REPORT_EXPORT")))' >/dev/null
print '[2/6] 多角色权限并集通过'

ticket="$(request_json POST /auth/admin/web-login-tickets '' '' | jq -er '.data.ticket')"
request_json POST "/staff/web-login-tickets/$ticket/confirm" X-Mini-Token "$mini_token" \
  "{\"venueId\":$venue_id}" | jq -e '.data == true' >/dev/null
request_json GET "/auth/admin/web-login-tickets/$ticket" '' '' | jq -e '.data.status == "CONFIRMED"' >/dev/null
login="$(request_json POST "/auth/admin/web-login-tickets/$ticket/consume" '' '')"
employee_token="$(printf '%s' "$login" | jq -er '.data.token')"
printf '%s' "$login" | jq -e '.data.principal.role == "VENUE_EMPLOYEE"' >/dev/null
request_json POST "/auth/admin/web-login-tickets/$ticket/consume" '' '' \
  | jq -e '.code == 1' >/dev/null
print '[3/6] 扫码确认、一次消费与真实后台会话通过'

request_json GET '/venue-admin/reports/dashboard?days=7' X-Admin-Token "$employee_token" \
  | jq -e '.data.daily | length == 7' >/dev/null
start_date="$(TZ=Asia/Shanghai date -v-6d +%F)"
end_date="$(TZ=Asia/Shanghai date +%F)"
for export_name in bookings member-flows receipts; do
  export_file="$(mktemp -t "p3-$export_name").xlsx"
  curl -fsS -H "X-Admin-Token: $employee_token" \
    "$verify_api_base/venue-admin/exports/$export_name?startDate=$start_date&endDate=$end_date" \
    -o "$export_file"
  unzip -t "$export_file" >/dev/null
  mv "$export_file" "$export_file.verified"
  rm -f "$export_file.verified"
done
print '[4/6] 工作台与三类 Excel 导出通过'

request_json PUT "/venue-admin/employees/$employee_id/roles" X-Admin-Token "$venue_token" \
  "{\"roleIds\":[$export_role_id]}" | jq -e '.data == true' >/dev/null
request_json GET '/venue-admin/reports/dashboard?days=7' X-Admin-Token "$employee_token" \
  | jq -e '.code == 1 and .msg == "无权执行该操作"' >/dev/null
print '[5/6] 撤权后下一请求生效'

cross_ticket="$(request_json POST /auth/admin/web-login-tickets '' '' | jq -er '.data.ticket')"
request_json POST "/staff/web-login-tickets/$cross_ticket/confirm" X-Mini-Token "$mini_token" \
  "{\"venueId\":$other_venue_id}" | jq -e '.code == 1' >/dev/null
request_json PATCH "/venue-admin/employees/$employee_id/status" X-Admin-Token "$venue_token" \
  '{"status":"DISABLED"}' | jq -e '.data.status == "DISABLED"' >/dev/null
request_json GET "/venue-admin/reports/receipts?startDate=$start_date&endDate=$end_date" \
  X-Admin-Token "$employee_token" | jq -e '.code == 1' >/dev/null
print '[6/6] 跨场馆拒绝与停用即时生效'

print "P3 runtime verification passed: venue=$venue_id employee=$employee_id"
