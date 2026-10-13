"""在 verify-p5-time-search.zsh 的隔离 HTTP 环境核对区县资料与组合筛选。"""
import datetime as dt
import json
import os
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

base = os.environ.get('P5_API_BASE', 'http://127.0.0.1:8091/api')
container = os.environ['P5_MYSQL_CONTAINER']
day = dt.datetime.now(ZoneInfo('Asia/Shanghai')).date() + dt.timedelta(days=1)


def sql(query):
    result = subprocess.run(['docker', 'exec', '-i', container, 'mysql', '-uroot', '-preview_root',
                             '--default-character-set=utf8mb4', '-Ns', 'sports_booking'],
                            input=query, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def http(path, token=None, body=None, method=None, token_header="X-Admin-Token"):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers[token_header] = token
    request = urllib.request.Request(base + path,
        data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return json.load(response)


def ok(path, token=None, body=None, method=None, token_header="X-Admin-Token"):
    result = http(path, token, body, method, token_header)
    assert result['code'] == 0, (path, result['msg'])
    return result['data']


def login(username, password):
    return ok('/auth/admin/login', body={'username': username, 'password': password})['token']


original = sql("SELECT CONCAT_WS('|',COALESCE((SELECT district FROM s_venue WHERE id=1),'__NULL__'),COALESCE((SELECT district FROM s_venue WHERE id=2),'__NULL__'))")
old_one, old_two = original.split('|')
try:
    sql("UPDATE s_venue SET district='金水区' WHERE id=1; UPDATE s_venue SET district='二七区' WHERE id=2;")
    mini_token = ok('/auth/mini/mock-login', body={'code': 'p5-district-user'})['token']
    sport_id = ok('/mini/sports', mini_token, token_header='X-Mini-Token')[0]['id']

    def search(**filters):
        query = {'date': str(day), 'keyword': 'P5测试', 'startTime': '19:00', 'endTime': '21:00'}
        query.update(filters)
        return ok('/mini/slots/search?' + urllib.parse.urlencode(query), mini_token, token_header='X-Mini-Token')

    all_districts = [row for row in search() if row['id'] in (880001, 880002, 880003)]
    assert {row['id'] for row in all_districts} == {880001, 880002, 880003}, all_districts
    jinshui = [row for row in search(district='金水区') if row['id'] in (880001, 880002, 880003)]
    erqi = [row for row in search(district='二七区') if row['id'] in (880001, 880002, 880003)]
    assert {row['id'] for row in jinshui} == {880001, 880002}, jinshui
    assert {row['id'] for row in erqi} == {880003}, erqi
    combined = [row for row in search(district='金水区', sportTypeId=sport_id,
        minPriceFen=5050, maxPriceFen=6000) if row['id'] in (880001, 880002, 880003)]
    assert [row['id'] for row in combined] == [880001], combined

    venues = ok('/mini/venues?date=' + str(day) + '&district=' + urllib.parse.quote('二七区'),
        mini_token, token_header='X-Mini-Token')
    assert {row['id'] for row in venues} == {2}, [row['id'] for row in venues]
    assert next(row for row in ok('/mini/venues?date=' + str(day), mini_token, token_header='X-Mini-Token')
        if row['id'] == 1)['district'] == '金水区'
    detail = ok('/mini/venues/1?' + urllib.parse.urlencode({'date': str(day)}),
        mini_token, token_header='X-Mini-Token')
    assert detail['venue']['district'] == '金水区'

    venue_token = login('venue1', 'venue123')
    profile = ok('/venue-admin/profile/detail', venue_token)
    payload = {key: profile[key] for key in ('name', 'city', 'district', 'address', 'latitude', 'longitude',
        'phone', 'facilities', 'entryNotice', 'bookingDaysAhead', 'minBookingMinutes', 'cancelCutoffMinutes')}
    payload['district'] = '中原区'
    ok('/venue-admin/profile/update', venue_token, payload, 'PUT')
    assert ok('/venue-admin/profile/detail', venue_token)['district'] == '中原区'

    print('P5 district: unfiltered compatibility, slot + district/sport/price filters, public list/detail, and venue profile update passed')
finally:
    restore_one = 'NULL' if old_one == '__NULL__' else "'" + old_one.replace("'", "''") + "'"
    restore_two = 'NULL' if old_two == '__NULL__' else "'" + old_two.replace("'", "''") + "'"
    sql(f'UPDATE s_venue SET district={restore_one} WHERE id=1; UPDATE s_venue SET district={restore_two} WHERE id=2;')
