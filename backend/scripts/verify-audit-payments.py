"""隔离 Mock 支付审计回归：F02/F04/F05/C02；只清理本次 UUID fixture。"""
import datetime as dt
import json
import os
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid
from zoneinfo import ZoneInfo

BASE = os.environ.get('P5_API_BASE', 'http://127.0.0.1:8091/api').rstrip('/')
CONTAINER = os.environ['P5_MYSQL_CONTAINER']
assert 'p5-' in CONTAINER or 'p6-' in CONTAINER, '仅允许隔离验收容器'
assert urllib.parse.urlparse(BASE).hostname in ('127.0.0.1', 'localhost'), '仅允许本地 Mock API'
RUN = 'audit-pay-' + uuid.uuid4().hex[:12]
DAY = dt.datetime.now(ZoneInfo('Asia/Shanghai')).date() + dt.timedelta(days=1)
users, courts, plans, employees, roles, sessions = [], [], [], [], [], []


def sql(statement):
    result = subprocess.run(['docker', 'exec', '-i', CONTAINER, 'mysql', '-uroot', '-preview_root',
                             '--default-character-set=utf8mb4', '-Ns', 'sports_booking'],
                            input=statement, text=True, capture_output=True)
    assert result.returncode == 0, '隔离 SQL 失败：' + result.stderr.splitlines()[-1]
    return result.stdout.strip()


def http(path, session=None, body=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if session:
        headers[session[0]] = session[1]
    request = urllib.request.Request(BASE + path,
        data=None if body is None else json.dumps(body).encode(), headers=headers,
        method=method or ('POST' if body is not None else 'GET'))
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.load(response)


def ok(path, session=None, body=None, method=None):
    status, response = http(path, session, body, method)
    assert status == 200 and response['code'] == 0, (path, status, response.get('msg'))
    return response['data']


def login(kind, label):
    if kind == 'mini':
        result = ok('/auth/mini/mock-login', body={'code': RUN + '-' + label})
        users.append(result['profile']['id'])
        session = ('X-Mini-Token', result['token'])
    else:
        result = ok('/auth/admin/login', body={'username': label, 'password': 'venue123'})
        session = ('X-Admin-Token', result['token'])
    sessions.append((kind, session))
    return session


def slot(hour):
    return ok('/venue-admin/sessions/create', venue, {
        'courtId': courts[0], 'sportTypeId': sport, 'date': str(DAY),
        'startTime': f'{hour:02d}:00', 'endTime': f'{hour+1:02d}:00', 'priceFen': 1000})['id']


def create_online(session, hour):
    slot_id = slot(hour)
    booking = ok('/mini/bookings/create', session, {'sessionSlotId': slot_id,
        'contactName': '本地Mock验收', 'contactPhone': '00000000000',
        'idempotencyKey': RUN + '-' + str(hour), 'expectedPriceFen': 1000})
    assert booking['status'] == 'PENDING_PAYMENT'
    detail = ok('/mini/bookings/' + str(booking['id']), session)
    return booking['id'], slot_id, detail['payment']['businessNo'], detail['composition']['externalAmountFen']


def notify(number, amount, result):
    return ok('/mock/payments/notify', body={'businessNo': number, 'amountFen': amount,
        'result': result, 'signature': 'local-mock-signature'})


def payment_fact(booking_id):
    return sql(f"SELECT b.status,b.payment_status,p.status,DATE_FORMAT(p.paid_at,'%Y-%m-%d %H:%i:%s.%f'),"
               f"(SELECT COUNT(*) FROM s_refund r WHERE r.booking_id=b.id) "
               f"FROM s_booking b JOIN s_payment p ON p.booking_id=b.id WHERE b.id={booking_id}")


def cleanup():
    # 先销毁本脚本令牌；既有管理员会话和其他验收 fixture 不受影响。
    for kind, session in reversed(sessions):
        ok('/auth/' + kind + '/logout', session, method='POST')
    # 响应丢失时也能用本次唯一标记找回自有行，绝不扫描或删除其他验收数据。
    for table, field, ids in (('s_user_account', 'mock_openid', users), ('s_court', 'name', courts),
                             ('s_recharge_plan', 'name', plans), ('s_employee', 'name', employees),
                             ('s_venue_role', 'name', roles)):
        condition = f"{field} IN ('mock:{RUN}-owner','mock:{RUN}-employee')" if table == 's_user_account' else f"{field}='{RUN}'"
        ids[:] = sorted(set(ids + [int(value) for value in sql(f'SELECT id FROM {table} WHERE {condition}').splitlines()]))
    uid = ','.join(map(str, users)) or '0'
    cid = ','.join(map(str, courts)) or '0'
    pid = ','.join(map(str, plans)) or '0'
    eid = ','.join(map(str, employees)) or '0'
    rid = ','.join(map(str, roles)) or '0'
    # 单事务内锁定通知并按 FK 从叶到根删除，仅命中本次新用户或新场地。
    sql(f"""START TRANSACTION;
        SELECT id FROM s_notification WHERE user_id IN ({uid}) OR booking_id IN
            (SELECT id FROM s_booking WHERE court_id IN ({cid})) FOR UPDATE;
        DELETE FROM s_notification_delivery_attempt WHERE notification_id IN
            (SELECT id FROM s_notification WHERE user_id IN ({uid}) OR booking_id IN
                (SELECT id FROM s_booking WHERE court_id IN ({cid})));
        DELETE FROM s_notification_delivery WHERE notification_id IN
            (SELECT id FROM s_notification WHERE user_id IN ({uid}) OR booking_id IN
                (SELECT id FROM s_booking WHERE court_id IN ({cid})));
        DELETE FROM s_notification WHERE user_id IN ({uid}) OR booking_id IN
            (SELECT id FROM s_booking WHERE court_id IN ({cid}));
        DELETE FROM s_user_action_event WHERE user_id IN ({uid});
        DELETE FROM s_member_balance_flow WHERE venue_customer_id IN
            (SELECT id FROM s_venue_customer WHERE user_id IN ({uid}));
        DELETE FROM s_booking_balance_hold WHERE booking_id IN
            (SELECT id FROM s_booking WHERE court_id IN ({cid}));
        DELETE FROM s_member_balance_lot WHERE venue_customer_id IN
            (SELECT id FROM s_venue_customer WHERE user_id IN ({uid}));
        DELETE FROM s_member_recharge WHERE venue_customer_id IN
            (SELECT id FROM s_venue_customer WHERE user_id IN ({uid}));
        DELETE FROM s_booking_payment_status_log WHERE booking_id IN
            (SELECT id FROM s_booking WHERE court_id IN ({cid}));
        DELETE FROM s_booking_reschedule_log WHERE booking_id IN
            (SELECT id FROM s_booking WHERE court_id IN ({cid}));
        DELETE FROM s_refund WHERE booking_id IN (SELECT id FROM s_booking WHERE court_id IN ({cid}));
        DELETE FROM s_payment WHERE booking_id IN (SELECT id FROM s_booking WHERE court_id IN ({cid}));
        UPDATE s_session_slot SET hold_booking_id=NULL,hold_expires_at=NULL WHERE court_id IN ({cid});
        DELETE FROM s_booking WHERE court_id IN ({cid});
        DELETE FROM s_slot_release_event WHERE session_slot_id IN
            (SELECT id FROM s_session_slot WHERE court_id IN ({cid}));
        DELETE FROM s_session_slot WHERE court_id IN ({cid});
        DELETE FROM s_court_sport WHERE court_id IN ({cid});
        DELETE FROM s_court WHERE id IN ({cid});
        DELETE FROM s_employee_role WHERE employee_id IN ({eid});
        DELETE FROM s_venue_role_permission WHERE role_id IN ({rid});
        DELETE FROM s_venue_role WHERE id IN ({rid});
        DELETE FROM s_employee WHERE id IN ({eid});
        DELETE FROM s_venue_customer WHERE user_id IN ({uid});
        DELETE FROM s_recharge_plan WHERE id IN ({pid});
        DELETE FROM s_user_account WHERE id IN ({uid});
        COMMIT;""")
    assert sql(f"SELECT COUNT(*) FROM s_user_account WHERE id IN ({uid})") == '0'
    assert sql(f"SELECT COUNT(*) FROM s_court WHERE id IN ({cid})") == '0'
    print('Fixture cleanup passed (FK-safe, scoped to this run)', flush=True)


try:
    venue = login('admin', 'venue1')
    other_venue = login('admin', 'venue2')
    owner = login('mini', 'owner')
    employee_user = login('mini', 'employee')
    venue_id = ok('/venue-admin/profile/detail', venue)['id']
    sport = ok('/venue-admin/sports/list', venue)[0]['id']
    courts.append(ok('/venue-admin/courts/create', venue,
        {'name': RUN, 'sportIds': [sport]})['id'])
    # 合法 API 创建、支付，再把专用预约置于待复核履约终态；不改历史业务数据。
    for hour, terminal in ((10, 'NO_SHOW'), (11, 'COMPLETED')):
        booking_id, slot_id, number, amount = create_online(owner, hour)
        ok(f'/mini/bookings/{booking_id}/mock-pay', owner, method='POST')
        sql(f"UPDATE s_booking SET status='{terminal}',start_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 HOUR),"
            f"end_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 HOUR) WHERE id={booking_id};"
            f"UPDATE s_payment SET paid_at='2026-09-07 10:30:00.123' WHERE booking_id={booking_id};")
        before = payment_fact(booking_id)
        for result in ('FAILED', 'SUCCESS', 'FAILED'):
            assert notify(number, amount, result)['status'] == terminal
            assert payment_fact(booking_id) == before, '履约终态或原支付事实被重放改写'
    print('F02 passed: NO_SHOW/COMPLETED success and failure replay preserve fulfillment and paidAt', flush=True)

    booking_id, slot_id, number, amount = create_online(owner, 12)
    ok(f'/mini/bookings/{booking_id}/mock-pay', owner, method='POST')
    ok(f'/mini/bookings/{booking_id}/cancel', owner, method='POST')
    sql(f"UPDATE s_payment SET paid_at='2026-09-07 10:30:00.123' WHERE booking_id={booking_id}")
    before = payment_fact(booking_id)
    assert '\tREFUNDED\tSUCCESS\t' in before
    for result in ('SUCCESS', 'FAILED', 'SUCCESS'):
        notify(number, amount, result)
        assert payment_fact(booking_id) == before, '取消后重放移动实收日或重复退款'
    print('F04 passed: cancelled/refunded replay preserves first paidAt and one refund', flush=True)

    employee = ok('/venue-admin/employees/create', venue, {'name': RUN})
    employees.append(employee['employeeId'])
    ok('/mini/staff/bind', employee_user, {'bindingCode': employee['bindingCode']})
    role = ok('/venue-admin/roles', venue, {'name': RUN, 'status': 'ACTIVE',
        'permissions': ['OFFLINE_PAYMENT_STATUS_EDIT']})
    roles.append(role['id'])
    ok(f'/venue-admin/employees/{employees[0]}/roles', venue, {'roleIds': roles}, method='PUT')
    ticket = ok('/auth/admin/web-login-tickets', method='POST')['ticket']
    ok(f'/staff/web-login-tickets/{ticket}/confirm', employee_user, {'venueId': venue_id})
    employee_login = ok(f'/auth/admin/web-login-tickets/{ticket}/consume', method='POST')
    assert employee_login['principal']['role'] == 'VENUE_EMPLOYEE'
    employee_session = ('X-Admin-Token', employee_login['token'])
    sessions.append(('admin', employee_session))
    offline = []
    for hour in (13, 14):
        offline.append(ok('/venue-admin/bookings/offline', venue,
            {'sessionSlotId': slot(hour), 'contactName': '本地Mock验收', 'contactPhone': '00000000000',
             'paymentStatus': 'UNPAID'})['id'])
    sql(f"UPDATE s_booking SET payment_status='UNRECORDED' WHERE id={offline[0]}")
    body = {'paymentStatus': 'PAID', 'reason': '本地Mock登记验收'}
    status, result = http(f'/venue-admin/bookings/{offline[0]}/payment-status', employee_session, body, 'PUT')
    assert status == 403 and result['code'] != 0
    assert sql(f'SELECT payment_status FROM s_booking WHERE id={offline[0]}') == 'UNRECORDED'
    assert ok(f'/venue-admin/bookings/{offline[0]}/payment-status', venue, body, 'PUT')['paymentStatus'] == 'PAID'
    assert ok(f'/venue-admin/bookings/{offline[1]}/payment-status', employee_session, body, 'PUT')['paymentStatus'] == 'PAID'
    print('F05 passed: fresh employee Web session rejects historical backfill; admin backfill and normal employee update work', flush=True)

    # 通过真实 Mock 充值建立 500 分实付余额，实际创建 1000 分组合支付并验证冻结释放。
    plan = ok('/venue-admin/recharge-plans', venue,
        {'name': RUN, 'paidAmountFen': 500, 'giftAmountFen': 0})
    plans.append(plan['id'])
    recharge = ok('/mini/recharges/create', owner,
        {'venueId': venue_id, 'planId': plan['id'], 'idempotencyKey': RUN})
    ok(f"/mini/recharges/{recharge['id']}/mock-pay", owner, method='POST')
    booking_id, slot_id, number, amount = create_online(owner, 15)
    assert amount == 500
    assert sql(f"SELECT status,amount_fen FROM s_booking_balance_hold WHERE booking_id={booking_id}") == 'FROZEN\t500'
    status, result = http(f'/mini/bookings/{booking_id}/cancel', employee_user, method='POST')
    assert status == 404 and result['code'] != 0
    cancelled = ok(f'/mini/bookings/{booking_id}/cancel', owner, method='POST')
    assert cancelled['status'] == 'CANCELLED' and cancelled['cancelledBy'] == 'USER'
    assert sql(f"SELECT status,COALESCE(hold_booking_id,'NULL') FROM s_session_slot WHERE id={slot_id}") == 'OPEN\tNULL'
    assert sql(f"SELECT status FROM s_booking_balance_hold WHERE booking_id={booking_id}") == 'RELEASED'
    assert sql(f"SELECT available_amount_fen,frozen_amount_fen FROM s_member_balance_lot WHERE venue_customer_id="
               f"(SELECT venue_customer_id FROM s_booking WHERE id={booking_id})") == '500\t0'
    repeated_status, repeated_result = http(f'/mini/bookings/{booking_id}/cancel', owner, method='POST')
    assert notify(number, amount, 'SUCCESS')['status'] == 'CANCELLED'
    assert notify(number, amount, 'SUCCESS')['status'] == 'CANCELLED'
    assert sql(f"SELECT COUNT(*),MAX(status) FROM s_refund WHERE booking_id={booking_id}") == '1\tSUCCESS'
    assert sql(f"SELECT COUNT(*) FROM s_member_balance_flow WHERE booking_id={booking_id} AND business_type='CONSUME'") == '0'
    assert sql(f"SELECT status,COALESCE(hold_booking_id,'NULL') FROM s_session_slot WHERE id={slot_id}") == 'OPEN\tNULL'
    print('C02 user passed: actual combination payment close releases hold/frozen funds; late success refunds once without reviving booking', flush=True)

    booking_id, slot_id, number, amount = create_online(owner, 16)
    before = sql(f"SELECT b.status,p.status FROM s_booking b JOIN s_payment p ON p.booking_id=b.id WHERE b.id={booking_id}")
    status, result = http(f'/venue-admin/bookings/{booking_id}/cancel', venue, {'reason': '  '})
    assert status == 400 and result['code'] != 0
    status, result = http(f'/venue-admin/bookings/{booking_id}/cancel', other_venue, {'reason': '本地Mock取消验收'})
    assert status == 404 and result['code'] != 0
    assert sql(f"SELECT b.status,p.status FROM s_booking b JOIN s_payment p ON p.booking_id=b.id WHERE b.id={booking_id}") == before
    reason = '本地Mock场馆取消验收'
    cancelled = ok(f'/venue-admin/bookings/{booking_id}/cancel', venue, {'reason': reason})
    assert cancelled['status'] == 'CANCELLED' and cancelled['cancelledBy'] == 'VENUE' and cancelled['cancelReason'] == reason
    assert sql(f"SELECT status,COALESCE(hold_booking_id,'NULL') FROM s_session_slot WHERE id={slot_id}") == 'OPEN\tNULL'
    print('C02 venue passed: blank reason and cross-venue calls have no side effects; actor/reason preserved', flush=True)
    assert repeated_status == 200 and repeated_result['data']['status'] == 'CANCELLED', '本人重复取消应返回已取消事实'
finally:
    cleanup()
print('PASS: F02/F04/F05/C02 HTTP regression and fixture cleanup completed', flush=True)
