"""隔离 MySQL/Redis + HTTP 验收 P6 开售、空位、到场和 Mock 重试。"""

import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo


BASE = os.environ.get("P5_API_BASE", "http://127.0.0.1:8091/api").rstrip("/")
CONTAINER = os.environ["P5_MYSQL_CONTAINER"]
ZONE = ZoneInfo("Asia/Shanghai")
TODAY = datetime.now(ZONE).date()
TOMORROW = TODAY + timedelta(days=1)


def sql(query):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-preview_root",
         "--default-character-set=utf8mb4", "-Ns", "sports_booking"],
        input=query, text=True, capture_output=True, check=True,
    )
    return result.stdout.strip()


def http(path, token=None, body=None, admin=False, method=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Admin-Token" if admin else "X-Mini-Token"] = token
    request = urllib.request.Request(BASE + path, headers=headers,
        data=json.dumps(body).encode() if body is not None else None,
        method=method or ("POST" if body is not None else "GET"))
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return json.load(response)


def ok(path, token=None, body=None, admin=False, method=None):
    result = http(path, token, body, admin, method)
    assert result["code"] == 0, (path, result)
    return result["data"]


def login(code):
    result = ok("/auth/mini/mock-login", body={"code": "p6-reminder-" + code + uuid.uuid4().hex[:8]})
    return result["token"], int(result["profile"]["id"])


def messages(token, message_type=None, subscription_id=None):
    rows = ok("/mini/content/notifications", token)
    return [row for row in rows if (message_type is None or row["type"] == message_type)
            and (subscription_id is None or row["subscriptionId"] == subscription_id)]


def wait_for(check, label, seconds=65):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = check()
        if result:
            return result
        time.sleep(1)
    raise AssertionError(f"等待 {label} 超时")


def moment(day, hour, minute=0):
    local = datetime(day.year, day.month, day.day, hour, minute, tzinfo=ZONE)
    return local.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def create_slot(day, start_hour, end_hour, price, status="OPEN"):
    return int(sql(f"""
        INSERT INTO s_session_slot (venue_id,court_id,sport_type_id,manual_override,slot_date,
            start_at,end_at,price_fen,status)
        VALUES ({venue_id},{court_id},{sport_id},1,'{day}',
            '{moment(day,start_hour)}','{moment(day,end_hour)}',{price},'{status}');
        SELECT LAST_INSERT_ID();
    """).splitlines()[-1])


def create_booking(token, slot_id, price):
    return ok("/mini/bookings/create", token, {
        "sessionSlotId": slot_id, "contactName": "本地测试", "contactPhone": "00000000000",
        "expectedPriceFen": price, "idempotencyKey": "p6-" + uuid.uuid4().hex,
    })


def subscribe(token, alert_type, day, start, end):
    return ok("/mini/reminder-subscriptions", token, {
        "venueId": venue_id, "sportTypeId": sport_id, "targetDate": str(day),
        "startTime": start, "endTime": end, "type": alert_type,
    })


assert sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='sports_booking' "
           "AND table_name='s_reminder_subscription'") == "1", "需要先应用 V20-V22"
venue_id = int(sql("SELECT id FROM s_venue WHERE status='ACTIVE' ORDER BY id LIMIT 1"))
sport_id = int(sql(f"""SELECT cs.sport_type_id FROM s_court c
    JOIN s_court_sport cs ON cs.court_id=c.id JOIN s_sport_type st ON st.id=cs.sport_type_id
    WHERE c.venue_id={venue_id} AND c.status='ACTIVE' AND st.status='ACTIVE'
    ORDER BY c.id,cs.sport_type_id LIMIT 1"""))
booking_days = int(sql(f"SELECT booking_days_ahead FROM s_venue WHERE id={venue_id}"))
court_name = "P6提醒测试场" + uuid.uuid4().hex[:8]
court_id = int(sql(f"""
    INSERT INTO s_court (venue_id,name,status) VALUES ({venue_id},'{court_name}','ACTIVE');
    SELECT LAST_INSERT_ID();
""").splitlines()[-1])
sql(f"INSERT INTO s_court_sport (court_id,sport_type_id) VALUES ({court_id},{sport_id})")
owner, owner_id = login("owner")
watcher, watcher_id = login("watcher")
stranger, _ = login("stranger")
admin_name = sql(f"SELECT username FROM s_admin_account WHERE venue_id={venue_id} AND role='VENUE_ADMIN' ORDER BY id LIMIT 1")
venue_admin = ok("/auth/admin/login", body={"username": admin_name,
    "password": os.environ.get("P6_VENUE_ADMIN_PASSWORD", "venue123")})["token"]

# 登记边界：当前窗口、紧接将开放的一天、重复幂等、本人隔离与撤回再登记。
baseline_slot = create_slot(TOMORROW, 18, 19, 0)
baseline = subscribe(watcher, "OPENING", TOMORROW, "18:00", "19:00")
assert baseline["availableNow"] is True and baseline["type"] == "OPENING", baseline
listed_baseline = next(row for row in ok("/mini/reminder-subscriptions", watcher) if row["id"] == baseline["id"])
assert listed_baseline["bookingDaysAhead"] == booking_days and listed_baseline["minBookingMinutes"] >= 0
assert subscribe(watcher, "OPENING", TOMORROW, "18:00", "19:00")["id"] == baseline["id"]
assert all(row["id"] != baseline["id"] for row in ok("/mini/reminder-subscriptions", stranger))
assert http("/mini/reminder-subscriptions")["code"] != 0
assert http(f"/mini/reminder-subscriptions/{baseline['id']}", stranger, method="DELETE")["code"] != 0
assert ok(f"/mini/reminder-subscriptions/{baseline['id']}", watcher, method="DELETE") is True
assert ok(f"/mini/reminder-subscriptions/{baseline['id']}", watcher, method="DELETE") is True
again = subscribe(watcher, "OPENING", TOMORROW, "18:00", "19:00")
assert again["id"] != baseline["id"] and again["availableNow"] is True
assert not messages(watcher, "OPENING_ALERT", again["id"]), "已有 OPEN 不能伪造新开售"

next_day = TODAY + timedelta(days=booking_days)
opening = subscribe(watcher, "OPENING", next_day, "19:00", "20:00")
assert opening["availableNow"] is False
assert http("/mini/reminder-subscriptions", watcher, {
    "venueId": venue_id, "sportTypeId": sport_id,
    "targetDate": str(next_day + timedelta(days=1)), "startTime": "19:00", "endTime": "20:00",
    "type": "OPENING",
})["code"] != 0
next_slot = create_slot(next_day, 19, 20, 0)
sql(f"UPDATE s_venue SET booking_days_ahead={booking_days+1} WHERE id={venue_id}")
opening_message = wait_for(lambda: messages(watcher, "OPENING_ALERT", opening["id"]), "开售提醒")[0]
assert "核销" not in opening_message["content"] and "00000000000" not in opening_message["content"]
assert opening_message["createdAt"].endswith("Z"), opening_message
opening_record = next(row for row in ok("/mini/reminder-subscriptions", watcher) if row["id"] == opening["id"])
assert opening_record["openingNotifiedAt"].endswith("Z"), opening_record
time.sleep(1)
assert len(messages(watcher, "OPENING_ALERT", opening["id"])) == 1, "重复调度不得重复生成开售消息"
assert sql(f"SELECT COUNT(*) FROM s_booking WHERE session_slot_id={next_slot}") == "0", "提醒不得创建预约"

# 空位：已有开放只引导、两次真实占用释放生成两个不同事件、人工开场和支付失败。
released_slot = create_slot(TOMORROW, 19, 20, 0)
first_booking = create_booking(owner, released_slot, 0)
assert first_booking["status"] == "CONFIRMED", first_booking
payment_slot = create_slot(TOMORROW, 20, 21, 6000)
manual_slot = create_slot(TOMORROW, 21, 22, 0, "CLOSED")
timeout_slot = create_slot(TOMORROW, 22, 23, 6000)
released = subscribe(watcher, "RELEASED", TOMORROW, "19:00", "23:00")
assert not messages(watcher, "SLOT_RELEASED_ALERT", released["id"]), "登记时 OPEN 不构成释放事件"
assert ok(f"/mini/bookings/{first_booking['id']}/cancel", owner, method="POST")["status"] == "CANCELLED"
first_release = wait_for(lambda: messages(watcher, "SLOT_RELEASED_ALERT", released["id"]), "取消释放提醒")[0]
assert first_release["businessId"] == released_slot and first_release["subscriptionId"] == released["id"]
assert len(messages(watcher, "SLOT_RELEASED_ALERT", released["id"])) == 1
second_booking = create_booking(owner, released_slot, 0)
assert second_booking["status"] == "CONFIRMED"
ok(f"/mini/bookings/{second_booking['id']}/cancel", owner, method="POST")
wait_for(lambda: len(messages(watcher, "SLOT_RELEASED_ALERT", released["id"])) == 2, "再次释放提醒")
assert sql(f"SELECT COUNT(*) FROM s_slot_release_event WHERE session_slot_id={released_slot}") == "2"

assert ok(f"/venue-admin/sessions/{manual_slot}/open", venue_admin, method="POST", admin=True) is True
wait_for(lambda: len(messages(watcher, "SLOT_RELEASED_ALERT", released["id"])) == 3, "人工重新开放提醒")
pending = create_booking(owner, payment_slot, 6000)
assert pending["status"] == "PENDING_PAYMENT", pending
payment = ok(f"/mini/bookings/{pending['id']}", owner)["payment"]
failed = ok("/mock/payments/notify", body={"businessNo": payment["businessNo"],
    "amountFen": 6000, "result": "FAILED", "signature": os.environ.get("P6_MOCK_SIGNATURE", "local-mock-signature")})
assert failed["status"] == "CANCELLED", failed
wait_for(lambda: len(messages(watcher, "SLOT_RELEASED_ALERT", released["id"])) == 4, "支付失败释放提醒")
second_failed_booking = create_booking(stranger, payment_slot, 6000)
assert second_failed_booking["status"] == "PENDING_PAYMENT"
second_payment = ok(f"/mini/bookings/{second_failed_booking['id']}", stranger)["payment"]
second_failed = ok("/mock/payments/notify", body={"businessNo": second_payment["businessNo"],
    "amountFen": 6000, "result": "FAILED", "signature": os.environ.get("P6_MOCK_SIGNATURE", "local-mock-signature")})
assert second_failed["status"] == "CANCELLED"
wait_for(lambda: len(messages(watcher, "SLOT_RELEASED_ALERT", released["id"])) == 5,
         "同一场次第二次支付失败释放提醒")
assert sql(f"SELECT COUNT(DISTINCT event_key) FROM s_slot_release_event WHERE session_slot_id={payment_slot}") == "2"
timeout_booking = create_booking(owner, timeout_slot, 6000)
assert timeout_booking["status"] == "PENDING_PAYMENT"
sql(f"UPDATE s_booking SET payment_expires_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE) WHERE id={timeout_booking['id']}")
wait_for(lambda: sql(f"SELECT status FROM s_booking WHERE id={timeout_booking['id']}") == "CANCELLED",
         "支付超时取消", seconds=100)
wait_for(lambda: len(messages(watcher, "SLOT_RELEASED_ALERT", released["id"])) == 6,
         "支付超时释放提醒")
assert sql(f"SELECT COUNT(*) FROM s_slot_release_event WHERE session_slot_id={timeout_slot}") == "1"

# 人工重新开放发生冲突时事务失败，不得产生空位事件。
conflict_slot = int(sql(f"""INSERT INTO s_session_slot (venue_id,court_id,sport_type_id,manual_override,
    slot_date,start_at,end_at,price_fen,status) VALUES ({venue_id},{court_id},{sport_id},1,
    '{TOMORROW}','{moment(TOMORROW,19,30)}','{moment(TOMORROW,20,30)}',0,'CLOSED');
    SELECT LAST_INSERT_ID();""").splitlines()[-1])
assert http(f"/venue-admin/sessions/{conflict_slot}/open", venue_admin, method="POST", admin=True)["code"] != 0
assert sql(f"SELECT COUNT(*) FROM s_slot_release_event WHERE session_slot_id={conflict_slot}") == "0"

# 消息打开时库存已被其他用户占用，公开比较应保留原状态和可选替代。
booked_again = create_booking(stranger, released_slot, 0)
assert booked_again["status"] == "CONFIRMED"
comparison = ok(f"/public/venues/{venue_id}/slot-comparison?date={TOMORROW}&sportTypeId={sport_id}"
    "&startTime=19:00&endTime=22:00")
assert any(row["id"] == released_slot and row["status"] == "BOOKED" for row in comparison)
assert any(row["id"] == payment_slot and row["status"] == "OPEN" for row in comparison)
assert sql(f"SELECT COUNT(*) FROM s_booking WHERE session_slot_id={manual_slot}") == "0"

# 同一消息重试：模拟已落库失败事实，仍以原通知 ID 成功收敛；成功落库后重启遗留不再调用 Adapter。
retry_id = opening_message["id"]
wait_for(lambda: sql(f"SELECT COUNT(*) FROM s_notification_delivery WHERE notification_id={retry_id} AND status='SUCCESS'") == "1",
         "首次 Mock 投递")
sql(f"""UPDATE s_notification SET status='PENDING_DELIVERY',delivery_next_attempt_at=UTC_TIMESTAMP(3)
    WHERE id={retry_id};
    UPDATE s_notification_delivery SET status='FAILED',failure_reason='本地模拟失败'
    WHERE notification_id={retry_id};""")
attempts_before = int(sql(f"SELECT COUNT(*) FROM s_notification_delivery_attempt WHERE notification_id={retry_id}"))
wait_for(lambda: sql(f"SELECT status FROM s_notification WHERE id={retry_id}") == "MOCK_SENT", "原消息 ID 重试")
assert int(sql(f"SELECT COUNT(*) FROM s_notification_delivery_attempt WHERE notification_id={retry_id}")) == attempts_before + 1
summary_attempts = int(sql(f"SELECT attempt_count FROM s_notification_delivery WHERE notification_id={retry_id}"))
sql(f"UPDATE s_notification SET status='DELIVERING',delivery_claimed_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 2 MINUTE) WHERE id={retry_id}")
wait_for(lambda: sql(f"SELECT status FROM s_notification WHERE id={retry_id}") == "MOCK_SENT", "重启遗留成功状态收敛")
assert int(sql(f"SELECT attempt_count FROM s_notification_delivery WHERE notification_id={retry_id}")) == summary_attempts

# 临近确认即时补发与本人详情权限，不依赖 30 秒轮询。
arrival_start = datetime.now(ZONE).replace(second=0, microsecond=0) + timedelta(minutes=90)
arrival_end = arrival_start + timedelta(hours=1)
arrival_slot = int(sql(f"""INSERT INTO s_session_slot (venue_id,court_id,sport_type_id,manual_override,
    slot_date,start_at,end_at,price_fen,status) VALUES ({venue_id},{court_id},{sport_id},1,
    '{arrival_start.date()}','{arrival_start.astimezone(timezone.utc):%Y-%m-%d %H:%M:%S}',
    '{arrival_end.astimezone(timezone.utc):%Y-%m-%d %H:%M:%S}',0,'OPEN'); SELECT LAST_INSERT_ID();""").splitlines()[-1])
arrival_booking = create_booking(owner, arrival_slot, 0)
assert arrival_booking["status"] == "CONFIRMED"
arrival_rows = messages(owner, "ARRIVAL_REMINDER")
arrival = next((row for row in arrival_rows if row["businessId"] == arrival_booking["id"]), None)
assert arrival is not None, "临近确认应在 HTTP 返回前提交后补记，不能只等调度"
assert "00000000000" not in arrival["content"] and "shortCode" not in arrival["content"]
assert http(f"/mini/bookings/{arrival_booking['id']}", watcher)["code"] != 0
arrival_detail = ok(f"/mini/bookings/{arrival_booking['id']}", owner)
assert arrival_detail["booking"]["id"] == arrival_booking["id"]
assert "entryNotice" in arrival_detail["venue"] and "latitude" in arrival_detail["venue"]
assert "longitude" in arrival_detail["venue"] and arrival_detail["credential"] is not None

# 撤回/注销后的待投递提醒保持历史但不得新调用 Mock。
held = subscribe(watcher, "RELEASED", TOMORROW, "22:00", "23:00")
synthetic = int(sql(f"""INSERT INTO s_notification (user_id,business_type,business_id,subscription_id,type,title,
    content,idempotency_key,status,delivery_next_attempt_at)
    VALUES ({watcher_id},'SESSION_SLOT',{timeout_slot},{held['id']},'SLOT_RELEASED_ALERT','本地待验','本地待验',
    'p6-held-{uuid.uuid4().hex}','PENDING_DELIVERY',DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 2 MINUTE));
    SELECT LAST_INSERT_ID();""").splitlines()[-1])
ok(f"/mini/reminder-subscriptions/{held['id']}", watcher, method="DELETE")
sql(f"UPDATE s_notification SET delivery_next_attempt_at=UTC_TIMESTAMP(3) WHERE id={synthetic}")
wait_for(lambda: sql(f"SELECT status FROM s_notification WHERE id={synthetic}") == "SKIPPED", "撤回后的投递跳过")
assert sql(f"SELECT COUNT(*) FROM s_notification_delivery_attempt WHERE notification_id={synthetic}") == "0"
held_again = subscribe(watcher, "RELEASED", TOMORROW, "22:00", "23:00")
deleted_pending = int(sql(f"""INSERT INTO s_notification (user_id,business_type,business_id,subscription_id,type,title,
    content,idempotency_key,status,delivery_next_attempt_at)
    VALUES ({watcher_id},'SESSION_SLOT',{timeout_slot},{held_again['id']},'SLOT_RELEASED_ALERT','本地待验','本地待验',
    'p6-deleted-{uuid.uuid4().hex}','PENDING_DELIVERY',DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 2 MINUTE));
    SELECT LAST_INSERT_ID();""").splitlines()[-1])
assert ok("/auth/mini/close-account", watcher, method="POST") is True
sql(f"UPDATE s_notification SET delivery_next_attempt_at=UTC_TIMESTAMP(3) WHERE id={deleted_pending}")
wait_for(lambda: sql(f"SELECT status FROM s_notification WHERE id={deleted_pending}") == "SKIPPED",
         "注销后的投递跳过")
assert sql(f"SELECT COUNT(*) FROM s_notification_delivery_attempt WHERE notification_id={deleted_pending}") == "0"
print("P6 reminders HTTP: registration, opening, release, retry, arrival, ownership and landing passed")
