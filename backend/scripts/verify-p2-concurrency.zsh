#!/usr/bin/env zsh
set -euo pipefail

api=${VERIFY_API_BASE:-http://127.0.0.1:8089/api}
suffix=$(date +%s)
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT INT TERM

request() {
  local method=$1 request_path=$2 header=$3 token=$4 body=${5:-}
  local args=(-sS -X "$method" "$api$request_path")
  [[ -n "$token" ]] && args+=(-H "$header: $token")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  curl "${args[@]}"
}

venue_token=$(request POST /auth/admin/login '' '' '{"username":"venue1","password":"venue123"}' | jq -er '.data.token')
mini_token=$(request POST /auth/mini/mock-login '' '' "{\"code\":\"p2-concurrency-$suffix\"}" | jq -er '.data.token')
venue_id=$(request GET /venue-admin/profile/detail X-Admin-Token "$venue_token" | jq -er '.data.id')
sport_id=$(request GET /venue-admin/sports/list X-Admin-Token "$venue_token" | jq -er '.data[0].id')
court_id=$(request POST /venue-admin/courts/create X-Admin-Token "$venue_token" \
  "{\"name\":\"P2并发$suffix\",\"sportIds\":[$sport_id]}" | jq -er '.data.id')
plan_id=$(request POST /venue-admin/recharge-plans X-Admin-Token "$venue_token" \
  '{"name":"并发充100","paidAmountFen":10000,"giftAmountFen":0}' | jq -er '.data.id')
recharge=$(request POST /mini/recharges/create X-Mini-Token "$mini_token" \
  "{\"venueId\":$venue_id,\"planId\":$plan_id,\"idempotencyKey\":\"concurrent-recharge-$suffix\"}")
recharge_no=$(print -r -- "$recharge" | jq -er '.data.rechargeNo')
request POST /mock/recharges/notify '' '' \
  "{\"businessNo\":\"$recharge_no\",\"amountFen\":10000,\"result\":\"SUCCESS\",\"signature\":\"local-mock-signature\"}" >/dev/null

slot_date=$(TZ=Asia/Shanghai date -v+1d +%F)
create_slot() {
  request POST /venue-admin/sessions/create X-Admin-Token "$venue_token" \
    "{\"courtId\":$court_id,\"sportTypeId\":$sport_id,\"date\":\"$slot_date\",\"startTime\":\"$1\",\"endTime\":\"$2\",\"priceFen\":$3}" \
    | jq -er '.data.id'
}

slot_a=$(create_slot 08:00 09:00 8000)
slot_b=$(create_slot 09:00 10:00 8000)
request POST /mini/bookings/create X-Mini-Token "$mini_token" \
  "{\"sessionSlotId\":$slot_a,\"contactName\":\"并发用户\",\"contactPhone\":\"13900000008\",\"idempotencyKey\":\"concurrent-a-$suffix\"}" \
  >"$tmp_dir/a.json" &
pid_a=$!
request POST /mini/bookings/create X-Mini-Token "$mini_token" \
  "{\"sessionSlotId\":$slot_b,\"contactName\":\"并发用户\",\"contactPhone\":\"13900000008\",\"idempotencyKey\":\"concurrent-b-$suffix\"}" \
  >"$tmp_dir/b.json" &
pid_b=$!
wait "$pid_a" "$pid_b"

success_count=$(jq -s '[.[] | select(.code == 0)] | length' "$tmp_dir/a.json" "$tmp_dir/b.json")
[[ "$success_count" -ge 1 ]] || { print -u2 '并发预约均失败'; exit 1; }
account=$(request GET "/mini/memberships/$venue_id/balance-flows" X-Mini-Token "$mini_token")
print -r -- "$account" | jq -e '.data.paidBalanceFen >= 0 and .data.giftBalanceFen >= 0' >/dev/null
print '[1/2] 同会员并发预约未超扣余额'

combo_slot=$(create_slot 10:00 11:00 12000)
combo=$(request POST /mini/bookings/create X-Mini-Token "$mini_token" \
  "{\"sessionSlotId\":$combo_slot,\"contactName\":\"并发用户\",\"contactPhone\":\"13900000008\",\"idempotencyKey\":\"concurrent-combo-$suffix\"}")
combo_id=$(print -r -- "$combo" | jq -er '.data.id')
detail=$(request GET "/mini/bookings/$combo_id" X-Mini-Token "$mini_token")
payment_no=$(print -r -- "$detail" | jq -er '.data.payment.businessNo')
external_amount=$(print -r -- "$detail" | jq -er '.data.composition.externalAmountFen')

request POST /mock/payments/notify '' '' \
  "{\"businessNo\":\"$payment_no\",\"amountFen\":$((external_amount + 1)),\"result\":\"SUCCESS\",\"signature\":\"local-mock-signature\"}" \
  | jq -e '.code == 1' >/dev/null
request GET "/mini/bookings/$combo_id" X-Mini-Token "$mini_token" \
  | jq -e '.data.booking.status == "PENDING_PAYMENT" and .data.payment.status == "PENDING" and all(.data.composition.lots[]; .status == "FROZEN")' >/dev/null

notification="{\"businessNo\":\"$payment_no\",\"amountFen\":$external_amount,\"result\":\"SUCCESS\",\"signature\":\"local-mock-signature\"}"
request POST /mock/payments/notify '' '' "$notification" >"$tmp_dir/notify-a.json" &
notify_a=$!
request POST /mock/payments/notify '' '' "$notification" >"$tmp_dir/notify-b.json" &
notify_b=$!
wait "$notify_a" "$notify_b"
request GET "/mini/bookings/$combo_id" X-Mini-Token "$mini_token" \
  | jq -e '.data.booking.status == "CONFIRMED" and .data.payment.status == "SUCCESS" and all(.data.composition.lots[]; .status == "CONSUMED")' >/dev/null
print '[2/2] 错误金额保持原状态，重复成功通知幂等消费'

print "P2 concurrency verification passed: booking=$combo_id"
