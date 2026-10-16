#!/usr/bin/env python3
"""本地 Mock 未付充值退出闭环，保留测试终态用于审计，不输出身份令牌。"""
import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--base', default='http://127.0.0.1:8091/api')
args = parser.parse_args()
assert urllib.parse.urlparse(args.base).hostname in ('127.0.0.1', 'localhost')


def call(path, token=None, body=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['X-Mini-Token'] = token
    request = urllib.request.Request(args.base + path, headers=headers,
                                     data=None if body is None else json.dumps(body).encode(),
                                     method=method or ('POST' if body is not None else 'GET'))
    try:
        with urllib.request.urlopen(request) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def ok(path, **kwargs):
    status, result = call(path, **kwargs)
    assert status == 200 and result['code'] == 0, (path, status, result.get('msg'))
    return result['data']


suffix = uuid.uuid4().hex[:12]
owner = ok('/auth/mini/mock-login', body={'code': 'recharge-close-owner-' + suffix})['token']
other = ok('/auth/mini/mock-login', body={'code': 'recharge-close-other-' + suffix})['token']
plans = ok('/mini/recharges/plans?venueId=1', token=owner)
assert plans, '隔离环境需一个启用的本地充值档位'
recharge = ok('/mini/recharges/create', token=owner,
              body={'venueId': 1, 'planId': plans[0]['id'], 'idempotencyKey': 'close-' + suffix})
assert recharge['status'] == 'PENDING'
recharge_id = recharge['id']
rows = ok('/mini/recharges?venueId=1', token=owner)
assert any(row['id'] == recharge_id and row['status'] == 'PENDING' for row in rows)
print('PASS: 用户可重新查询本人未付充值')
assert call('/auth/mini/close-account', token=owner, method='POST')[0] == 409
for path in (f'/mini/recharges/{recharge_id}', f'/mini/recharges/{recharge_id}/cancel'):
    assert call(path, token=other, method='POST' if path.endswith('/cancel') else 'GET')[0] == 404
print('PASS: 未付充值仍阻止注销，他人不能查看或关闭')
closed = ok(f'/mini/recharges/{recharge_id}/cancel', token=owner, method='POST')
assert closed['status'] == 'CLOSED'
assert ok(f'/mini/recharges/{recharge_id}/cancel', token=owner, method='POST')['status'] == 'CLOSED'
account = ok('/mini/memberships/1/balance-flows', token=owner)
assert account['paidBalanceFen'] == 0 and account['giftBalanceFen'] == 0 and account['flows'] == []
assert call(f'/mini/recharges/{recharge_id}/mock-pay', token=owner, method='POST')[0] == 409
status, result = call('/mock/recharges/notify', body={'businessNo': recharge['rechargeNo'],
                     'amountFen': recharge['paidAmountFen'], 'result': 'SUCCESS',
                     'signature': os.environ.get('MOCK_SIGNATURE', 'local-mock-signature')})
assert status == 409 and result['code'] != 0
assert ok(f'/mini/recharges/{recharge_id}', token=owner)['status'] == 'CLOSED'
assert ok('/mini/memberships/1/balance-flows', token=owner)['flows'] == []
print('PASS: 已关闭充值幂等，Mock-pay及矛盾迟到成功通知均拒绝，余额与流水不复活')
assert ok('/auth/mini/close-account', token=owner, method='POST') is True
print('PASS: 零余额用户关闭未付充值后可以注销')
print('EVIDENCE: 隔离账号及 CLOSED 充值记录保留作为终态证据')
