"""隔离 HTTP 验收：时段/价格、分页、并发库存与原构成退款。"""
import concurrent.futures
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

BASE = 'http://127.0.0.1:8091/api'
CONTAINER = os.environ['P5_MYSQL_CONTAINER']
ZONE = ZoneInfo('Asia/Shanghai')
TODAY = dt.datetime.now(ZONE).date()
DAY = TODAY + dt.timedelta(days=1)


def sql(query, database='sports_booking'):
    result = subprocess.run(['docker', 'exec', '-i', CONTAINER, 'mysql', '-uroot', '-preview_root',
                             '--default-character-set=utf8mb4', '-Ns', database], input=query, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def http(path, token=None, body=None, admin=False):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['X-Admin-Token' if admin else 'X-Mini-Token'] = token
    request = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        result = json.load(response)
        result["_http_status"] = response.status
        return result


def ok(path, token=None, body=None, admin=False):
    result = http(path, token, body, admin)
    assert result['code'] == 0, (path, result['msg'])
    return result['data']


def login(code):
    return ok('/auth/mini/mock-login', body={'code': code})['token']


def search(token, **params):
    query = {'date': str(DAY), 'keyword': 'P5测试', 'startTime': '19:00', 'endTime': '21:00'}
    query.update(params)
    return ok('/mini/slots/search?' + urllib.parse.urlencode(query), token)


user = login('p5-time-search-user')
other = login('p5-time-search-other')
sport = ok('/mini/sports', user)[0]['id']
# 只向本脚本新建的容器写验收数据；不给既有演示实例写资金流水。
sql("UPDATE s_venue SET name=CONCAT('P5测试馆', id), booking_days_ahead=7 WHERE id IN (1,2,3);")
statements = []
for court_id, venue_id, state in [(880001, 1, 'ACTIVE'), (880002, 2, 'ACTIVE'), (880003, 3, 'ACTIVE'), (880004, 1, 'DISABLED')]:
    statements.append(f"INSERT INTO s_court(id,venue_id,name,status) VALUES({court_id},{venue_id},'测试场地{court_id}','{state}');")
    statements.append(f"INSERT INTO s_court_sport(court_id,sport_type_id) VALUES({court_id},{sport});")

def fixture(slot_id, court_id, venue_id, start, end, price, state='OPEN', day=DAY):
    start_at = dt.datetime.combine(day, dt.time.fromisoformat(start), ZONE).astimezone(dt.timezone.utc)
    end_at = dt.datetime.combine(day, dt.time.fromisoformat(end), ZONE).astimezone(dt.timezone.utc)
    return f"INSERT INTO s_session_slot(id,venue_id,court_id,sport_type_id,manual_override,slot_date,start_at,end_at,price_fen,status) VALUES({slot_id},{venue_id},{court_id},{sport},1,'{day}','{start_at:%Y-%m-%d %H:%M:%S}','{end_at:%Y-%m-%d %H:%M:%S}',{price},'{state}');"

statements += [fixture(880001, 880001, 1, '19:00', '20:00', 5050),
               fixture(880002, 880001, 1, '20:00', '21:00', 6500),
               fixture(880003, 880002, 2, '19:30', '20:30', 7000),
               fixture(880004, 880002, 2, '18:00', '19:00', 2000),
               fixture(880005, 880002, 2, '21:00', '22:00', 2000),
               fixture(880006, 880003, 3, '19:00', '20:00', 6000, 'HELD'),
               fixture(880007, 880003, 3, '20:00', '21:00', 6000, 'CLOSED'),
               fixture(880008, 880004, 1, '19:00', '20:00', 6000),
               fixture(880009, 880002, 2, '22:00', '23:00', 6000, day=TODAY + dt.timedelta(days=7))]
sql('\n'.join(statements))
rows = search(user, minPriceFen=5000, maxPriceFen=7000, sportTypeId=sport)
# 既有演示场次不影响断言；测试场地具有独立 ID。
ours = [row for row in rows if row['courtId'] >= 880000]
assert [row['id'] for row in ours] == [880001, 880003, 880002], (rows, sql("SELECT ss.id,ss.slot_date,ss.start_at,v.name,v.status,c.status,st.status FROM s_session_slot ss JOIN s_venue v ON v.id=ss.venue_id JOIN s_court c ON c.id=ss.court_id JOIN s_sport_type st ON st.id=ss.sport_type_id WHERE ss.id BETWEEN 880001 AND 880009"))
assert ours[0]['priceFen'] == 5050 and ours[0]['startAt'].endswith('+08:00')
assert all('bookingId' not in row and 'contactPhone' not in row for row in rows)
assert [r['id'] for r in search(user, minPriceFen=5050, maxPriceFen=5050) if r['courtId'] >= 880000] == [880001]
print('P5: full containment, exact cents, stable ordering and public fields passed')

for params in [{'startTime': '21:00', 'endTime': '19:00'}, {'startTime': '19:00', 'endTime': '19:00'},
               {'startTime': '23:00', 'endTime': '01:00'}, {'startTime': '19:00'},
               {'minPriceFen': -1}, {'minPriceFen': '50.50'}, {'minPriceFen': 100, 'maxPriceFen': 99},
               {'page': 0}, {'page': 10001}]:
    response = http('/mini/slots/search?' + urllib.parse.urlencode({'date': str(DAY), **params}), user)
    assert response['code'] != 0 and response['_http_status'] == 400 and response['msg'] == '参数不正确', (params, response)
assert not [r for r in search(user, date=str(TODAY + dt.timedelta(days=7)), startTime='22:00', endTime='23:00') if r['id'] == 880009]
sql("UPDATE s_venue SET min_booking_minutes=100000 WHERE id=1;")
assert not [r for r in search(user) if r['venueId'] == 1]
sql("UPDATE s_venue SET min_booking_minutes=30, status='SUSPENDED' WHERE id=1;")
assert not [r for r in search(user) if r['venueId'] == 1]
blocked = http('/mini/bookings/create', user, {'sessionSlotId': 880001, 'contactName': '测试用户', 'contactPhone': '13800000000'})
assert blocked['msg'] == '场次不可预约'
sql("UPDATE s_venue SET status='ACTIVE' WHERE id=1;")
print('P5: invalid filters, sale window, lead time and suspended venue passed')

# 分页构造来自独立场地，稳定顺序不能遗漏或重复具体场次。
statements = []
for index in range(23):
    court_id = 881000 + index
    statements += [f"INSERT INTO s_court(id,venue_id,name) VALUES({court_id},1,'分页场地{index}');",
                   f"INSERT INTO s_court_sport VALUES({court_id},{sport});",
                   fixture(court_id, court_id, 1, '15:00', '16:00', 1234)]
sql('\n'.join(statements))
first = search(user, startTime='15:00', endTime='16:00', minPriceFen=1234, maxPriceFen=1234)
second = search(user, startTime='15:00', endTime='16:00', minPriceFen=1234, maxPriceFen=1234, page=2)
assert len(first) == 20 and len(second) == 3
assert [r['id'] for r in first + second] == list(range(881000, 881023))
detail = ok('/mini/venues/1?' + urllib.parse.urlencode({'date': str(DAY), 'startTime': '19:00', 'endTime': '21:00', 'minPriceFen': 5000, 'maxPriceFen': 5500}), user)
assert [r['id'] for r in detail['slots']] == [880001]
assert isinstance(ok('/mini/venues?date=' + str(DAY), user), list)
assert isinstance(ok('/mini/venues/1?date=' + str(DAY), user)['slots'], list)
print('P5: bounded pagination, detail filters and old requests passed')

body = {'sessionSlotId': 880001, 'contactName': '测试用户', 'contactPhone': '13800000000',
        'idempotencyKey': 'p5-time-search-booking', 'expectedPriceFen': 5050, 'entryType': 'TIME_SEARCH'}
sql('UPDATE s_session_slot SET price_fen=5550 WHERE id=880001;')
changed = http('/mini/bookings/create', user, body)
assert changed['msg'] == '场次价格已变化，请返回刷新后重新确认'
assert not any(r['sessionSlotId'] == 880001 for r in ok('/mini/bookings/list', user))
body['expectedPriceFen'] = 5550
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
    responses = list(executor.map(lambda token: http('/mini/bookings/create', token, body), [user, other]))
assert sum(r['code'] == 0 for r in responses) == 1, [r['msg'] for r in responses]
winner = user if responses[0]['code'] == 0 else other
loser = other if winner == user else user
created = next(r['data'] for r in responses if r['code'] == 0)
assert created['amountFen'] == 5550
assert not any(r['sessionSlotId'] == 880001 for r in ok('/mini/bookings/list', loser))
assert sql(f"SELECT entry_type FROM s_booking WHERE id={created['id']}") == 'TIME_SEARCH'
replay = ok('/mini/bookings/create', winner, body)
assert replay['id'] == created['id']
confirmed = ok(f"/mini/bookings/{created['id']}/mock-pay", winner, {})
assert confirmed['status'] == 'CONFIRMED'
cancelled = ok(f"/mini/bookings/{created['id']}/cancel", winner, {})
assert cancelled['status'] == 'CANCELLED' and cancelled['paymentStatus'] == 'REFUNDED'
assert any(row['id'] == 880001 for row in search(user))
print('P5: changed price, last-slot race, idempotency, entry attribution and refund passed')

# 再订从本人历史详情读取偏好，新日期、价格和资金组成全部重新确认。
legacy = ok(f"/mini/bookings/{created['id']}", winner)['booking']
assert http(f"/mini/bookings/{created['id']}", loser)['code'] != 0
new_day = DAY + dt.timedelta(days=1)
sql(fixture(883001, 880001, 1, '19:00', '20:30', 7777, day=new_day))
new_slots = ok('/mini/venues/1?date=' + str(new_day), winner)['slots']
assert next(row for row in new_slots if row['id'] == 883001)['priceFen'] == 7777
rebook = ok('/mini/bookings/create', winner, {'sessionSlotId': 883001, 'contactName': '新确认联系人',
    'contactPhone': '13800000000', 'expectedPriceFen': 7777, 'entryType': 'BOOK_AGAIN',
    'idempotencyKey': 'p5-book-again'})
assert rebook['amountFen'] == 7777 and rebook['id'] != legacy['id']
assert rebook['contactName'] == '新确认联系人'
assert sql(f"SELECT entry_type FROM s_booking WHERE id={rebook['id']}") == 'BOOK_AGAIN'
ok(f"/mini/bookings/{rebook['id']}/mock-pay", winner, {})
assert ok(f"/mini/bookings/{rebook['id']}/cancel", winner, {})['paymentStatus'] == 'REFUNDED'
print('P5: owned history, new date/current price, BOOK_AGAIN attribution and refund passed')

# 同一 DDL 在 V1-V10 数据库升级：保留预约快照与历史来源空值。
sql('CREATE DATABASE p5_upgrade;')
migrations = Path(__file__).resolve().parents[1] / 'sports-booking-server/src/main/resources/db/migration'
for version in range(1, 11):
    sql(next(migrations.glob(f'V{version}__*.sql')).read_text(), 'p5_upgrade')
sql("""INSERT INTO s_venue(id,name,address,phone,status) VALUES(1,'升级测试馆','测试地址','00000000000','ACTIVE');
INSERT INTO s_court(id,venue_id,name) VALUES(1,1,'升级场地');
INSERT INTO s_session_slot(id,venue_id,court_id,sport_type_id,slot_date,start_at,end_at,price_fen,status)
VALUES(1,1,1,1,'2026-01-01','2026-01-01 10:00:00','2026-01-01 11:00:00',5050,'BOOKED');
INSERT INTO s_booking(id,booking_no,venue_id,court_id,sport_type_id,session_slot_id,source,contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,status,payment_status)
VALUES(1,'p5-legacy',1,1,1,1,'OFFLINE','测试用户','00000000000','旧馆名','旧场地','旧运动','2026-01-01 10:00:00','2026-01-01 11:00:00',5050,'CONFIRMED','UNPAID');""", 'p5_upgrade')
sql(next(migrations.glob('V11__*.sql')).read_text(), 'p5_upgrade')
assert sql("SELECT CONCAT(amount_fen,':',venue_name,':',IF(entry_type IS NULL,'NULL',entry_type)) FROM s_booking WHERE id=1", 'p5_upgrade') == '5050:旧馆名:NULL'
assert int(sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1')) >= 14
for version in range(12, 16):
    sql(next(migrations.glob(f'V{version}__*.sql')).read_text(), 'p5_upgrade')
assert sql("SELECT IF(district IS NULL,'NULL',district) FROM s_venue WHERE id=1", 'p5_upgrade') == 'NULL'
assert sql("SELECT CONCAT(amount_fen,':',venue_name) FROM s_booking WHERE id=1", 'p5_upgrade') == '5050:旧馆名'
print('P5: Flyway empty database and SQL upgrade V10-V15 preserve history passed')
# 运行态 schema 是契约真源，导出新查询和可选字段供仓库审阅。
request = urllib.request.Request(BASE + '/v3/api-docs.yaml')
with urllib.request.urlopen(request, timeout=20) as response:
    document = response.read()
# 隔离验收端口不改变仓库默认本地接口地址。
document = document.replace(b'http://127.0.0.1:8091/api', b'http://127.0.0.1:8089/api')
assert b'/mini/slots/search:' in document and b'expectedPriceFen:' in document
Path(__file__).resolve().parents[2].joinpath('docs/openapi.yaml').write_bytes(document)
