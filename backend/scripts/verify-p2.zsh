#!/usr/bin/env zsh
set -euo pipefail

api="${VERIFY_API_BASE:-http://127.0.0.1:8089/api}"
suffix="$(date +%s)"

request() {
  local method=$1 request_path=$2 header=$3 token=$4 body=${5:-}
  local args=(-sS -X "$method" "$api$request_path")
  [[ -n "$token" ]] && args+=(-H "$header: $token")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  curl "${args[@]}"
}

venue_token=$(request POST /auth/admin/login '' '' '{"username":"venue1","password":"venue123"}' | jq -er '.data.token')
mini_token=$(request POST /auth/mini/mock-login '' '' "{\"code\":\"p2-$suffix\"}" | jq -er '.data.token')
print '[0.1/4] 登录通过'
venue_id=$(request GET /venue-admin/profile/detail X-Admin-Token "$venue_token" | jq -er '.data.id')
sport_id=$(request GET /venue-admin/sports/list X-Admin-Token "$venue_token" | jq -er '.data[0].id')
print '[0.2/4] 场馆资料通过'
court_id=$(request POST /venue-admin/courts/create X-Admin-Token "$venue_token" \
  "{\"name\":\"P2资金验证$suffix\",\"sportIds\":[$sport_id]}" | jq -er '.data.id')
print '[0.3/4] 场地创建通过'
plan=$(request POST /venue-admin/recharge-plans X-Admin-Token "$venue_token" \
  '{"name":"充100送10","paidAmountFen":10000,"giftAmountFen":1000,"giftValidDays":30}')
plan_id=$(print -r -- "$plan" | jq -er '.data.id')
print '[0.4/4] 档位创建通过'

recharge=$(request POST /mini/recharges/create X-Mini-Token "$mini_token" \
  "{\"venueId\":$venue_id,\"planId\":$plan_id,\"idempotencyKey\":\"mini-$suffix\"}")
recharge_id=$(print -r -- "$recharge" | jq -er '.data.id')
recharge_no=$(print -r -- "$recharge" | jq -er '.data.rechargeNo')
request POST /mock/recharges/notify '' '' \
  "{\"businessNo\":\"$recharge_no\",\"amountFen\":10000,\"result\":\"SUCCESS\",\"signature\":\"local-mock-signature\"}" \
  | jq -e '.data.status == "SUCCESS"' >/dev/null
request POST /mock/recharges/notify '' '' \
  "{\"businessNo\":\"$recharge_no\",\"amountFen\":10000,\"result\":\"SUCCESS\",\"signature\":\"local-mock-signature\"}" \
  | jq -e --argjson id "$recharge_id" '.data.id == $id and .data.status == "SUCCESS"' >/dev/null
account=$(request GET "/mini/memberships/$venue_id/balance-flows" X-Mini-Token "$mini_token")
print -r -- "$account" | jq -e '.data.paidBalanceFen == 10000 and .data.giftBalanceFen == 1000' >/dev/null
print '[1/4] 充值、重复成功通知与会员激活通过'

slot_date=$(TZ=Asia/Shanghai date -v+1d +%F)
create_slot() {
  request POST /venue-admin/sessions/create X-Admin-Token "$venue_token" \
    "{\"courtId\":$court_id,\"sportTypeId\":$sport_id,\"date\":\"$slot_date\",\"startTime\":\"$1\",\"endTime\":\"$2\",\"priceFen\":$3}" \
    | jq -er '.data.id'
}

full_slot=$(create_slot 18:00 19:00 6000)
full_booking=$(request POST /mini/bookings/create X-Mini-Token "$mini_token" \
  "{\"sessionSlotId\":$full_slot,\"contactName\":\"P2用户\",\"contactPhone\":\"13900000000\",\"idempotencyKey\":\"booking-full-$suffix\"}")
full_id=$(print -r -- "$full_booking" | jq -er '.data.id')
print -r -- "$full_booking" | jq -e '.data.status == "CONFIRMED" and .data.paymentStatus == "PAID"' >/dev/null
request GET "/mini/bookings/$full_id" X-Mini-Token "$mini_token" \
  | jq -e '.data.payment == null and .data.composition.externalAmountFen == 0' >/dev/null
request POST "/mini/bookings/$full_id/cancel" X-Mini-Token "$mini_token" \
  | jq -e '.data.status == "CANCELLED" and .data.paymentStatus == "REFUNDED"' >/dev/null
print '[2/4] 全余额无外部支付、取消原批次返还通过'

combo_slot=$(create_slot 19:00 20:00 12000)
combo=$(request POST /mini/bookings/create X-Mini-Token "$mini_token" \
  "{\"sessionSlotId\":$combo_slot,\"contactName\":\"P2用户\",\"contactPhone\":\"13900000000\",\"idempotencyKey\":\"booking-combo-$suffix\"}")
combo_id=$(print -r -- "$combo" | jq -er '.data.id')
combo_detail=$(request GET "/mini/bookings/$combo_id" X-Mini-Token "$mini_token")
payment_no=$(print -r -- "$combo_detail" | jq -er '.data.payment.businessNo')
print -r -- "$combo_detail" | jq -e '.data.composition.balanceAmountFen == 11000 and .data.composition.externalAmountFen == 1000' >/dev/null
request POST /mock/payments/notify '' '' \
  "{\"businessNo\":\"$payment_no\",\"amountFen\":1000,\"result\":\"UNKNOWN\",\"signature\":\"local-mock-signature\"}" \
  | jq -e '.data.status == "PENDING_PAYMENT"' >/dev/null
request POST "/mini/bookings/$combo_id/cancel" X-Mini-Token "$mini_token" \
  | jq -e '.code == 1 and .msg == "操作处理中"' >/dev/null
request POST /mock/payments/notify '' '' \
  "{\"businessNo\":\"$payment_no\",\"amountFen\":1000,\"result\":\"SUCCESS\",\"signature\":\"local-mock-signature\"}" \
  | jq -e '.data.status == "CONFIRMED"' >/dev/null
request POST "/mini/bookings/$combo_id/cancel" X-Mini-Token "$mini_token" \
  | jq -e '.data.status == "CANCELLED" and .data.paymentStatus == "REFUNDED"' >/dev/null
print '[3/4] 组合支付 UNKNOWN、成功、取消和原路退款通过'

failed_slot=$(create_slot 20:00 21:00 12000)
failed=$(request POST /mini/bookings/create X-Mini-Token "$mini_token" \
  "{\"sessionSlotId\":$failed_slot,\"contactName\":\"P2用户\",\"contactPhone\":\"13900000000\",\"idempotencyKey\":\"booking-failed-$suffix\"}")
failed_id=$(print -r -- "$failed" | jq -er '.data.id')
failed_payment=$(request GET "/mini/bookings/$failed_id" X-Mini-Token "$mini_token" | jq -er '.data.payment.businessNo')
request POST /mock/payments/notify '' '' \
  "{\"businessNo\":\"$failed_payment\",\"amountFen\":1000,\"result\":\"FAILED\",\"signature\":\"local-mock-signature\"}" \
  | jq -e '.data.status == "CANCELLED" and .data.paymentStatus == "PAYMENT_FAILED"' >/dev/null
customer_id=$(request GET '/venue-admin/customers/list?keyword=13900000000' X-Admin-Token "$venue_token" | jq -er '.data[0].customerId')
admin_recharge=$(request POST "/venue-admin/members/$customer_id/recharges" X-Admin-Token "$venue_token" \
  "{\"planId\":$plan_id,\"channel\":\"CASH\",\"idempotencyKey\":\"admin-$suffix\"}")
admin_recharge_id=$(print -r -- "$admin_recharge" | jq -er '.data.id')
request POST "/venue-admin/recharges/$admin_recharge_id/reverse" X-Admin-Token "$venue_token" \
  '{"reason":"验收撤销","offlineFundsHandled":true}' | jq -e '.data.status == "REVERSED"' >/dev/null
print '[4/4] 明确失败释放、后台充值与整笔撤销通过'

print "P2 runtime verification passed: recharge=$recharge_id full=$full_id combo=$combo_id failed=$failed_id"
