"""仅在隔离验收库验证一次性绑定、真实解绑、旧员工会话及票据撤权。"""
import concurrent.futures
import base64
import hashlib
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get('P5_API_BASE', 'http://127.0.0.1:8091/api')
CONTAINER = os.environ['P5_MYSQL_CONTAINER']
assert 'p5-' in CONTAINER or 'p6-' in CONTAINER, '仅允许隔离验收容器'
PREFIX = 'F01-' + uuid.uuid4().hex[:12]


def sql(statement):
    return subprocess.run(['docker', 'exec', '-i', CONTAINER, 'mysql', '-uroot', '-preview_root',
                           '--default-character-set=utf8mb4', '-Ns', 'sports_booking'],
                          input=statement, text=True, capture_output=True, check=True).stdout.strip()


def http(path, body=None, token=None, mini=False, method=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['X-Mini-Token' if mini else 'X-Admin-Token'] = token
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


def denied(path, **kwargs):
    status, result = http(path, **kwargs)
    assert status in (400, 401, 403, 409) and result['code'] != 0, (status, result.get('message'))


def ticket(user_token):
    value = ok('/auth/admin/web-login-tickets', method='POST')['ticket']
    ok('/staff/web-login-tickets/' + value + '/confirm', body={'venueId': 1},
       token=user_token, mini=True)
    return value


employee_ids, user_ids, mini_tokens, admin_tokens = [], [], [], []
role_id = None
blocker = None
try:
    admin = ok('/auth/admin/login', body={'username': 'venue1', 'password': 'venue123'})['token']
    admin_tokens.append(admin)
    for suffix in ('A', 'B'):
        login = ok('/auth/mini/mock-login', body={'code': PREFIX + suffix})
        mini_tokens.append(login['token'])
        user_ids.append(login['profile']['id'])
    role_id = ok('/venue-admin/roles', token=admin, body={
        'name': PREFIX, 'status': 'ACTIVE', 'permissions': ['VENUE_PROFILE_VIEW']})['id']
    binding = ok('/venue-admin/employees/create', token=admin, body={'name': PREFIX})
    employee_id = binding['employeeId']
    employee_ids.append(employee_id)
    ok(f'/venue-admin/employees/{employee_id}/roles', token=admin,
       body={'roleIds': [role_id]}, method='PUT')

    # 两个真实HTTP请求同时消费同一码，只允许一名用户绑定成功。
    def bind(token):
        return http('/mini/staff/bind', body={'bindingCode': binding['bindingCode']}, token=token, mini=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(bind, mini_tokens))
    successes = [i for i, (status, result) in enumerate(results) if status == 200 and result['code'] == 0]
    assert len(successes) == 1, [(status, result['code']) for status, result in results]
    winner = successes[0]
    loser = 1 - winner
    assert sql(f'SELECT user_id, binding_code_hash IS NULL, binding_expires_at IS NULL '
               f'FROM s_employee WHERE id={employee_id}') == f'{user_ids[winner]}\t1\t1'
    denied('/mini/staff/bind', body={'bindingCode': binding['bindingCode']}, token=mini_tokens[loser], mini=True)
    first_ticket = ticket(mini_tokens[winner])
    staff = ok('/auth/admin/web-login-tickets/' + first_ticket + '/consume', method='POST')['token']
    admin_tokens.append(staff)
    ok('/venue-admin/profile/detail', token=staff)
    pending_ticket = ticket(mini_tokens[winner])

    # 保留一个尚未消费的绑定码：解绑必须同时清用户、摘要、到期时间。
    unused_code = ok(f'/venue-admin/employees/{employee_id}/binding-code', token=admin, method='POST')['bindingCode']
    ok(f'/venue-admin/employees/{employee_id}/binding', token=admin, method='DELETE')
    assert sql(f'SELECT user_id IS NULL, binding_code_hash IS NULL, binding_expires_at IS NULL '
               f'FROM s_employee WHERE id={employee_id}') == '1\t1\t1'
    denied('/venue-admin/profile/detail', token=staff)
    denied('/mini/staff/bind', body={'bindingCode': unused_code}, token=mini_tokens[loser], mini=True)
    assert not ok('/staff/venues', token=mini_tokens[winner], mini=True)

    # 同一员工重绑给另一用户后，旧会话和旧确认票据也不能恢复访问。
    replacement = ok(f'/venue-admin/employees/{employee_id}/binding-code', token=admin, method='POST')['bindingCode']
    ok('/mini/staff/bind', body={'bindingCode': replacement}, token=mini_tokens[loser], mini=True)
    denied('/venue-admin/profile/detail', token=staff)
    denied('/auth/admin/web-login-tickets/' + pending_ticket + '/consume', method='POST')
    second_ticket = ticket(mini_tokens[loser])
    new_staff = ok('/auth/admin/web-login-tickets/' + second_ticket + '/consume', method='POST')['token']
    admin_tokens.append(new_staff)
    ok('/venue-admin/profile/detail', token=new_staff)
    second = ok('/venue-admin/employees/create', token=admin, body={'name': PREFIX})
    employee_ids.append(second['employeeId'])
    denied('/mini/staff/bind', body={'bindingCode': second['bindingCode']}, token=mini_tokens[loser], mini=True)
    # 同馆重复身份拒绝时不消费码，另一尚未绑定该馆的用户仍可正常使用。
    ok('/mini/staff/bind', body={'bindingCode': second['bindingCode']}, token=mini_tokens[winner], mini=True)

    # 认证仍读到旧ACTIVE，但绑定等待注销共用User锁，提交后必须拒绝且不消费码。
    racing_user = ok('/auth/mini/mock-login', body={'code': PREFIX + 'C'})
    mini_tokens.append(racing_user['token'])
    racing_id = racing_user['profile']['id']
    user_ids.append(racing_id)
    racing_employee = ok('/venue-admin/employees/create', token=admin, body={'name': PREFIX})
    employee_ids.append(racing_employee['employeeId'])
    before_binding = sql(f'SELECT user_id IS NULL,binding_code_hash,binding_expires_at '
                         f'FROM s_employee WHERE id={racing_employee["employeeId"]}')
    command = ['docker', 'exec', '-i', CONTAINER, 'mysql', '-uroot', '-preview_root',
               '--default-character-set=utf8mb4', '--unbuffered', '-Ns', 'sports_booking']
    statement = (f"START TRANSACTION; UPDATE s_user_account SET status='DISABLED',deleted_at=UTC_TIMESTAMP(3) "
                 f"WHERE id={racing_id} AND mock_openid='mock:{PREFIX}C'; SELECT id FROM s_user_account "
                 f'WHERE id={racing_id}; SELECT SLEEP(2); COMMIT;')
    blocker = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, text=True)
    blocker.stdin.write(statement)
    blocker.stdin.close()
    blocker.stdin = None
    assert blocker.stdout.readline().strip() == str(racing_id), '未取得专属用户行锁'
    started = time.monotonic()
    status, result = http('/mini/staff/bind', body={'bindingCode': racing_employee['bindingCode']},
                          token=racing_user['token'], mini=True)
    assert status == 401 and result['code'] != 0, (status, result.get('message'))
    assert time.monotonic() - started >= 1, '未覆盖认证后等待User锁的注销并发'
    assert blocker.wait(timeout=10) == 0
    assert sql(f'SELECT user_id IS NULL,binding_code_hash,binding_expires_at '
               f'FROM s_employee WHERE id={racing_employee["employeeId"]}') == before_binding

    # 模拟旧坏数据：员工仍ACTIVE且user_id未解绑，已停用用户的现有Web token也必须失效。
    sql(f"UPDATE s_user_account SET status='DISABLED',deleted_at=UTC_TIMESTAMP(3) "
        f"WHERE id={user_ids[loser]} AND mock_openid LIKE 'mock:{PREFIX}%';")
    assert sql(f'SELECT user_id,status FROM s_employee WHERE id={employee_id}') == f'{user_ids[loser]}\tACTIVE'
    denied('/venue-admin/profile/detail', token=new_staff)
finally:
    if blocker is not None and blocker.poll() is None:
        blocker.wait(timeout=10)
    # 仅删除本次随机前缀创建的无业务关联fixture，不触碰现有员工、角色及用户。
    for token in admin_tokens:
        http('/auth/admin/logout', token=token, method='POST')
    for token in mini_tokens:
        http('/auth/mini/logout', token=token, mini=True, method='POST')
    # 已失效身份无法通过认证进入logout，仅精确删除本次创建的会话摘要，不扫描Redis。
    def session_key(kind, token):
        digest = base64.urlsafe_b64encode(hashlib.sha256(token.encode()).digest()).decode().rstrip('=')
        return 'session:' + kind + ':' + digest
    session_keys = [session_key('admin', token) for token in admin_tokens]
    session_keys += [session_key('mini', token) for token in mini_tokens]
    if session_keys:
        result = subprocess.run(['docker', 'exec', CONTAINER.replace('-mysql-', '-redis-'),
                                 'redis-cli', 'DEL', *session_keys], text=True, capture_output=True)
        if result.returncode:
            raise RuntimeError(result.stderr)
    for employee_id in employee_ids:
        sql(f'DELETE FROM s_employee_role WHERE employee_id={employee_id}; '
            f'DELETE FROM s_employee WHERE id={employee_id} AND name="{PREFIX}";')
    if role_id is not None:
        sql(f'DELETE FROM s_venue_role WHERE id={role_id} AND name="{PREFIX}";')
    for user_id in user_ids:
        sql(f'DELETE FROM s_user_account WHERE id={user_id} AND mock_openid LIKE "mock:{PREFIX}%";')

print('PASS F01: one-time binding, NULL clearing, revocation, user-closure race and disabled-user legacy binding; fixtures cleaned')
