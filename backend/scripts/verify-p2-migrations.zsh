#!/usr/bin/env zsh
set -euo pipefail

script_dir=${0:A:h}
backend_dir=${script_dir:h}
migrations=${backend_dir}/sports-booking-server/src/main/resources/db/migration
container="sports-booking-p2-migration-$$"

cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker run --rm -d --name "$container" -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8.4 >/dev/null
for attempt in {1..40}; do
  if docker exec "$container" mysqladmin ping -uroot --silent >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$container" mysqladmin ping -uroot --silent >/dev/null

mysql_exec() {
  docker exec -i "$container" mysql -uroot "$@"
}

apply_migration() {
  local database=$1
  local file=$2
  docker exec -i "$container" mysql -uroot "$database" < "$file"
}

mysql_exec -e "CREATE DATABASE p2_empty; CREATE DATABASE p2_upgrade;"
for version in 1 2 3 4 5 6; do
  file=(${migrations}/V${version}__*.sql)
  apply_migration p2_empty "$file"
done

for version in 1 2 3 4; do
  file=(${migrations}/V${version}__*.sql)
  apply_migration p2_upgrade "$file"
done

mysql_exec p2_upgrade <<'SQL'
INSERT INTO s_venue (id, name, address, phone, status) VALUES (1, '迁移场馆', '测试地址', '00000000000', 'ACTIVE');
INSERT INTO s_user_account (id, mock_openid, contact_name, phone) VALUES (1, 'migration-user', '迁移用户', '00000000001');
INSERT INTO s_court (id, venue_id, name) VALUES (1, 1, '1号场');
INSERT INTO s_session_slot
    (id, venue_id, court_id, sport_type_id, manual_override, template_slot_date, slot_date,
     start_at, end_at, price_fen, status)
VALUES
    (1, 1, 1, 1, 0, NULL, '2026-10-01', '2026-10-01 10:00:00', '2026-10-01 11:00:00', 6000, 'HELD'),
    (2, 1, 1, 1, 0, NULL, '2026-10-01', '2026-10-01 11:00:00', '2026-10-01 12:00:00', 6000, 'BOOKED'),
    (3, 1, 1, 1, 0, NULL, '2026-10-01', '2026-10-01 12:00:00', '2026-10-01 13:00:00', 6000, 'OPEN');
INSERT INTO s_booking
    (id, booking_no, user_id, venue_id, court_id, sport_type_id, session_slot_id, source,
     contact_name, contact_phone, venue_name, court_name, sport_name, start_at, end_at,
     amount_fen, service_fee_rate_basis_points, status, payment_expires_at)
VALUES
    (1, 'UPGRADE-PENDING', 1, 1, 1, 1, 1, 'ONLINE', '用户', '00000000001', '场馆', '场地', '篮球',
     '2026-10-01 10:00:00', '2026-10-01 11:00:00', 6000, 525, 'PENDING_PAYMENT', '2026-10-01 09:55:00'),
    (2, 'UPGRADE-OFFLINE', NULL, 1, 1, 1, 2, 'OFFLINE', '线下', '00000000002', '场馆', '场地', '篮球',
     '2026-10-01 11:00:00', '2026-10-01 12:00:00', 6000, 0, 'CONFIRMED', NULL),
    (3, 'UPGRADE-REFUNDED', 1, 1, 1, 1, 3, 'ONLINE', '用户', '00000000001', '场馆', '场地', '篮球',
     '2026-10-01 12:00:00', '2026-10-01 13:00:00', 6000, 525, 'CANCELLED', NULL);
UPDATE s_session_slot SET hold_booking_id = id WHERE id IN (1, 2);
INSERT INTO s_payment (id, booking_id, provider, provider_no, amount_fen, status)
VALUES (1, 1, 'MOCK', NULL, 6000, 'PENDING'), (2, 3, 'MOCK', 'OLD-PAID', 6000, 'REFUNDED');
INSERT INTO s_refund (id, booking_id, payment_id, amount_fen, reason, status, refunded_at)
VALUES (1, 3, 2, 6000, '历史退款', 'SUCCESS', '2026-09-01 00:00:00');
SQL

apply_migration p2_upgrade "${migrations}/V5__add_venue_customer_and_offline_booking_operations.sql"
apply_migration p2_upgrade "${migrations}/V6__add_member_balance_and_online_payment.sql"

pending_row=$(mysql_exec -N -s -D p2_upgrade -e "SELECT CONCAT(payment_status,':',external_amount_fen) FROM s_booking WHERE id=1")
offline_row=$(mysql_exec -N -s -D p2_upgrade -e "SELECT CONCAT(balance_amount_fen,':',external_amount_fen) FROM s_booking WHERE id=2")
payment_row=$(mysql_exec -N -s -D p2_upgrade -e "SELECT status FROM s_payment WHERE id=2")
[[ "$pending_row" == "PAYING:6000" ]] || { print -u2 "待支付迁移断言失败: $pending_row"; exit 1; }
[[ "$offline_row" == "0:0" ]] || { print -u2 "线下组成迁移断言失败: $offline_row"; exit 1; }
[[ "$payment_row" == "SUCCESS" ]] || { print -u2 "历史退款支付状态迁移断言失败: $payment_row"; exit 1; }

mysql_exec p2_upgrade -e "INSERT INTO s_recharge_plan
    (id, venue_id, name, paid_amount_fen, gift_amount_fen, gift_valid_days, status)
    VALUES (1, 1, '有效档位', 10000, 1000, 30, 'ACTIVE');"
if mysql_exec p2_upgrade -e "INSERT INTO s_recharge_plan
    (venue_id, name, paid_amount_fen, gift_amount_fen, gift_valid_days, status)
    VALUES (1, '无效档位', 10000, 0, 30, 'ACTIVE');" >/dev/null 2>&1; then
  print -u2 "赠送有效期 CHECK 未生效"
  exit 1
fi
if mysql_exec p2_upgrade -e "UPDATE s_booking SET external_amount_fen=1 WHERE id=2" >/dev/null 2>&1; then
  print -u2 "线下预约支付组成 CHECK 未生效"
  exit 1
fi

print "P2 migrations: empty V1-V6, V5 upgrade backfill, and constraints passed"
