#!/usr/bin/env zsh
set -euo pipefail

script_dir=${0:A:h}
migrations=${script_dir:h}/sports-booking-server/src/main/resources/db/migration
container="sports-booking-p4-v9-$$"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true }
trap cleanup EXIT INT TERM

docker run --rm -d --name "$container" -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8.4 >/dev/null
for attempt in {1..40}; do
  docker exec "$container" mysqladmin ping -uroot --silent >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$container" mysqladmin ping -uroot --silent >/dev/null
mysql_exec() { docker exec -i "$container" mysql -uroot "$@"; }
apply_migration() { docker exec -i "$container" mysql -uroot "$1" < "$2"; }
must_reject() {
  if mysql_exec -D p4_v9 -e "$1" >/dev/null 2>&1; then
    print -u2 "V9 约束未拒绝: $2"
    exit 1
  fi
}

mysql_exec -e "CREATE DATABASE p4_v9;"
for version in {1..9}; do
  file=(${migrations}/V${version}__*.sql)
  apply_migration p4_v9 "$file"
done

mysql_exec -D p4_v9 <<'SQL'
INSERT INTO s_venue (id,name,address,phone,status) VALUES (1,'动态场馆','测试地址','00000000001','ACTIVE');
INSERT INTO s_user_account (id,mock_openid) VALUES (1,'p4-v9-user'),(2,'p4-v9-user-2');
INSERT INTO s_court (id,venue_id,name) VALUES (1,1,'1号场');
INSERT INTO s_session_slot (id,venue_id,court_id,sport_type_id,slot_date,start_at,end_at,price_fen,status)
VALUES (1,1,1,1,'2026-10-01','2026-10-01 10:00:00','2026-10-01 11:00:00',6000,'BOOKED');
INSERT INTO s_booking (id,booking_no,user_id,venue_id,court_id,sport_type_id,session_slot_id,source,
    contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,
    external_amount_fen,service_fee_rate_basis_points,status,payment_status)
VALUES (1,'P4-V9',1,1,1,1,1,'ONLINE','测试','00000000002','动态场馆','1号场','羽毛球',
    '2026-10-01 10:00:00','2026-10-01 11:00:00',6000,6000,0,'CONFIRMED','PAID');
INSERT INTO s_post (id,author_type,author_id,is_official,is_pinned,comments_enabled,content_status)
VALUES (1,'USER',1,FALSE,FALSE,TRUE,'PUBLISHED');
INSERT INTO s_post_revision (id,post_id,revision_no,body,sport_type_id,moderation_status)
VALUES (1,1,1,'第一次动态',1,'APPROVED');
UPDATE s_post SET current_revision_id=1, public_revision_id=1 WHERE id=1;
INSERT INTO s_post_like (post_id,user_id) VALUES (1,1);
INSERT INTO s_post_recruitment (id,post_id,booking_id,level,max_members,fee_mode,aa_amount_fen,status)
VALUES (1,1,1,'新手',3,'FREE',0,'RECRUITING');
INSERT INTO s_user_block (blocker_user_id,blocked_user_id) VALUES (1,2);
SQL

must_reject "INSERT INTO s_post_like (post_id,user_id) VALUES (1,1)" '动态点赞幂等键'
must_reject "INSERT INTO s_post_recruitment (post_id,booking_id,level,max_members,fee_mode,aa_amount_fen,status)
  VALUES (1,1,'新手',3,'FREE',0,'RECRUITING')" '一动态一招募'
must_reject "INSERT INTO s_user_block (blocker_user_id,blocked_user_id) VALUES (1,1)" '不能拉黑自己'
must_reject "INSERT INTO s_post_media
  (revision_id,position,media_type,storage_provider,object_key,mime_type,size_bytes,sha256)
  VALUES (1,1,'AUDIO','LOCAL','bad','audio/mpeg',100,
  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')" '媒体类型'

print 'P4 V9 migrations: post, comment, like, recruitment, and block constraints passed'
