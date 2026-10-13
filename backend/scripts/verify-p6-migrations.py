"""在同一隔离容器的 V15 历史库追加 P6 DDL，验证历史消息及预约保留。"""
import os
from pathlib import Path
import subprocess

container = os.environ['P5_MYSQL_CONTAINER']

def sql(statement):
    result = subprocess.run(['docker', 'exec', '-i', container, 'mysql', '-uroot', '-preview_root',
                             '--default-character-set=utf8mb4', '-Ns', 'p5_upgrade'],
                            input=statement, text=True, capture_output=True, check=True)
    return result.stdout.strip()

sql("""INSERT INTO s_user_account(id,mock_openid,contact_name) VALUES(991,'p6-upgrade-mock','历史测试用户');
INSERT INTO s_notification(id,user_id,booking_id,type,status,business_type,business_id,title,content,idempotency_key)
VALUES(991,991,1,'BOOKING_CREATED','MOCK_SENT','BOOKING',1,'历史预约消息','历史正文','p6-upgrade-old');
INSERT INTO s_notification_delivery(notification_id,channel,status,provider_no)
VALUES(991,'MOCK','SUCCESS','p6-upgrade-mock-provider');
""")
before = sql("SELECT CONCAT(amount_fen,':',venue_name,':',status,':',payment_status) FROM s_booking WHERE id=1")
migrations = Path(__file__).resolve().parents[1] / 'sports-booking-server/src/main/resources/db/migration'
for path in sorted(migrations.glob('V*__*.sql'), key=lambda p: int(p.name.split('__')[0][1:])):
    if int(path.name.split('__')[0][1:]) > 15:
        sql(path.read_text())
assert sql("SELECT CONCAT(amount_fen,':',venue_name,':',status,':',payment_status) FROM s_booking WHERE id=1") == before
assert sql("SELECT CONCAT(title,':',content,':',status,':',idempotency_key) FROM s_notification WHERE id=991") == '历史预约消息:历史正文:MOCK_SENT:p6-upgrade-old'
assert sql("SELECT CONCAT(status,':',provider_no,':',attempt_count) FROM s_notification_delivery WHERE notification_id=991") == 'SUCCESS:p6-upgrade-mock-provider:0'
assert sql('SELECT COUNT(*) FROM s_reminder_subscription') == '0'
assert sql('SELECT COUNT(*) FROM s_venue_customer_followup') == '0'
print('P6 SQL upgrade from V15 preserves legacy bookings, messages and delivery results')
