"""隔离 P5 测试库内的 P6 报表 HTTP 验收；只新增本脚本专用只读报表事实。"""
import datetime as dt
import json
import os
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

BASE = os.environ.get('P5_API_BASE', 'http://127.0.0.1:8091/api')
CONTAINER = os.environ['P5_MYSQL_CONTAINER']
assert 'p5-' in CONTAINER or 'p6-' in CONTAINER, '仅允许隔离验收容器'
ZONE = ZoneInfo('Asia/Shanghai')
DAY = dt.datetime.now(ZONE).date() - dt.timedelta(days=3)
FUTURE = dt.datetime.now(ZONE).date() + dt.timedelta(days=1)


def sql(statement):
    return subprocess.run(['docker', 'exec', '-i', CONTAINER, 'mysql', '-uroot', '-preview_root',
                           '--default-character-set=utf8mb4', '-Ns', 'sports_booking'],
                          input=statement, text=True, capture_output=True, check=True).stdout.strip()


def http(path, token=None, body=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['X-Admin-Token'] = token
    request = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return json.load(response)


def ok(path, token=None, body=None):
    result = http(path, token, body)
    assert result['code'] == 0, (path, result)
    return result['data']


def instant(day, clock):
    if clock == '24:00':
        day, clock = day + dt.timedelta(days=1), '00:00'
    return dt.datetime.combine(day, dt.time.fromisoformat(clock), ZONE).astimezone(dt.timezone.utc).strftime('%Y-%m-%d %H:%M:%S')


sport = int(sql("SELECT id FROM s_sport_type WHERE owner_venue_id IS NULL AND status='ACTIVE' ORDER BY id LIMIT 1"))
second_sport = int(sql("SELECT id FROM s_sport_type WHERE owner_venue_id IS NULL AND status='ACTIVE' ORDER BY id DESC LIMIT 1"))
# 自有 fixture IDs；重复执行只重建自己的事实，不触碰预约、支付或余额流程的数据。
sql("DELETE FROM s_booking WHERE booking_no LIKE 'P6UTIL-%'; DELETE FROM s_session_slot WHERE id BETWEEN 981001 AND 981020; DELETE FROM s_court_sport WHERE court_id IN (981001,981002,981003,981004); DELETE FROM s_court WHERE id IN (981001,981002,981003,981004);")
sql(f"INSERT INTO s_court(id,venue_id,name,status,created_at) VALUES(981001,1,'P6分钟测试场','ACTIVE','{DAY-dt.timedelta(days=10)} 00:00:00'),(981002,2,'P6隔离测试场','ACTIVE','{DAY-dt.timedelta(days=10)} 00:00:00'); INSERT INTO s_court_sport(court_id,sport_type_id) VALUES(981001,{sport}),(981002,{sport});")


def slot(number, start, end, status='OPEN', day=DAY):
    sql(f"INSERT INTO s_session_slot(id,venue_id,court_id,sport_type_id,manual_override,slot_date,start_at,end_at,price_fen,status) VALUES({981000+number},1,981001,{sport},1,'{day}','{instant(day,start)}','{instant(day,end)}',1000,'{status}');")


def booking(number, slot_number, start, end, status='ENDED_UNTRACKED', use_sport=sport):
    sql(f"INSERT INTO s_booking(booking_no,venue_id,court_id,sport_type_id,session_slot_id,source,contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,status,payment_status) VALUES('P6UTIL-{number}',1,981001,{use_sport},{981000+slot_number},'OFFLINE','本地Mock','MOCK_PHONE','P6验收','P6分钟测试场','测试运动','{instant(DAY,start)}','{instant(DAY,end)}',1000,'{status}','PAID');")


slot(1, '10:00', '11:00')
slot(2, '10:30', '11:30')  # 不同起止的重叠供给，正确容量共 90 分钟。
slot(3, '14:00', '15:00', 'BOOKED')
booking(1, 3, '10:45', '11:15')  # 人工改时后快照不能丢失，也不能增加 14 点供给。
booking(2, 1, '10:15', '11:15', 'CANCELLED')
slot(4, '12:00', '13:00', 'CLOSED')
booking(3, 4, '12:00', '13:00', 'CANCELLED')
slot(5, '22:00', '24:00')
slot(6, '16:00', '17:00', 'BOOKED')
booking(4, 6, '22:55', '23:05')
slot(7, '18:00', '19:00', day=FUTURE)
slot(8, '20:00', '21:00', 'HELD')
admin = ok('/auth/admin/login', body={'username': 'admin', 'password': 'admin123'})['token']
venue = ok('/auth/admin/login', body={'username': 'venue1', 'password': 'venue123'})['token']
other = ok('/auth/admin/login', body={'username': 'venue2', 'password': 'venue123'})['token']
params = {'startDate': str(DAY), 'endDate': str(DAY), 'courtId': 981001}
path = '/venue-admin/reports/utilization?'
report = ok(path + urllib.parse.urlencode(params), venue)
court = report['courts'][0]
assert (court['availableMinutes'], court['bookedMinutes'], court['cancelledCount'], court['bookingCount']) == (270, 40, 2, 4), court
cells = {c['hourOfDay']: c for c in report['heatmap'] if c['dayOfWeek'] == DAY.isoweekday()}
assert cells[10]['availableMinutes'] == 60 and cells[10]['bookedMinutes'] == 15, cells[10]
assert cells[11]['availableMinutes'] == 30 and cells[11]['bookedMinutes'] == 15, cells[11]
assert cells[12]['dataStatus'] == 'UNAVAILABLE' and cells[12]['cancelledCount'] == 1
assert cells[9]['dataStatus'] == 'DATA_INSUFFICIENT'
assert cells[14]['dataStatus'] == 'DATA_INSUFFICIENT', '改时后的已占用场次不能复制出一份容量'
assert cells[20]['availableMinutes'] == 60 and cells[20]['bookedMinutes'] == 0, '支付占位不计确认占用'
assert sum(c['cancelledCount'] for c in report['heatmap']) == 2
assert not report['futureCourts']
assert not ok(path + urllib.parse.urlencode(params), other)['courts'], '不能读取其他馆场地'
assert http(path + urllib.parse.urlencode(params), admin)['code'] != 0, '平台不能使用场馆明细入口'
platform = ok('/platform/reports/utilization?' + urllib.parse.urlencode({'startDate': str(DAY), 'endDate': str(DAY), 'venueId': 1}), admin)
assert not platform['courts'] and not platform['futureCourts'] and not platform['lowUtilization']
assert not platform['courtDetailsIncluded']
assert http('/platform/reports/utilization?' + urllib.parse.urlencode(params), admin)['code'] != 0
night = next(row for row in report['lowUtilization'] if row['startTime'].endswith('23:00'))
assert night['endTime'].startswith(str(DAY + dt.timedelta(days=1)))
drill = {**params, 'slotStart': night['startTime'], 'slotEnd': night['endTime'], 'sportTypeId': sport}
rows = ok('/venue-admin/reports/bookings-filtered?' + urllib.parse.urlencode(drill), venue)
assert [row['bookingNo'] for row in rows] == ['P6UTIL-4'], rows
if second_sport != sport:
    assert not ok('/venue-admin/reports/bookings-filtered?' + urllib.parse.urlencode({**drill, 'sportTypeId': second_sport}), venue)
future = ok(path + urllib.parse.urlencode({**params, 'startDate': str(FUTURE), 'endDate': str(FUTURE)}), venue)
assert not future['courts'] and future['futureCourts'][0]['availableMinutes'] == 60
assert all(c['availableMinutes'] == 0 for c in future['heatmap'])

# 同一星期的另一天完全缺失，必须保持未知；显式关闭则是已知且没有容量。
previous = DAY - dt.timedelta(days=7)
repeated_params = {**params, 'startDate': str(previous)}
repeated = ok(path + urllib.parse.urlencode(repeated_params), venue)
cell = next(c for c in repeated['heatmap'] if c['dayOfWeek'] == DAY.isoweekday() and c['hourOfDay'] == 10)
assert (cell['dataStatus'], cell['availableMinutes'], cell['bookedMinutes']) == ('DATA_INSUFFICIENT', 60, 15), cell
assert repeated['courts'][0]['dataStatus'] == 'DATA_INSUFFICIENT'
slot(9, '10:00', '11:00', 'CLOSED', day=previous)
closed = ok(path + urllib.parse.urlencode(repeated_params), venue)
cell = next(c for c in closed['heatmap'] if c['dayOfWeek'] == DAY.isoweekday() and c['hourOfDay'] == 10)
assert (cell['dataStatus'], cell['availableMinutes'], cell['bookedMinutes']) == ('KNOWN', 60, 15), cell

# 空场地也必须参与完整性检查，场地创建前的日期却不能被当成缺失供给。
sql(f"INSERT INTO s_court(id,venue_id,name,status,created_at) VALUES(981003,1,'P6空事实测试场','ACTIVE','{previous} 00:00:00'),(981004,1,'P6新建日期测试场','ACTIVE','{DAY} 23:30:00'); INSERT INTO s_court_sport(court_id,sport_type_id) VALUES(981003,{sport}),(981004,{sport});")
empty = ok(path + urllib.parse.urlencode({**repeated_params, 'courtId': 981003}), venue)
assert empty['courts'][0]['dataStatus'] == 'DATA_INSUFFICIENT' and empty['courts'][0]['availableMinutes'] == 0
assert not empty['lowUtilization']
created = ok(path + urllib.parse.urlencode({**repeated_params, 'courtId': 981004}), venue)
assert created['courts'][0]['dataStatus'] == 'DATA_INSUFFICIENT', '创建时间按旧链路业务时区理解，不能漏掉当晚创建的空场地'
sql(f"INSERT INTO s_session_slot(id,venue_id,court_id,sport_type_id,manual_override,slot_date,start_at,end_at,price_fen,status) VALUES(981010,1,981004,{sport},1,'{DAY}','{instant(DAY,'23:30')}','{instant(DAY,'24:00')}',1000,'OPEN');")
created = ok(path + urllib.parse.urlencode({**repeated_params, 'courtId': 981004}), venue)
cell = next(c for c in created['heatmap'] if c['dayOfWeek'] == DAY.isoweekday() and c['hourOfDay'] == 23)
assert (cell['dataStatus'], cell['availableMinutes']) == ('KNOWN', 30), cell
if second_sport != sport:
    unrelated = ok(path + urllib.parse.urlencode({**repeated_params, 'courtId': 981003, 'sportTypeId': second_sport}), venue)
    assert not unrelated['courts'], '运动筛选不能纳入无关联也无历史事实的场地'
sql('DELETE FROM s_court_sport WHERE court_id=981001;')
try:
    removed = ok(path + urllib.parse.urlencode({**repeated_params, 'sportTypeId': sport}), venue)
    cell = next(c for c in removed['heatmap'] if c['dayOfWeek'] == DAY.isoweekday() and c['hourOfDay'] == 11)
    assert cell['dataStatus'] == 'DATA_INSUFFICIENT', '移除当前运动关联不能隐藏历史范围内缺失日期'
finally:
    sql(f'INSERT INTO s_court_sport(court_id,sport_type_id) VALUES(981001,{sport});')
print('P6 utilization HTTP passed: interval union 270/40, snapshots, held supply, unique cancellation counts, missing data, future separation, platform scope and midnight/sport drilldown')
print('P6 utilization completeness passed: repeated weekday gaps, explicit closures, empty courts, creation boundary and current/historical sport scope')
