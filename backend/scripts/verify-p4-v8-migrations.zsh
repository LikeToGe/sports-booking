#!/usr/bin/env zsh
set -euo pipefail

script_dir=${0:A:h}
migrations=${script_dir:h}/sports-booking-server/src/main/resources/db/migration
container="sports-booking-p4-v8-$$"

cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true }
trap cleanup EXIT INT TERM

docker run --rm -d --name "$container" -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8.4 >/dev/null
for attempt in {1..40}; do
  docker exec "$container" mysqladmin ping -uroot --silent >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$container" mysqladmin ping -uroot --silent >/dev/null

mysql_exec() { docker exec -i "$container" mysql -uroot "$@" }
apply_migration() { docker exec -i "$container" mysql -uroot "$1" < "$2" }
must_reject() {
  if mysql_exec -D p4_upgrade -e "$1" >/dev/null 2>&1; then
    print -u2 "V8 约束未拒绝: $2"
    exit 1
  fi
}

mysql_exec -e "CREATE DATABASE p4_empty; CREATE DATABASE p4_upgrade;"
for version in {1..8}; do
  file=(${migrations}/V${version}__*.sql)
  apply_migration p4_empty "$file"
done
for version in {1..7}; do
  file=(${migrations}/V${version}__*.sql)
  apply_migration p4_upgrade "$file"
done
apply_migration p4_upgrade "${migrations}/V8__add_venue_reviews.sql"

mysql_exec -D p4_upgrade <<'SQL'
INSERT INTO s_venue (id,name,address,phone,status) VALUES (1,'迁移场馆','测试地址','00000000001','ACTIVE');
INSERT INTO s_user_account (id,mock_openid) VALUES (1,'p4-review-user');
INSERT INTO s_court (id,venue_id,name) VALUES (1,1,'1号场');
INSERT INTO s_session_slot (id,venue_id,court_id,sport_type_id,slot_date,start_at,end_at,price_fen,status)
VALUES (1,1,1,1,'2026-10-01','2026-10-01 10:00:00','2026-10-01 11:00:00',6000,'BOOKED');
INSERT INTO s_booking (id,booking_no,user_id,venue_id,court_id,sport_type_id,session_slot_id,source,
    contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,
    external_amount_fen,service_fee_rate_basis_points,status,payment_status)
VALUES (1,'P4-REVIEW',1,1,1,1,1,'ONLINE','测试','00000000002','迁移场馆','1号场','羽毛球',
    '2026-10-01 10:00:00','2026-10-01 11:00:00',6000,6000,0,'COMPLETED','PAID');
INSERT INTO s_venue_review (id,booking_id,venue_id,user_id,sport_type_id) VALUES (1,1,1,1,1);
INSERT INTO s_venue_review_revision
    (id,review_id,revision_no,body,overall_score,court_score,environment_score,service_score)
VALUES (1,1,1,'场地整洁',5,5,4,5);
UPDATE s_venue_review SET current_revision_id=1 WHERE id=1;
INSERT INTO s_review_tag (id,name,normalized_name) VALUES
    (1,'地胶良好','地胶良好'),(2,'灯光明亮','灯光明亮'),(3,'服务周到','服务周到'),(4,'交通便利','交通便利');
INSERT INTO s_venue_review_tag_rel (revision_id,tag_id,position) VALUES (1,1,1),(1,2,2),(1,3,3);
INSERT INTO s_venue_review_media
    (revision_id,position,storage_provider,object_key,mime_type,size_bytes,sha256)
VALUES (1,1,'LOCAL','review-1.jpg','image/jpeg',100,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');
INSERT INTO s_venue_review_reply (id,review_id,venue_id) VALUES (1,1,1);
SQL

must_reject "INSERT INTO s_venue_review (booking_id,venue_id,user_id,sport_type_id)
  VALUES (1,1,1,1)" '一预约一评价'
must_reject "INSERT INTO s_venue_review_revision
  (review_id,revision_no,body,overall_score,court_score,environment_score,service_score)
  VALUES (1,2,'无效评分',6,5,5,5)" '评分范围'
must_reject "INSERT INTO s_venue_review_tag_rel (revision_id,tag_id,position)
  VALUES (1,4,4)" '最多三个标签'
must_reject "INSERT INTO s_venue_review_media
  (revision_id,position,storage_provider,object_key,mime_type,size_bytes,sha256)
  VALUES (1,4,'LOCAL','review-4.jpg','image/jpeg',100,
  'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb')" '最多三张图片'
must_reject "INSERT INTO s_venue_review_reply (review_id,venue_id) VALUES (1,1)" '单条官方回复'

print 'P4 V8 migrations: empty V1-V8, V7 upgrade, and review constraints passed'
