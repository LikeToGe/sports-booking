"""隔离环境 HTTP 验收：真实匹配、时区、治理边界和最后名额并发。"""

import json
import os
import subprocess
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo


BASE = os.environ["P5_API_BASE"].rstrip("/")
CONTAINER = os.environ["P5_MYSQL_CONTAINER"]


def sql(statement):
    result = subprocess.run(
        ["docker", "exec", CONTAINER, "mysql", "--default-character-set=utf8mb4",
         "-uroot", "-preview_root", "-Ns", "sports_booking", "-e", statement],
        check=True, text=True, capture_output=True,
    )
    return result.stdout.strip()


def api(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Mini-Token"] = token
    request = urllib.request.Request(
        BASE + path, method=method, headers=headers,
        data=json.dumps(body).encode() if body is not None else None,
    )
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return json.load(response)


def user():
    result = api("POST", "/auth/mini/mock-login", body={"code": "p5-discovery-" + uuid.uuid4().hex})
    assert result["code"] == 0, result
    token = result["data"]["token"]
    agreement = api("GET", "/mini/content/agreement", token)
    assert agreement["code"] == 0, agreement
    rules = agreement["data"]
    accepted = api("POST", "/mini/content/agreement", token,
                   {"rulesVersion": rules["rulesVersion"], "privacyVersion": rules["privacyVersion"]})
    assert accepted["code"] == 0, accepted
    return token, int(result["data"]["profile"]["id"])


def discover(token, date=None, level=None):
    query = "/mini/recruitments?page=1&size=20&minRemaining=1"
    if date:
        query += "&date=" + date
    if level:
        query += "&levelTag=" + level
    result = api("GET", query, token)
    assert result["code"] == 0, result
    return result["data"]


def contains(rows, post_id):
    return [row for row in rows if row["postId"] == post_id]


def expect_visible(token, date, post_id, expected):
    rows = contains(discover(token, date), post_id)
    assert bool(rows) == expected, rows
    return rows


author_token, author_id = user()
first_token, first_id = user()
second_token, second_id = user()
late_token, _ = user()

venue_id, court_id, sport_id = map(int, sql("""
    SELECT v.id,c.id,cs.sport_type_id FROM s_venue v
    JOIN s_court c ON c.venue_id=v.id AND c.status='ACTIVE'
    JOIN s_court_sport cs ON cs.court_id=c.id
    WHERE v.status='ACTIVE' ORDER BY v.id,c.id,cs.sport_type_id LIMIT 1
""").split("\t"))
local_date = (datetime.now(ZoneInfo("Asia/Shanghai")) + timedelta(days=2)).date()
first_second = int(uuid.uuid4().hex[:8], 16) % 3600
for offset in range(3600):
    second_of_hour = (first_second + offset) % 3600
    start = datetime.combine(local_date, time(0, second_of_hour // 60, second_of_hour % 60),
                             ZoneInfo("Asia/Shanghai"))
    candidate = start.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    if sql(f"SELECT COUNT(*) FROM s_session_slot WHERE court_id={court_id} AND start_at='{candidate}'") == "0":
        break
else:
    raise AssertionError("本地跨日测试时段已全部占用")
end = start + timedelta(hours=1)
utc_start = start.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
utc_end = end.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
previous_utc_date = start.astimezone(timezone.utc).date().isoformat()
assert previous_utc_date != local_date.isoformat()
booking_no = "P5R" + uuid.uuid4().hex[:24].upper()

slot_id = int(sql(f"""
    INSERT INTO s_session_slot (venue_id,court_id,sport_type_id,slot_date,start_at,end_at,price_fen,status)
    VALUES ({venue_id},{court_id},{sport_id},'{local_date}','{utc_start}','{utc_end}',6000,'BOOKED');
    SELECT LAST_INSERT_ID();
""").splitlines()[-1])
booking_id = int(sql(f"""
    INSERT INTO s_booking (booking_no,user_id,venue_id,court_id,sport_type_id,session_slot_id,source,
      contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,
      external_amount_fen,service_fee_rate_basis_points,status,payment_status)
    SELECT '{booking_no}',{author_id},s.venue_id,s.court_id,s.sport_type_id,s.id,'ONLINE',
      '本地测试','00000000000',v.name,c.name,st.name,s.start_at,s.end_at,6000,6000,0,'CONFIRMED','PAID'
    FROM s_session_slot s JOIN s_venue v ON v.id=s.venue_id JOIN s_court c ON c.id=s.court_id
    JOIN s_sport_type st ON st.id=s.sport_type_id WHERE s.id={slot_id};
    SELECT LAST_INSERT_ID();
""").splitlines()[-1])
sql(f"UPDATE s_session_slot SET hold_booking_id={booking_id} WHERE id={slot_id}")
post_id = int(sql(f"""
    INSERT INTO s_post (author_type,author_id,content_status) VALUES ('USER',{author_id},'PUBLISHED');
    SELECT LAST_INSERT_ID();
""").splitlines()[-1])
revision_id = int(sql(f"""
    INSERT INTO s_post_revision (post_id,revision_no,body,sport_type_id,venue_id,booking_id,moderation_status)
    VALUES ({post_id},1,'本地找搭子验收',{sport_id},{venue_id},{booking_id},'APPROVED');
    SELECT LAST_INSERT_ID();
""").splitlines()[-1])
sql(f"UPDATE s_post SET current_revision_id={revision_id},public_revision_id={revision_id} WHERE id={post_id}")
recruitment_id = int(sql(f"""
    INSERT INTO s_post_recruitment (post_id,booking_id,level,level_tag,max_members,fee_mode,aa_amount_fen,status)
    VALUES ({post_id},{booking_id},'CASUAL','CASUAL',1,'FREE',0,'RECRUITING');
    SELECT LAST_INSERT_ID();
""").splitlines()[-1])

row = expect_visible(first_token, local_date.isoformat(), post_id, True)[0]
assert row["venueId"] == venue_id and row["sportTypeId"] == sport_id, row
assert row["remainingCount"] == 1 and "bookingId" not in row, row
assert contains(discover(first_token, local_date.isoformat(), "CASUAL"), post_id)
expect_visible(first_token, previous_utc_date, post_id, False)
opened = api("POST", "/mini/content/events?eventType=RECRUITMENT_DISCOVERY_OPEN"
             f"&targetType=POST&targetId={post_id}", first_token)
assert opened["code"] == 0, opened
open_count = sql(f"""SELECT COUNT(*) FROM s_user_action_event WHERE user_id={first_id}
    AND event_type='RECRUITMENT_DISCOVERY_OPEN' AND target_type='POST' AND target_id={post_id}""")
assert open_count == "1", open_count

# 旧招募自由文本保留，不能推断有限标签。
sql(f"UPDATE s_post_recruitment SET level='历史水平说明',level_tag=NULL WHERE id={recruitment_id}")
historical = expect_visible(first_token, local_date.isoformat(), post_id, True)[0]
assert historical["levelTag"] is None and historical["level"] == "历史水平说明", historical
assert not contains(discover(first_token, local_date.isoformat(), "CASUAL"), post_id)
sql(f"UPDATE s_post_recruitment SET level='CASUAL',level_tag='CASUAL' WHERE id={recruitment_id}")

# 公开动态伪造的场馆/预约关系和线下预约不能进入可申请列表。
sql(f"UPDATE s_post_revision SET venue_id=NULL WHERE id={revision_id}")
expect_visible(first_token, local_date.isoformat(), post_id, False)
sql(f"UPDATE s_post_revision SET venue_id={venue_id} WHERE id={revision_id}")
sql(f"UPDATE s_booking SET source='OFFLINE',external_amount_fen=0 WHERE id={booking_id}")
expect_visible(first_token, local_date.isoformat(), post_id, False)
sql(f"UPDATE s_booking SET source='ONLINE',external_amount_fen=6000,status='CANCELLED' WHERE id={booking_id}")
expect_visible(first_token, local_date.isoformat(), post_id, False)
sql(f"UPDATE s_booking SET status='CONFIRMED' WHERE id={booking_id}")
sql(f"UPDATE s_post SET content_status='HIDDEN' WHERE id={post_id}")
expect_visible(first_token, local_date.isoformat(), post_id, False)
sql(f"UPDATE s_post SET content_status='PUBLISHED' WHERE id={post_id}")

for blocker, blocked in [(first_id, author_id), (author_id, first_id)]:
    sql(f"INSERT INTO s_user_block (blocker_user_id,blocked_user_id) VALUES ({blocker},{blocked})")
    expect_visible(first_token, local_date.isoformat(), post_id, False)
    rejected = api("POST", f"/mini/posts/recruitments/{recruitment_id}/apply", first_token)
    assert rejected["code"] != 0, rejected
    sql(f"DELETE FROM s_user_block WHERE blocker_user_id={blocker} AND blocked_user_id={blocked}")

for token in [first_token, second_token]:
    applied = api("POST", f"/mini/posts/recruitments/{recruitment_id}/apply", token)
    assert applied["code"] == 0, applied
apply_count = sql(f"""SELECT COUNT(*) FROM s_user_action_event WHERE user_id IN ({first_id},{second_id})
    AND event_type='RECRUITMENT_APPLY' AND target_type='RECRUITMENT' AND target_id={recruitment_id}""")
assert apply_count == "2", apply_count

def approve(user_id):
    return api("POST", f"/mini/posts/recruitments/{recruitment_id}/members/{user_id}/decide?approve=true", author_token)

with ThreadPoolExecutor(max_workers=2) as pool:
    results = list(pool.map(approve, [first_id, second_id]))
assert sum(result["code"] == 0 for result in results) == 1, results
approved = sql(f"SELECT COUNT(*) FROM s_post_recruitment_member WHERE recruitment_id={recruitment_id} AND status='APPROVED'")
assert approved == "1", approved
approved_event = sql(f"""SELECT COUNT(*) FROM s_user_action_event WHERE user_id IN ({first_id},{second_id})
    AND event_type='RECRUITMENT_APPROVED' AND target_type='RECRUITMENT' AND target_id={recruitment_id}""")
assert approved_event == "1", approved_event
expect_visible(first_token, local_date.isoformat(), post_id, False)
late = api("POST", f"/mini/posts/recruitments/{recruitment_id}/apply", late_token)
assert late["code"] != 0, late

# 失效分享只保留状态，不能回传成员。
sql(f"UPDATE s_booking SET status='CANCELLED' WHERE id={booking_id}")
detail = api("GET", f"/mini/posts/{post_id}/recruitment", first_token)
assert detail["code"] == 0 and detail["data"]["status"] == "BOOKING_CANCELLED", detail
assert detail["data"]["members"] == [], detail
print("P5 recruitment HTTP: business date, ownership, historical level, governance, capacity and invalid detail passed")
