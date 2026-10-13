"""P6 顾客经营隔离 HTTP 验收；复用调用者提供的 MySQL 和 HTTP 环境。"""
import concurrent.futures
import datetime as dt
import json
import os
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid
from zoneinfo import ZoneInfo

base = os.environ.get('P5_API_BASE', 'http://127.0.0.1:8091/api')
container = os.environ['P5_MYSQL_CONTAINER']
venue_id = int(os.environ.get('P6_CUSTOMER_VENUE_ID', '1'))
other_venue_id = int(os.environ.get('P6_CUSTOMER_OTHER_VENUE_ID', '2'))
fixture = {'court': 993001, 'other_court': 993002, 'employee': 994001, 'other_employee': 994002,
           'users': [990001, 990002, 990003, 990004],
           'customers': [991001, 991002, 991003, 991004, 991005],
           'slots': list(range(992001, 992011)), 'bookings': list(range(992001, 992011))}


def sql(query):
    result = subprocess.run(['docker', 'exec', '-i', container, 'mysql', '-uroot', '-preview_root',
                             '--default-character-set=utf8mb4', '-Ns', 'sports_booking'],
                            input=query, text=True, capture_output=True)
    if result.returncode:
        # mysql stderr contains the database diagnostic, but not the submitted SQL.
        # Keep the latter (which includes fixture identities) out of failure output.
        diagnostic = result.stderr.strip() or f'mysql exited with status {result.returncode}'
        raise RuntimeError(f'fixture SQL failed: {diagnostic}')
    return result.stdout.strip()


def http(path, token=None, body=None, method=None, token_header='X-Admin-Token'):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers[token_header] = token
    request = urllib.request.Request(base + path,
        data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=25)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return json.load(response)


def ok(path, token=None, body=None, method=None, token_header='X-Admin-Token'):
    response = http(path, token, body, method, token_header)
    assert response['code'] == 0, (path, response['msg'])
    return response['data']


def login_admin(username, password):
    return ok('/auth/admin/login', body={'username': username, 'password': password})['token']


def mock_login(code):
    return ok('/auth/mini/mock-login', body={'code': code})['token']


role_id = None
fixture_tag_ids = []
mini_staff_openid = ""
mini_reader_openid = ""
role_name = ""


def build_fixtures(sport_id):
    user_rows = []
    for index, user_id in enumerate(fixture['users'], 1):
        user_rows.append(f"INSERT INTO s_user_account(id,mock_openid,contact_name,phone,status) "
                         f"VALUES({user_id},'p6-customer-{index}','测试顾客{index}','00000000000','ACTIVE');")
    customer_rows = [
        (fixture['customers'][0], venue_id, fixture['users'][0], 'P6A0001'),
        (fixture['customers'][1], venue_id, fixture['users'][1], 'P6A0002'),
        (fixture['customers'][2], venue_id, fixture['users'][2], 'P6A0003'),
        (fixture['customers'][3], venue_id, fixture['users'][3], 'P6A0004'),
        (fixture['customers'][4], other_venue_id, fixture['users'][0], 'P6B0001'),
    ]
    for customer_id, owner, user_id, member_code in customer_rows:
        user_rows.append(f"INSERT INTO s_venue_customer(id,venue_id,user_id,status,member_code) "
                         f"VALUES({customer_id},{owner},{user_id},'CUSTOMER','{member_code}');")
    user_rows.extend([
        f"INSERT INTO s_court(id,venue_id,name,status) VALUES({fixture['court']},{venue_id},'P6验收场','ACTIVE');",
        f"INSERT INTO s_court_sport(court_id,sport_type_id) VALUES({fixture['court']},{sport_id});",
        f"INSERT INTO s_court(id,venue_id,name,status) VALUES({fixture['other_court']},{other_venue_id},'P6异馆验收场','ACTIVE');",
        f"INSERT INTO s_court_sport(court_id,sport_type_id) VALUES({fixture['other_court']},{sport_id});",
        f"INSERT INTO s_employee(id,venue_id,name,status) VALUES({fixture['employee']},{venue_id},'P6验收员工','ACTIVE');",
        f"INSERT INTO s_employee(id,venue_id,name,status) VALUES({fixture['other_employee']},{other_venue_id},'P6异馆验收员工','ACTIVE');",
    ])

    sessions = [
        (992001, venue_id, fixture['court'], 31),  # Customer A: last online completion at 31 days.
        (992002, venue_id, fixture['court'], 60),  # Customer A: second valid booking.
        (992003, venue_id, fixture['court'], 40),  # Customer B: old slot but recent completion (29 days).
        (992004, venue_id, fixture['court'], 40),  # Customer C: old online completion.
        (992005, venue_id, fixture['court'], 100), # Customer D: offline fulfillment is untracked.
        (992006, other_venue_id, fixture['other_court'], 90), # Shared user at another venue.
        (992007, venue_id, fixture['court'], 5),   # Customer A: cancellation/pending slot.
        (992008, venue_id, fixture['court'], 0),   # Customer C: future confirmed booking.
        (992010, venue_id, fixture['court'], 0),   # Customer A: post-followup rebooking.
    ]
    for slot_id, owner, court_id, days_ago in sessions:
        future_days = {992008: 2, 992010: 3}
        base_expr = (f"DATE_ADD(UTC_TIMESTAMP(3),INTERVAL {future_days[slot_id]} DAY)"
                     if slot_id in future_days else f"DATE_SUB(UTC_TIMESTAMP(3),INTERVAL {days_ago} DAY)")
        # Distinct minute offsets prevent same-court fixtures on the same day from
        # colliding with the production unique key (court_id, start_at, end_at).
        start_expr = f"DATE_ADD({base_expr},INTERVAL {slot_id - 992000} MINUTE)"
        user_rows.append(
            f"INSERT INTO s_session_slot(id,venue_id,court_id,sport_type_id,slot_date,start_at,end_at,price_fen,status) "
            f"VALUES({slot_id},{owner},{court_id},{sport_id},"
            f"DATE(CONVERT_TZ({start_expr},'+00:00','+08:00')),{start_expr},"
            f"DATE_ADD({start_expr},INTERVAL 1 HOUR),1000,'BOOKED');")

    def booking(booking_id, slot_id, customer_index, owner, source, status, payment, completed_days=None):
        start_expr = f"(SELECT start_at FROM s_session_slot WHERE id={slot_id})"
        if completed_days is None:
            completed_expr = "NULL"
        elif completed_days == 30:
            completed_expr = "DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 30 DAY) + INTERVAL 1 MINUTE"
        else:
            completed_expr = f"DATE_SUB(UTC_TIMESTAMP(3),INTERVAL {completed_days} DAY)"
        user_id = fixture['users'][customer_index]
        customer_id = (fixture['customers'][customer_index] if owner == venue_id
                       else fixture['customers'][4])
        court_id = fixture['court'] if owner == venue_id else fixture['other_court']
        employee_id = fixture['employee'] if owner == venue_id else fixture['other_employee']
        ext_amount = 1000 if source == 'ONLINE' else 0
        user_rows.append(
            "INSERT INTO s_booking(id,booking_no,user_id,venue_customer_id,venue_id,court_id,sport_type_id,session_slot_id,"
            "source,contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,"
            "external_amount_fen,status,payment_status,completed_at) VALUES "
            f"({booking_id},'P6-CUSTOMER-{booking_id}',{user_id},{customer_id},{owner},{court_id},{sport_id},{slot_id},"
            f"'{source}','测试顾客','00000000000','测试场馆','P6验收场','测试运动',{start_expr},"
            f"DATE_ADD({start_expr},INTERVAL 1 HOUR),1000,{ext_amount},'{status}','{payment}',{completed_expr});")
        if source == 'ONLINE' and status == 'COMPLETED':
            user_rows.append(f"INSERT INTO s_verification(booking_id,employee_id,method,verified_at) "
                             f"VALUES({booking_id},{employee_id},'CODE',{completed_expr});")

    booking(992001, 992001, 0, venue_id, 'ONLINE', 'COMPLETED', 'PAID', 31)
    booking(992002, 992002, 0, venue_id, 'ONLINE', 'COMPLETED', 'PAID', 60)
    booking(992003, 992003, 1, venue_id, 'ONLINE', 'COMPLETED', 'PAID', 30)
    booking(992004, 992004, 2, venue_id, 'ONLINE', 'COMPLETED', 'PAID', 40)
    booking(992005, 992005, 3, venue_id, 'OFFLINE', 'ENDED_UNTRACKED', 'UNRECORDED')
    booking(992006, 992006, 0, other_venue_id, 'ONLINE', 'COMPLETED', 'PAID', 90)
    booking(992007, 992007, 0, venue_id, 'ONLINE', 'CANCELLED', 'REFUNDED')
    booking(992008, 992008, 2, venue_id, 'ONLINE', 'CONFIRMED', 'PAID')
    booking(992009, 992007, 0, venue_id, 'ONLINE', 'PENDING_PAYMENT', 'UNPAID')
    sql('\n'.join(user_rows))


def cleanup():
    ids = fixture['customers']
    sql(f"DELETE FROM s_venue_customer_followup WHERE customer_id IN ({','.join(map(str, ids))});"
        f"DELETE FROM s_venue_customer_tag_audit WHERE tag_id IN ({','.join(map(str, fixture_tag_ids)) or '0'}) "
        f"OR customer_id IN ({','.join(map(str, ids))});"
        f"DELETE FROM s_venue_customer_tag_relation WHERE customer_id IN ({','.join(map(str, ids))});"
        f"DELETE FROM s_venue_customer_tag WHERE id IN ({','.join(map(str, fixture_tag_ids)) or '0'});"
        f"DELETE FROM s_verification WHERE booking_id BETWEEN 992001 AND 992009;"
        f"DELETE FROM s_booking WHERE id BETWEEN 992001 AND 992010;"
        f"DELETE FROM s_session_slot WHERE id BETWEEN 992001 AND 992010;"
        f"DELETE FROM s_court_sport WHERE court_id IN ({fixture['court']},{fixture['other_court']});"
        f"DELETE FROM s_court WHERE id IN ({fixture['court']},{fixture['other_court']});"
        f"DELETE FROM s_venue_customer WHERE id IN ({','.join(map(str, ids))});"
        f"DELETE FROM s_venue_role WHERE id={role_id or 0} AND venue_id={venue_id};"
        f"DELETE FROM s_employee WHERE id IN ({fixture['employee']},{fixture['other_employee']});"
        f"DELETE FROM s_user_account WHERE id IN ({','.join(map(str, fixture['users']))});"
        f"DELETE FROM s_user_account WHERE mock_openid IN ('{mini_staff_openid}','{mini_reader_openid}');")



suffix = uuid.uuid4().hex[:8]
role_name = 'P6顾客经营角色-' + suffix
mini_staff_openid = 'p6-customer-staff-' + suffix
mini_reader_openid = 'p6-customer-' + suffix
admin_token = login_admin('venue1', 'venue123')
second_venue_token = login_admin('venue2', 'venue123')
platform_token = login_admin('admin', 'admin123')
mini_token = mock_login(mini_reader_openid)

try:
    sport_id = ok('/mini/sports', mini_token, token_header='X-Mini-Token')[0]['id']
    build_fixtures(sport_id)
    customer_id = fixture['customers'][0]

    empty_tag = http('/venue-admin/customer-tags', admin_token, {}, 'POST')
    empty_followup = http('/venue-admin/customer-followups', admin_token, {}, 'POST')
    empty_update = http('/venue-admin/customer-followups/7', admin_token, {}, 'PATCH')
    assert all(response['code'] != 0 and response['msg'] == '参数不正确'
               for response in (empty_tag, empty_followup, empty_update)), (empty_tag, empty_followup, empty_update)

    customers = ok(f'/venue-admin/customer-operations?segment=30_DAY_NO_REBOOK', admin_token)
    fixture_rows = [row for row in customers if row['customerId'] in fixture['customers']]
    assert [row['customerId'] for row in fixture_rows] == [fixture['customers'][0]], fixture_rows
    customer = fixture_rows[0]
    assert customer['validBookingCount'] == 2, customer
    assert customer['futureConfirmedCount'] == 0, customer
    assert customer['latestVerifiedAt'], customer
    assert customer['lastValidBookingAt'] and customer['lastValidBookingSource'] == 'ONLINE', customer
    assert customer['usualHours'], customer
    assert customer['tags'] == [], customer
    detail = ok(f"/venue-admin/customer-operations/{fixture['customers'][0]}", admin_token)
    assert detail['validBookingCount'] == 2 and detail['usualHours'] == customer['usualHours'], detail
    all_customer_rows = ok('/venue-admin/customer-operations', admin_token)
    offline_only = next(row for row in all_customer_rows if row['customerId'] == fixture['customers'][3])
    assert offline_only['validBookingCount'] == 1 and offline_only['lastCompletedAt'] is None
    assert offline_only['latestVerifiedAt'] is None and offline_only['tags'] == []
    assert offline_only['lastValidBookingSource'] == 'OFFLINE', offline_only
    assert http(f"/venue-admin/customer-operations/{fixture['customers'][0]}", second_venue_token)['code'] != 0
    assert http(f"/venue-admin/customers/{customer_id}/tags/1", second_venue_token, method='POST')['code'] != 0
    assert http('/venue-admin/customer-operations', platform_token)['code'] != 0

    tag_a_name = f'P6-{suffix}-新手,低峰'
    tag_b_name = f'P6-{suffix}-AA\"约球\"'
    tag_a = ok('/venue-admin/customer-tags', admin_token, {'name': tag_a_name}, 'POST')
    fixture_tag_ids.append(tag_a['id'])
    tag_b = ok('/venue-admin/customer-tags', admin_token, {'name': tag_b_name}, 'POST')
    fixture_tag_ids.append(tag_b['id'])
    assert tag_a['createdByName'] and tag_a['createdAt'].endswith('Z'), tag_a
    assert sql(f"SELECT COUNT(*) FROM s_venue_customer_tag WHERE id={tag_a['id']} AND created_by_admin_id=(SELECT id FROM s_admin_account WHERE username='venue1') AND created_by_employee_id IS NULL") == '1'
    duplicate = http('/venue-admin/customer-tags', admin_token, {'name': tag_a_name}, 'POST')
    assert duplicate['code'] != 0
    ok(f'/venue-admin/customers/{customer_id}/tags/{tag_a["id"]}', admin_token, method='POST')
    ok(f'/venue-admin/customers/{customer_id}/tags/{tag_a["id"]}', admin_token, method='POST')
    ok(f'/venue-admin/customers/{customer_id}/tags/{tag_b["id"]}', admin_token, method='POST')
    detail = ok(f'/venue-admin/customer-operations/{customer_id}', admin_token)
    assert detail['validBookingCount'] == 2 and set(detail['tags']) == {tag_a['name'], tag_b['name']}, detail
    assert sql(f"SELECT COUNT(*) FROM s_venue_customer_tag_audit WHERE customer_id={customer_id} AND action='TAG_ASSIGNED'") == '2'
    assert http('/venue-admin/members/' + str(customer_id) + '/balance-flows', admin_token)['code'] == 0

    # 客户端不能伪造来源；也不能给不在当前分组的本馆顾客建立回访。
    bogus_source = http('/venue-admin/customer-followups', admin_token,
        {'customerId': customer_id, 'purpose': '30_DAY_NO_REBOOK', 'sourceGroup': '任意来源'}, 'POST')
    assert bogus_source['code'] != 0
    ineligible = http('/venue-admin/customer-followups', admin_token,
        {'customerId': fixture['customers'][1], 'purpose': '30_DAY_NO_REBOOK', 'sourceGroup': '30_DAY_NO_REBOOK'}, 'POST')
    assert ineligible['code'] != 0

    # 同一客户同一目的并发建待办仅允许一条 PENDING。
    followup_payload = {'customerId': customer_id, 'purpose': '30_DAY_NO_REBOOK',
                        'sourceGroup': '30_DAY_NO_REBOOK'}
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        responses = list(executor.map(lambda _: http('/venue-admin/customer-followups', admin_token,
            followup_payload, 'POST'), range(2)))
    assert sum(response['code'] == 0 for response in responses) == 1, responses
    followup = next(response['data'] for response in responses if response['code'] == 0)
    assert followup['createdByName'] and followup['createdAt'].endswith('Z'), followup
    assert sql(f"SELECT COUNT(*) FROM s_venue_customer_followup WHERE venue_id={venue_id} "
               f"AND customer_id={customer_id} AND status='PENDING'") == '1'

    # 预约在回访处理前创建，但对应场次在处理时间之后；指标应按场次时间计入。
    # created_at 用 UTC 表示以便可与 handled_at 比较；生产代码可能写本地 LocalDateTime。
    sql(f"INSERT INTO s_booking(id,booking_no,user_id,venue_customer_id,venue_id,court_id,sport_type_id,session_slot_id,"
        f"source,contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,"
        f"external_amount_fen,status,payment_status,created_at) SELECT 992010,'P6-CUSTOMER-992010',vc.user_id,vc.id,{venue_id},"
        f"{fixture['court']},{sport_id},992010,'ONLINE','测试顾客','00000000000','测试场馆','P6验收场','测试运动',"
        f"ss.start_at,ss.end_at,1000,1000,'CONFIRMED','PAID',DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 MINUTE) "
        f"FROM s_session_slot ss JOIN s_venue_customer vc ON vc.id={customer_id} WHERE ss.id=992010;")

    # 并发状态更新最多只有一个请求能离开 PENDING。
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        updates = list(executor.map(lambda status: http(f"/venue-admin/customer-followups/{followup['id']}",
            admin_token, {'status': status, 'note': status}, 'PATCH'), ['COMPLETED', 'NOT_NEEDED']))
    assert sum(response['code'] == 0 for response in updates) == 1, updates
    handled = ok('/venue-admin/customer-followups?status=COMPLETED', admin_token) + \
              ok('/venue-admin/customer-followups?status=NOT_NEEDED', admin_token)
    first = next(row for row in handled if row['id'] == followup['id'])
    assert first['handledByName'] and first['handledAt'].endswith('Z') and first['note'], first

    # 暂时撤掉未来确认 fixture，重新满足分组，独立验证终态历史不占 PENDING 唯一槽位。
    sql("UPDATE s_booking SET status='CANCELLED' WHERE id=992010")
    second_followup = ok('/venue-admin/customer-followups', admin_token, followup_payload, 'POST')
    assert second_followup['id'] != followup['id']
    ok(f"/venue-admin/customer-followups/{second_followup['id']}", admin_token,
       {'status': 'COMPLETED', 'note': '再次完成'}, 'PATCH')
    sql("UPDATE s_booking SET status='CONFIRMED' WHERE id=992010")

    # 这里统计的是处理后日期的有效预约场次（按场次开始时间），不是处理后创建的预约。
    assert sql(f"SELECT COUNT(*) FROM s_booking b JOIN s_venue_customer_followup f "
               f"ON f.customer_id=b.venue_customer_id WHERE b.id=992010 AND b.created_at<f.handled_at "
               f"AND b.start_at>f.handled_at AND f.customer_id={customer_id}") == '2'
    after_handle = [row for row in ok('/venue-admin/customer-followups', admin_token)
                    if row['customerId'] == customer_id]
    assert len(after_handle) == 2 and all(row['rebookedAfterHandled'] is True for row in after_handle), after_handle
    assert all(row['rebookedAfterHandledSource'] == 'ONLINE' and row['rebookedAfterHandledAt'].endswith('Z') for row in after_handle), after_handle
    assert all(row['rebookedAfterHandledAt'] > row['handledAt'] for row in after_handle), after_handle
    latest_operation = ok(f'/venue-admin/customer-operations/{customer_id}', admin_token)
    assert latest_operation['lastValidBookingSource'] == 'ONLINE' and latest_operation['futureConfirmedCount'] == 1

    # 管理员 actor 与员工 actor 分开存储；撤掉权限后下一请求拒绝。
    employee = ok('/venue-admin/employees/create', admin_token, {'name': 'P6顾客经营员工'}, 'POST')
    employee_id = employee['employeeId']
    role_id = None
    mini_bind_token = mock_login(mini_staff_openid)
    ok('/mini/staff/bind', mini_bind_token, {'bindingCode': employee['bindingCode']}, 'POST', 'X-Mini-Token')
    role = ok('/venue-admin/roles', admin_token, {'name': role_name, 'status': 'ACTIVE',
        'permissions': ['CUSTOMER_VIEW', 'CUSTOMER_OPERATIONS_EDIT']}, 'POST')
    role_id = role['id']
    ok(f'/venue-admin/employees/{employee_id}/roles', admin_token, {'roleIds': [role_id]}, 'PUT')
    ticket = ok('/auth/admin/web-login-tickets', method='POST')['ticket']
    ok(f'/staff/web-login-tickets/{ticket}/confirm', mini_bind_token, {'venueId': venue_id}, 'POST', 'X-Mini-Token')
    employee_session = ok(f'/auth/admin/web-login-tickets/{ticket}/consume', method='POST')
    employee_token = employee_session['token']
    ok('/venue-admin/customer-operations', employee_token)
    employee_tag = ok('/venue-admin/customer-tags', employee_token, {'name': f'P6-{suffix}-员工标签'}, 'POST')
    fixture_tag_ids.append(employee_tag['id'])
    ok(f'/venue-admin/customers/{customer_id}/tags/{employee_tag["id"]}', employee_token, method='POST')
    assert sql(f"SELECT COUNT(*) FROM s_venue_customer_tag_audit WHERE tag_id={employee_tag['id']} "
               f"AND actor_employee_id={employee_id} AND actor_admin_id IS NULL") == '2'
    ok(f'/venue-admin/employees/{employee_id}/roles', admin_token, {'roleIds': []}, 'PUT')
    assert http('/venue-admin/customer-operations', employee_token)['code'] != 0
    assert http('/venue-admin/customer-tags', employee_token, {'name': '越权标签'}, 'POST')['code'] != 0

    # 删除已关联标签会留下关系移除和标签删除的姓名/时间审计。
    ok(f'/venue-admin/customer-tags/{tag_a["id"]}', admin_token, method='DELETE')
    assert sql(f"SELECT COUNT(*) FROM s_venue_customer_tag_audit WHERE tag_id={tag_a['id']} "
               f"AND action IN ('UNASSIGNED_BY_TAG_DELETE','TAG_DELETED')") == '2'
    assert sql(f"SELECT COUNT(*) FROM s_venue_customer_tag_relation WHERE tag_id={tag_a['id']}") == '0'

    print('P6 customers: venue isolation, 30-day boundary, completed/verification facts, join-safe counts, tags/audit, pending-only followups, concurrency, actor identity and immediate revoke passed')
finally:
    cleanup()
