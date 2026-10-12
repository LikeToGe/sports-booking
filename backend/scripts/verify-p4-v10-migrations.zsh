#!/usr/bin/env zsh
set -euo pipefail

script_dir=${0:A:h}
migrations=${script_dir:h}/sports-booking-server/src/main/resources/db/migration
container="sports-booking-p4-v10-$$"
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

mysql_exec -e "CREATE DATABASE p4_v10;"
for version in {1..10}; do
  file=(${migrations}/V${version}__*.sql)
  apply_migration p4_v10 "$file"
done

mysql_exec -D p4_v10 -e "SELECT id, post_enabled, comment_enabled, review_enabled FROM s_content_moderation_setting WHERE id=1" \
  | grep -q '1.*1.*1.*1'
mysql_exec -D p4_v10 -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='p4_v10' AND table_name IN ('s_post','s_post_media','s_venue_review_media') AND column_name IN ('purge_after','legal_hold')" \
  | grep -q '^6$'
mysql_exec -D p4_v10 -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='p4_v10' AND table_name IN ('s_content_agreement_setting','s_content_agreement_acceptance','s_content_report','s_content_appeal','s_user_content_restriction','s_user_action_event','s_user_identity_tombstone')" \
  | grep -q '^7$'
mysql_exec -D p4_v10 -e "INSERT INTO s_user_account (mock_openid) VALUES ('p4-v10-test');
  INSERT INTO s_admin_account (role,username,password_hash,display_name) VALUES ('PLATFORM_ADMIN','p4-v10-admin','test','测试管理员');
  INSERT INTO s_content_report (reporter_user_id,target_type,target_id,reason_code) VALUES (1,'POST',1,'SPAM');"
if mysql_exec -D p4_v10 -e "INSERT INTO s_content_report (reporter_user_id,target_type,target_id,reason_code) VALUES (1,'POST',1,'SPAM');" >/dev/null 2>&1; then
  print -u2 '同一用户对同一目标出现两条处理中举报'
  exit 1
fi
mysql_exec -D p4_v10 -e "UPDATE s_content_report SET status='RESOLVED' WHERE id=1;
  INSERT INTO s_content_report (reporter_user_id,target_type,target_id,reason_code) VALUES (1,'POST',1,'SPAM');
  INSERT INTO s_content_moderation_log (content_type,content_id,action,reason,operator_admin_id)
  VALUES ('POST',1,'HIDE','测试',1);
  INSERT INTO s_content_appeal (moderation_log_id,appellant_user_id,body) VALUES (1,1,'申诉测试');"
if mysql_exec -D p4_v10 -e "INSERT INTO s_content_appeal (moderation_log_id,appellant_user_id,body) VALUES (1,1,'重复申诉');" >/dev/null 2>&1; then
  print -u2 '同一处置出现两次申诉'
  exit 1
fi
print 'P4 V10 migrations: moderation settings, governance tables, retention facts, and unique constraints passed'
