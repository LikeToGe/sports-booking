#!/usr/bin/env zsh
set -euo pipefail

script_dir=${0:A:h}
backend_dir=${script_dir:h}
migrations=${backend_dir}/sports-booking-server/src/main/resources/db/migration
container="sports-booking-p3-migration-$$"

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

mysql_exec -e "CREATE DATABASE p3_empty; CREATE DATABASE p3_upgrade;"
for version in 1 2 3 4 5 6 7; do
  file=(${migrations}/V${version}__*.sql)
  apply_migration p3_empty "$file"
done

for version in 1 2 3 4 5 6; do
  file=(${migrations}/V${version}__*.sql)
  apply_migration p3_upgrade "$file"
done

mysql_exec p3_upgrade <<'SQL'
INSERT INTO s_venue (id, name, address, phone, status)
VALUES (1, '一号场馆', '测试地址', '00000000001', 'ACTIVE'),
       (2, '二号场馆', '测试地址', '00000000002', 'ACTIVE');
INSERT INTO s_admin_account (id, username, password_hash, role, venue_id, display_name, status)
VALUES (1, 'venue-admin', 'hash', 'VENUE_ADMIN', 1, '管理员', 'ACTIVE');
INSERT INTO s_user_account (id, mock_openid, contact_name, phone)
VALUES (1, 'migration-user', '迁移用户', '00000000003');
INSERT INTO s_employee (id, venue_id, user_id, name, status)
VALUES (1, 1, 1, '核销员', 'ACTIVE');
SQL

apply_migration p3_upgrade "${migrations}/V7__add_venue_roles_and_employee_audit.sql"

seed_permission=$(mysql_exec -N -s -D p3_upgrade -e "SELECT CONCAT(r.name, ':', rp.permission_code)
  FROM s_venue_role r JOIN s_venue_role_permission rp ON rp.role_id=r.id
  JOIN s_employee_role er ON er.role_id=r.id WHERE er.employee_id=1")
[[ "$seed_permission" == "核销员工:BOOKING_VERIFY" ]] || {
  print -u2 "历史员工默认核销权限迁移失败: $seed_permission"
  exit 1
}

# 同一用户允许成为不同场馆员工，同场馆仍由唯一约束拒绝重复绑定。
mysql_exec p3_upgrade -e "INSERT INTO s_employee (venue_id,user_id,name,status) VALUES (2,1,'跨馆员工','ACTIVE');"
if mysql_exec p3_upgrade -e "INSERT INTO s_employee (venue_id,user_id,name,status)
    VALUES (1,1,'重复员工','ACTIVE');" >/dev/null 2>&1; then
  print -u2 "员工场馆用户唯一约束未生效"
  exit 1
fi

mysql_exec p3_upgrade <<'SQL'
INSERT INTO s_court (id, venue_id, name) VALUES (1, 1, '1号场');
INSERT INTO s_session_slot
    (id, venue_id, court_id, sport_type_id, manual_override, slot_date, start_at, end_at, price_fen, status)
VALUES (1, 1, 1, 1, 0, '2026-10-01', '2026-10-01 10:00:00', '2026-10-01 11:00:00', 6000, 'BOOKED'),
       (2, 1, 1, 1, 0, '2026-10-01', '2026-10-01 11:00:00', '2026-10-01 12:00:00', 6000, 'OPEN');
INSERT INTO s_booking
    (id, booking_no, venue_id, court_id, sport_type_id, session_slot_id, source,
     contact_name, contact_phone, venue_name, court_name, sport_name, start_at, end_at,
     amount_fen, external_amount_fen, service_fee_rate_basis_points, status, payment_status)
VALUES (1, 'P3-OFFLINE', 1, 1, 1, 1, 'OFFLINE', '线下', '00000000004', '场馆', '场地', '篮球',
        '2026-10-01 10:00:00', '2026-10-01 11:00:00', 6000, 0, 0, 'CONFIRMED', 'PAID');
INSERT INTO s_booking_payment_status_log
    (booking_id, before_status, after_status, operator_admin_id, reason)
VALUES (1, 'UNPAID', 'PAID', 1, '管理员登记');
INSERT INTO s_booking_payment_status_log
    (booking_id, before_status, after_status, operator_employee_id, reason)
VALUES (1, 'PAID', 'REFUNDED', 1, '员工登记');
INSERT INTO s_booking_reschedule_log
    (booking_id, original_session_slot_id, new_session_slot_id, operator_employee_id, reason)
VALUES (1, 1, 2, 1, '员工改期');
SQL

if mysql_exec p3_upgrade -e "INSERT INTO s_booking_payment_status_log
    (booking_id,before_status,after_status,reason) VALUES (1,'PAID','REFUNDED','无操作者');" >/dev/null 2>&1; then
  print -u2 "预约审计恰一操作者 CHECK 未拒绝全空"
  exit 1
fi
if mysql_exec p3_upgrade -e "INSERT INTO s_booking_reschedule_log
    (booking_id,original_session_slot_id,new_session_slot_id,reason)
    VALUES (1,1,2,'无操作者');" >/dev/null 2>&1; then
  print -u2 "改期审计恰一操作者 CHECK 未拒绝全空"
  exit 1
fi
if mysql_exec p3_upgrade -e "INSERT INTO s_booking_reschedule_log
    (booking_id,original_session_slot_id,new_session_slot_id,operator_admin_id,operator_employee_id,reason)
    VALUES (1,1,2,1,1,'双操作者');" >/dev/null 2>&1; then
  print -u2 "改期审计恰一操作者 CHECK 未拒绝双填"
  exit 1
fi
if mysql_exec p3_upgrade -e "INSERT INTO s_booking_payment_status_log
    (booking_id,before_status,after_status,operator_admin_id,operator_employee_id,reason)
    VALUES (1,'PAID','REFUNDED',1,1,'双操作者');" >/dev/null 2>&1; then
  print -u2 "预约审计恰一操作者 CHECK 未拒绝双填"
  exit 1
fi

# 自助充值允许管理员与员工操作者同时为空。
mysql_exec p3_upgrade <<'SQL'
INSERT INTO s_venue_customer (id,venue_id,user_id,status,member_code)
VALUES (1,1,1,'CUSTOMER','MEMBER000001');
INSERT INTO s_recharge_plan
    (id,venue_id,name,paid_amount_fen,gift_amount_fen,gift_valid_days,status)
VALUES (1,1,'自助档位',10000,0,NULL,'ACTIVE');
INSERT INTO s_member_recharge
    (id,recharge_no,venue_customer_id,plan_id,plan_name,paid_amount_fen,gift_amount_fen,
     gift_valid_days,channel,status)
VALUES (1,'RCH-P3-SELF',1,1,'自助档位',10000,0,NULL,'MOCK','PENDING');
SQL

print "P3 migrations: empty V1-V7, V6 upgrade, default verify role, and operator constraints passed"
