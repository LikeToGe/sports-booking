"""仅在隔离验收库验证有效快照、跨场次ID并发及周模板已占用日期保护。"""
import concurrent.futures
import datetime as dt
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from zoneinfo import ZoneInfo

BASE = os.environ.get('P5_API_BASE', 'http://127.0.0.1:8091/api')
CONTAINER = os.environ['P5_MYSQL_CONTAINER']
assert 'p5-' in CONTAINER or 'p6-' in CONTAINER, '仅允许隔离验收容器'
PREFIX = 'F03-' + uuid.uuid4().hex[:12]
ZONE = ZoneInfo('Asia/Shanghai')
DAY = dt.datetime.now(ZONE).date() + dt.timedelta(days=1)
MYSQL = ['docker', 'exec', '-i', CONTAINER, 'mysql', '-uroot', '-preview_root',
         '--default-character-set=utf8mb4', '--unbuffered', '-Ns', 'sports_booking']


def sql(statement):
    return subprocess.run(MYSQL, input=statement, text=True, capture_output=True, check=True).stdout.strip()


def utc(day, clock):
    return dt.datetime.combine(day, dt.time.fromisoformat(clock), ZONE).astimezone(
        dt.timezone.utc).strftime('%Y-%m-%d %H:%M:%S')


def http(path, body=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if admin:
        headers['X-Admin-Token'] = admin
    request = urllib.request.Request(BASE + path,
                                    data=None if body is None else json.dumps(body).encode(),
                                    headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.load(response)


def ok(path, **kwargs):
    status, result = http(path, **kwargs)
    assert status == 200 and result['code'] == 0, (status, result.get('message'))
    return result['data']


def conflict(path, **kwargs):
    status, result = http(path, **kwargs)
    assert status == 409 and result['code'] != 0, (status, result.get('message'))


def slot_body(start, end, day=DAY):
    return {'courtId': court, 'sportTypeId': sport, 'date': str(day),
            'startTime': start, 'endTime': end, 'priceFen': 1000}


def legacy_slot(start, end, status='OPEN', day=DAY):
    return int(sql(f"INSERT INTO s_session_slot(venue_id,court_id,sport_type_id,manual_override,"
                   f"slot_date,start_at,end_at,price_fen,status) VALUES(1,{court},{sport},1,'{day}',"
                   f"'{utc(day,start)}','{utc(day,end)}',1000,'{status}'); SELECT LAST_INSERT_ID();"))


def offline(slot):
    return {'sessionSlotId': slot, 'contactName': PREFIX, 'contactPhone': 'MOCK_PHONE', 'paymentStatus': 'PAID'}


admin = None
court = None
blocker = None
try:
    admin = ok('/auth/admin/login', body={'username': 'venue1', 'password': 'venue123'})['token']
    sport = int(sql("SELECT id FROM s_sport_type WHERE owner_venue_id IS NULL AND status='ACTIVE' ORDER BY id LIMIT 1"))
    court = int(sql(f"INSERT INTO s_court(venue_id,name,status) VALUES(1,'{PREFIX}','ACTIVE'); SELECT LAST_INSERT_ID();"))
    sql(f'INSERT INTO s_court_sport(court_id,sport_type_id) VALUES({court},{sport});')
    first_slot = ok('/venue-admin/sessions/create', body=slot_body('19:00', '20:00'))['id']
    original = ok('/venue-admin/bookings/offline', body=offline(first_slot))['id']
    ok(f'/venue-admin/sessions/{first_slot}', body=slot_body('21:00', '22:00'), method='PUT')
    assert sql(f"SELECT DATE_FORMAT(CONVERT_TZ(start_at,'+00:00','+08:00'),'%H:%i') FROM s_booking WHERE id={original}") == '19:00'
    conflict('/venue-admin/sessions/create', body=slot_body('19:00', '20:00'))
    duplicate = legacy_slot('19:00', '20:00')
    conflict('/venue-admin/bookings/offline', body=offline(duplicate))
    ok(f'/venue-admin/sessions/{duplicate}/close', method='POST')
    conflict(f'/venue-admin/sessions/{duplicate}/open', method='POST')
    # 原预约改期只排除自身快照，允许迁移到与原时段相交的另一开放场次。
    target = legacy_slot('19:30', '20:30')
    ok(f'/venue-admin/bookings/{original}/reschedule', body={'sessionSlotId': target, 'reason': '本地Mock同价改期'})
    ok('/venue-admin/sessions/create', body=slot_body('19:00', '19:30'))  # 半开区间相邻可用。
    assert sql(f'SELECT hold_booking_id IS NULL,hold_expires_at IS NULL FROM s_session_slot WHERE id={first_slot}') == '1\t1'

    # 历史遗留两个不同库存 ID 的重叠供给，场地行锁与快照当前读仍只允许一笔新预约。
    overlapping_ids = [legacy_slot('15:00', '16:00'), legacy_slot('15:30', '16:30')]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda item: http('/venue-admin/bookings/offline', body=offline(item)), overlapping_ids))
    assert sum(status == 200 and result['code'] == 0 for status, result in results) == 1, [
        (status, result['code']) for status, result in results]
    assert sql(f'SELECT COUNT(*) FROM s_booking WHERE court_id={court} AND session_slot_id IN ({overlapping_ids[0]},{overlapping_ids[1]})') == '1'

    weekly_body = slot_body('22:00', '23:00')
    weekly_body.pop('date')
    weekly_body['dayOfWeek'] = DAY.isoweekday()
    weekly = ok('/venue-admin/weekly-slots/create', body=weekly_body)['id']
    occurrence = int(sql(f"SELECT id FROM s_session_slot WHERE weekly_slot_id={weekly} AND template_slot_date='{DAY}'"))
    ok('/venue-admin/bookings/offline', body=offline(occurrence))
    weekly_body.update(startTime='22:30', endTime='23:30')
    ok(f'/venue-admin/weekly-slots/{weekly}/update', body=weekly_body, method='PUT')
    ok('/venue-admin/sessions/list?date=' + str(DAY))
    assert sql(f"SELECT COUNT(*) FROM s_session_slot WHERE weekly_slot_id={weekly} AND template_slot_date='{DAY}'") == '1'
    assert sql(f"SELECT DATE_FORMAT(CONVERT_TZ(start_at,'+00:00','+08:00'),'%H:%i'),status FROM s_session_slot WHERE id={occurrence}") == '22:00\tBOOKED'

    # 建立旧 RR 视图后等待场地锁：提交的新预约快照必须由 locking read 看见。
    future = DAY + dt.timedelta(days=2)
    shifted = legacy_slot('13:00', '14:00', status='BOOKED', day=future)
    statement = (f'START TRANSACTION; SELECT id FROM s_court WHERE id={court} FOR UPDATE; SELECT SLEEP(2); '
                 f"INSERT INTO s_booking(booking_no,venue_id,court_id,sport_type_id,session_slot_id,source,contact_name,"
                 f"contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,status,payment_status) "
                 f"VALUES('{PREFIX}-RR',1,{court},{sport},{shifted},'OFFLINE','{PREFIX}','MOCK_PHONE','本地Mock',"
                 f"'本地Mock','测试运动','{utc(future,'11:00')}','{utc(future,'12:00')}',1000,'CONFIRMED','PAID'); COMMIT;")
    blocker = subprocess.Popen(MYSQL, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    blocker.stdin.write(statement)
    blocker.stdin.close()
    blocker.stdin = None
    assert blocker.stdout.readline().strip() == str(court), '未取得专用场地行锁'
    started = time.monotonic()
    conflict('/venue-admin/sessions/create', body=slot_body('11:00', '12:00', future))
    assert time.monotonic() - started >= 1, '未覆盖等待场地锁期间的旧查询视图'
    assert blocker.wait(timeout=10) == 0
finally:
    if blocker is not None and blocker.poll() is None:
        blocker.wait(timeout=10)
    if court is not None:
        sql(f'UPDATE s_session_slot SET hold_booking_id=NULL,hold_expires_at=NULL WHERE court_id={court}; '
            f'DELETE FROM s_booking_reschedule_log WHERE booking_id IN (SELECT id FROM s_booking WHERE court_id={court}); '
            f'DELETE FROM s_booking WHERE court_id={court}; '
            f'DELETE FROM s_slot_release_event WHERE session_slot_id IN (SELECT id FROM s_session_slot WHERE court_id={court}); '
            f'DELETE FROM s_session_slot WHERE court_id={court}; '
            f'DELETE FROM s_weekly_slot WHERE court_id={court}; '
            f'DELETE FROM s_court_sport WHERE court_id={court}; '
            f"DELETE FROM s_court WHERE id={court} AND name='{PREFIX}';")
    if admin:
        http('/auth/admin/logout', method='POST')

print('PASS F03: snapshots, adjacent intervals, different-slot concurrency, occupied weekly occurrence, RR current read; fixtures cleaned')
