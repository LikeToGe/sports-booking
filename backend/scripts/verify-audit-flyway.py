"""用当前应用JAR内真实Flyway验证空库及V15历史库升级；仅操作随机专属隔离库。"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import uuid
from zipfile import ZipFile

CONTAINER = os.environ['P5_MYSQL_CONTAINER']
assert CONTAINER == 'sports-booking-p5-time-search-runtime-mysql-30691', '仅允许本次隔离验收容器'
BACKEND = Path(__file__).resolve().parents[1]
JAR = BACKEND / 'sports-booking-server/target/sports-booking-server.jar'
PASSWORD = 'review_root'  # 专用本地Mock容器，不使用生产凭证。
MYSQL = ['docker', 'exec', '-i', CONTAINER, 'mysql', '-uroot', '-p' + PASSWORD,
         '--default-character-set=utf8mb4', '-Ns']
PREFIX = 'audit_flyway_' + uuid.uuid4().hex[:12]
DATABASES = [PREFIX + '_empty', PREFIX + '_legacy']
created = []


def sql(statement, database=None):
    result = subprocess.run(MYSQL + ([] if database is None else [database]), input=statement,
                            text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


def history(database, target):
    return sql('SELECT installed_rank,version,description,type,script,checksum,installed_by,success '
               f'FROM flyway_schema_history WHERE CAST(version AS UNSIGNED)<={target} '
               'ORDER BY installed_rank', database)


JAVA_SOURCE = '''
import java.nio.charset.StandardCharsets;
import org.flywaydb.core.Flyway;

public class FlywayProbe {
    public static void main(String[] args) throws Exception {
        String password = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        // 升级前允许尚未应用的新迁移；已应用迁移的缺失、失败及校验和仍严格校验。
        Flyway before = Flyway.configure().dataSource(args[0], "root", password)
                .locations("filesystem:" + args[1]).target(args[2])
                .ignoreMigrationPatterns("*:pending").load();
        before.validate();
        Flyway flyway = Flyway.configure().dataSource(args[0], "root", password)
                .locations("filesystem:" + args[1]).target(args[2]).load();
        var result = flyway.migrate();
        if (!result.success) throw new IllegalStateException("migrate failed");
        flyway.validate();
        System.out.println("FLYWAY_PROBE target=" + args[2] + " applied="
                + result.migrationsExecuted + " current=" + flyway.info().current().getVersion());
    }
}
'''

FIXTURE = '''
INSERT INTO s_venue(id,name,address,phone,status,service_fee_rate_basis_points)
VALUES(991,'历史本地Mock场馆','LOCAL_MOCK_ADDRESS','MOCK_PHONE','ACTIVE',525);
INSERT INTO s_user_account(id,mock_openid,contact_name,phone,nickname)
VALUES(991,'audit-flyway-mock','历史本地Mock用户','MOCK_PHONE','历史球友');
INSERT INTO s_court(id,venue_id,name) VALUES(991,991,'历史本地Mock场地');
INSERT INTO s_court_sport(court_id,sport_type_id) VALUES(991,1);
INSERT INTO s_venue_customer(id,venue_id,user_id,status,member_code,activated_at)
VALUES(991,991,991,'MEMBER','MOCKV15','2026-09-01 10:00:00');
INSERT INTO s_member_recharge(id,recharge_no,venue_customer_id,plan_name,paid_amount_fen,
    gift_amount_fen,gift_valid_days,channel,status,succeeded_at)
VALUES(991,'MOCK-RECHARGE-15',991,'历史充值档位',5000,1000,60,'ONLINE','SUCCESS','2026-09-01 10:00:00');
INSERT INTO s_member_balance_lot(id,venue_customer_id,source_recharge_id,type,original_amount_fen,
    available_amount_fen,frozen_amount_fen,status,expires_at)
VALUES(991,991,991,'PAID',5000,4300,200,'ACTIVE',NULL),
      (992,991,991,'GIFT',1000,900,0,'ACTIVE','2026-11-01 23:59:59');
INSERT INTO s_session_slot(id,venue_id,court_id,sport_type_id,manual_override,slot_date,start_at,
    end_at,price_fen,status)
VALUES(991,991,991,1,1,'2026-10-10','2026-10-10 11:00:00','2026-10-10 12:00:00',3000,'BOOKED'),
      (992,991,991,1,1,'2026-10-20','2026-10-20 11:00:00','2026-10-20 12:00:00',1000,'HELD');
INSERT INTO s_booking(id,booking_no,user_id,venue_customer_id,venue_id,court_id,sport_type_id,
    session_slot_id,source,contact_name,contact_phone,venue_name,court_name,sport_name,start_at,
    end_at,amount_fen,balance_amount_fen,paid_balance_amount_fen,gift_balance_amount_fen,
    external_amount_fen,service_fee_rate_basis_points,status,payment_status,payment_expires_at,completed_at)
VALUES(991,'MOCK-BOOKING-15',991,991,991,991,1,991,'ONLINE','本地Mock','MOCK_PHONE','历史场馆快照',
    '历史场地快照','历史运动快照','2026-10-10 11:00:00','2026-10-10 12:00:00',3000,600,500,100,
    2400,525,'COMPLETED','PAID',NULL,'2026-10-10 11:10:00'),
    (992,'MOCK-PENDING-15',991,991,991,991,1,992,'ONLINE','本地Mock','MOCK_PHONE','历史场馆快照',
    '历史场地快照','历史运动快照','2026-10-20 11:00:00','2026-10-20 12:00:00',1000,200,200,0,
    800,525,'PENDING_PAYMENT','PAYING','2026-10-20 10:00:00',NULL);
UPDATE s_session_slot SET hold_booking_id=id,
    hold_expires_at=CASE WHEN id=992 THEN '2026-10-20 10:00:00' ELSE NULL END WHERE id IN (991,992);
INSERT INTO s_payment(id,booking_id,payment_no,provider,provider_no,amount_fen,status,paid_at)
VALUES(991,991,'MOCK-PAYMENT-15','MOCK','MOCK-SUCCESS-15',2400,'SUCCESS','2026-10-08 09:12:13'),
      (992,992,'MOCK-PENDING-PAY-15','MOCK',NULL,800,'PENDING',NULL);
INSERT INTO s_booking_balance_hold(booking_id,lot_id,amount_fen,status,frozen_at,consumed_at)
VALUES(991,991,500,'CONSUMED','2026-10-08 09:00:00','2026-10-08 09:12:13'),
      (991,992,100,'CONSUMED','2026-10-08 09:00:00','2026-10-08 09:12:13'),
      (992,991,200,'FROZEN','2026-10-13 09:00:00',NULL);
INSERT INTO s_member_balance_flow(venue_customer_id,lot_id,direction,amount_fen,business_type,
    booking_id,recharge_id,idempotency_key)
VALUES(991,991,'IN',5000,'RECHARGE',NULL,991,'MOCK-FLOW-PAID-IN'),
      (991,992,'IN',1000,'RECHARGE',NULL,991,'MOCK-FLOW-GIFT-IN'),
      (991,991,'OUT',500,'BOOKING_CONSUME',991,NULL,'MOCK-FLOW-PAID-OUT'),
      (991,992,'OUT',100,'BOOKING_CONSUME',991,NULL,'MOCK-FLOW-GIFT-OUT');
INSERT INTO s_notification(id,user_id,booking_id,type,status,business_type,business_id,title,
    content,idempotency_key,read_at)
VALUES(991,991,991,'BOOKING_CONFIRMED','MOCK_SENT','BOOKING',991,'历史预约消息','历史消息正文',
    'MOCK-NOTIFICATION-15','2026-10-08 09:13:00');
INSERT INTO s_notification_delivery(notification_id,channel,status,provider_no)
VALUES(991,'MOCK','SUCCESS','MOCK-DELIVERY-15');
INSERT INTO s_post(id,author_type,author_id,content_status) VALUES(991,'USER',991,'PUBLISHED');
INSERT INTO s_post_revision(id,post_id,revision_no,body,sport_type_id,moderation_status)
VALUES(991,991,1,'历史公开原文',1,'APPROVED');
UPDATE s_post SET current_revision_id=991,public_revision_id=991 WHERE id=991;
INSERT INTO s_content_report(id,reporter_user_id,target_type,target_id,reason_code,description)
VALUES(991,991,'POST',991,'OTHER','历史举报，没有提交时版本证据');
'''

TABLES = ['s_venue', 's_user_account', 's_court', 's_venue_customer', 's_member_recharge',
          's_member_balance_lot', 's_member_balance_flow', 's_booking_balance_hold', 's_session_slot',
          's_booking', 's_payment', 's_notification', 's_notification_delivery', 's_post',
          's_post_revision', 's_content_report']

try:
    # 复用项目已指定的 JDK，而不是机器默认 Java 8；不安装或调整依赖。
    java_home = Path(re.search(r'^export JAVA_HOME=(.+)$', (BACKEND / 'mvn-project').read_text(), re.M).group(1))
    port_output = subprocess.run(['docker', 'port', CONTAINER, '3306/tcp'],
                                 text=True, capture_output=True, check=True).stdout
    port = int(port_output.splitlines()[0].rsplit(':', 1)[1])
    with tempfile.TemporaryDirectory(prefix='sports-booking-flyway-', dir='/tmp') as directory:
        temporary = Path(directory)
        libraries = temporary / 'lib'
        migrations = temporary / 'migrations'
        libraries.mkdir()
        migrations.mkdir()
        with ZipFile(JAR) as archive:
            for name in archive.namelist():
                if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                    (libraries / Path(name).name).write_bytes(archive.read(name))
                elif name.startswith('BOOT-INF/classes/db/migration/') and name.endswith('.sql'):
                    assert archive.read(name) == (BACKEND / 'sports-booking-server/src/main/resources/db/migration'
                                                  / Path(name).name).read_bytes(), 'JAR内迁移与当前源码不一致'
                    (migrations / Path(name).name).write_bytes(archive.read(name))
        versions = sorted(int(path.name.split('__')[0][1:]) for path in migrations.glob('V*__*.sql'))
        assert versions[-1] == 24 and versions[:15] == list(range(1, 16)), versions
        source = temporary / 'FlywayProbe.java'
        source.write_text(JAVA_SOURCE)
        compile_result = subprocess.run([str(java_home / 'bin/javac'), '-cp', str(libraries / '*'), str(source)],
                                        text=True, capture_output=True)
        assert compile_result.returncode == 0, compile_result.stderr

        def migrate(database, target):
            url = (f'jdbc:mysql://127.0.0.1:{port}/{database}?allowPublicKeyRetrieval=true'
                   '&useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8')
            result = subprocess.run([str(java_home / 'bin/java'), '-Djava.awt.headless=true', '-cp',
                                     str(temporary) + os.pathsep + str(libraries / '*'), 'FlywayProbe',
                                     url, str(migrations), str(target)], input=PASSWORD,
                                    text=True, capture_output=True)
            if result.returncode:
                raise RuntimeError(result.stdout + result.stderr)
            if 'MySQL 8.4 is newer' in result.stdout:
                print('WARN existing Flyway declares MySQL 8.4 newer than its tested range; no dependency upgrade performed')
            marker = next(line for line in result.stdout.splitlines() if line.startswith('FLYWAY_PROBE '))
            assert 'current=' + str(target) in marker, marker
            print(marker)
            return int(re.search(r'applied=(\d+)', marker).group(1))

        for database in DATABASES:
            assert re.fullmatch(r'audit_flyway_[a-f0-9]{12}_(empty|legacy)', database)
            sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
            created.append(database)
        empty, legacy = DATABASES
        assert migrate(empty, 24) == len(versions)
        assert sql('SELECT COUNT(*),SUM(success),SUM(checksum IS NOT NULL) FROM flyway_schema_history', empty) == f'{len(versions)}\t{len(versions)}\t{len(versions)}'
        assert migrate(legacy, 15) == 15
        sql(FIXTURE, legacy)
        original_history = history(legacy, 15)
        columns = {table: [line.split('\t')[0] for line in sql(f'SHOW COLUMNS FROM {table}', legacy).splitlines()]
                   for table in TABLES}
        queries = {table: 'SELECT JSON_ARRAY(' + ','.join('`' + column + '`' for column in fields)
                   + f') FROM `{table}` ORDER BY id' for table, fields in columns.items()}
        before = {table: sql(query, legacy) for table, query in queries.items()}
        assert migrate(legacy, 24) == len(versions) - 15
        assert history(legacy, 15) == original_history, '已应用迁移的校验和或成功记录被改写'
        after = {table: sql(query, legacy) for table, query in queries.items()}
        assert before == after, [table for table in TABLES if before[table] != after[table]]
        assert sql('SELECT evidence_revision_id IS NULL FROM s_content_report WHERE id=991', legacy) == '1'
        assert sql('SELECT COUNT(*),SUM(success),SUM(checksum IS NOT NULL) FROM flyway_schema_history', legacy) == f'{len(versions)}\t{len(versions)}\t{len(versions)}'
        assert migrate(legacy, 24) == 0
        digest = hashlib.sha256(json.dumps(before, ensure_ascii=False, sort_keys=True).encode()).hexdigest()
        print(f'PASS empty V1-V24: {len(versions)} real SQL migrations; repository has no V16/V17')
        print('PASS real Flyway V15 validate/migrate to V24 and idempotent replay; old history checksums preserved')
        print(f'PASS all original fields across {len(TABLES)} legacy tables preserved; snapshot SHA256={digest}')
        print('PASS original paid_at, paid/gift/frozen balances, booking snapshots, messages/delivery and report content preserved')
        print('PASS old report evidence_revision_id remains NULL; no current-version evidence fabricated')
finally:
    for database in reversed(created):
        # 从本次随机名称列表取库名，不接收任意待清理数据库参数。
        assert database in DATABASES and database.startswith(PREFIX + '_')
        sql(f'DROP DATABASE `{database}`')
    if created:
        assert sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE '" + PREFIX + "%'") == '0'
        print('PASS dedicated databases and temporary Java helper removed; primary sports_booking untouched')
