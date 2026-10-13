"""公开只读验收，配合 P5 时段检索脚本生成的隔离场次。"""
import datetime as dt
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

base = os.environ.get('P5_API_BASE', 'http://127.0.0.1:8091/api')
day = dt.datetime.now(ZoneInfo('Asia/Shanghai')).date() + dt.timedelta(days=1)


def read(venue_id, **filters):
    query = urllib.parse.urlencode({'date': str(day), **filters})
    with urllib.request.urlopen(f'{base}/public/venues/{venue_id}/slot-comparison?{query}', timeout=15) as response:
        result = json.load(response)
    assert result['code'] == 0, result
    return result['data']


rows = read(1)
assert rows and all(set(row) == {'id', 'courtId', 'courtName', 'sportTypeId', 'sportName', 'startAt',
                               'endAt', 'priceFen', 'status', 'cancelDeadline'} for row in rows), rows
ours = {row['id']: row for row in rows if row['courtId'] >= 880000}
assert ours[880008]['status'] == 'UNAVAILABLE', ours
assert ours[880001]['priceFen'] in (5050, 5550) and ours[880001]['startAt'].endswith('+08:00')
assert (dt.datetime.fromisoformat(ours[880001]['startAt']) -
        dt.datetime.fromisoformat(ours[880001]['cancelDeadline'])).total_seconds() >= 0
third = {row['id']: row for row in read(3)}
assert third[880006]['status'] == 'HELD' and third[880007]['status'] == 'CLOSED', third
assert all(row['priceFen'] <= 5100 and row['startAt'][11:16] >= '19:00' and row['endAt'][11:16] <= '21:00'
           for row in read(1, startTime='19:00', endTime='21:00', maxPriceFen=5100))
try:
    read(1, startTime='19:00')
except urllib.error.HTTPError as error:
    assert error.code == 400
else:
    raise AssertionError('未拒绝单边时间条件')
print('P5 public comparison projection, statuses, full ranges and filters passed')
