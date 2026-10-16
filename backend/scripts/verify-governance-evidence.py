#!/usr/bin/env python3
"""隔离运行态案件证据回归；只清理本脚本创建的内容，不输出 Token。"""
import argparse
import json
import hashlib
from pathlib import Path
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--base', default='http://127.0.0.1:8091/api')
parser.add_argument('--mysql-container', required=True)
parser.add_argument('--upload-directory', help='隔离服务 app.upload.directory；仅删除本次唯一上传文件')
args = parser.parse_args()
assert 'runtime' in args.mysql_container or 'audit' in args.mysql_container, '只允许指定隔离验收容器'
assert urllib.parse.urlparse(args.base).hostname in ('127.0.0.1', 'localhost'), '只允许本地隔离接口'


def sql(statement):
    result = subprocess.run(['docker', 'exec', '-i', args.mysql_container, 'mysql', '-uroot',
                             '-preview_root', '--default-character-set=utf8mb4', '-Ns', 'sports_booking'],
                            input=statement, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def call(path, token=None, mini=False, body=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['X-Mini-Token' if mini else 'X-Admin-Token'] = token
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(args.base + path, data=data, headers=headers,
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
admin = ok('/auth/admin/login', body={'username': 'admin', 'password': 'admin123'})['token']
venue = ok('/auth/admin/login', body={'username': 'venue1', 'password': 'venue123'})['token']
author = ok('/auth/mini/mock-login', body={'code': 'evidence-author-' + suffix})
reporter = ok('/auth/mini/mock-login', body={'code': 'evidence-reporter-' + suffix})
user_id = author['profile']['id']
post_id = review_id = reply_id = comment_id = court_id = slot_id = booking_id = tag_id = upload_id = None
extra_targets = []
try:
    post_id = int(sql(f"INSERT INTO s_post(author_type,author_id,content_status) VALUES('USER',{user_id},'PUBLISHED'); SELECT LAST_INSERT_ID();"))
    revision1 = int(sql(f"INSERT INTO s_post_revision(post_id,revision_no,body,sport_type_id,moderation_status) VALUES({post_id},1,'本地 Mock 被举报原文',1,'APPROVED'); SELECT LAST_INSERT_ID();"))
    revision2 = int(sql(f"INSERT INTO s_post_revision(post_id,revision_no,body,sport_type_id,moderation_status) VALUES({post_id},2,'本地 Mock 后续编辑',1,'PENDING'); SELECT LAST_INSERT_ID();"))
    sql(f"UPDATE s_post SET current_revision_id={revision2},public_revision_id={revision1} WHERE id={post_id};")
    ok(f'/mini/content/reports?targetType=POST&targetId={post_id}&reasonCode=OTHER',
       token=reporter['token'], mini=True, method='POST')
    report_id = int(sql(f"SELECT id FROM s_content_report WHERE target_type='POST' AND target_id={post_id};"))
    detail = ok(f'/platform/content-governance/reports/{report_id}/evidence', token=admin)
    assert detail['evidenceRevisionId'] == revision1 and detail['currentRevisionId'] == revision2
    assert detail['evidenceSource'] == 'REPORT_SUBMISSION'
    assert any(item['body'] == '本地 Mock 被举报原文' for item in detail['revisions'])
    assert sql(f"SELECT legal_hold FROM s_post WHERE id={post_id};") == '1'
    print('PASS: 举报准确定位公开版本，后续编辑版本与提交证据分开；保留标记已设置')

    path = f'/platform/content-governance/reports/{report_id}/evidence'
    for token, mini in ((None, False), (venue, False), (reporter['token'], True)):
        status, _ = call(path, token=token, mini=mini)
        assert status in (401, 403), status
    print('PASS: 游客、场馆管理员、小程序用户均不能读取平台证据')

    ok(f'/platform/content-moderation/posts/{post_id}', token=admin,
       body={'revisionId': revision2, 'action': 'DELETE', 'reason': '本地 Mock 证据删除回归'})
    log_id = int(sql(f"SELECT MAX(id) FROM s_content_moderation_log WHERE content_type='POST' AND content_id={post_id};"))
    ok(f'/mini/content/appeals?moderationLogId={log_id}&body=' + urllib.parse.quote('本地 Mock 申诉'),
       token=author['token'], mini=True, method='POST')
    appeal_id = int(sql(f"SELECT id FROM s_content_appeal WHERE moderation_log_id={log_id};"))
    detail = ok(f'/platform/content-governance/appeals/{appeal_id}/evidence', token=admin)
    assert detail['currentStatus'] == 'DELETED' and detail['evidenceRevisionId'] == revision2
    assert detail['evidenceSource'] == 'MODERATION_DECISION'
    assert any(item['id'] == log_id and item['revisionId'] == revision2 for item in detail['decisions'])
    status, result = call(f'/mini/posts/{post_id}', token=reporter['token'], mini=True)
    assert status >= 400 and result['code'] != 0
    assert call(f'/platform/content-governance/targets/POST/{post_id}/release-hold', token=admin, method='POST')[0] == 409
    print('PASS: 删除对象申诉仍可查看处置版本、原文和日志；不恢复公开访问；待处理案件禁止解除保留')

    sql(f"UPDATE s_content_report SET evidence_revision_id=NULL WHERE id={report_id};")
    detail = ok(path, token=admin)
    assert detail['evidenceSource'] == 'LEGACY_UNRECORDED' and detail['evidenceRevisionId'] is None
    print('PASS: 旧举报不以当前版本伪造提交时证据')

    # 不存在真实文件的 PURGED 记录仅检验清理状态说明，不伪造媒体可读取。
    sql(f"INSERT INTO s_post_media(revision_id,position,media_type,storage_provider,object_key,mime_type,size_bytes,sha256,moderation_status,cleanup_status) VALUES({revision1},1,'IMAGE','LOCAL','mock-purged-{suffix}','image/jpeg',1,REPEAT('0',64),'APPROVED','PURGED');")
    detail = ok(path, token=admin)
    assert next(item for item in detail['revisions'] if item['revisionId'] == revision1)['purgedMediaCount'] == 1
    assert ok(f'/platform/content-media/POST/{revision1}', token=admin) == []
    print('PASS: 已清理媒体明确计数，不伪造可读取媒体')

    # 全部为本次独立夹具，不依赖 Demo 历史内容或已有预约。
    court_id = int(sql(f"INSERT INTO s_court(venue_id,name,status) VALUES(1,'Mock证据场地-{suffix}','ACTIVE'); SELECT LAST_INSERT_ID();"))
    slot_id = int(sql(f"INSERT INTO s_session_slot(venue_id,court_id,sport_type_id,slot_date,start_at,end_at,price_fen,status,manual_override) VALUES(1,{court_id},1,'2026-09-01','2026-09-01 10:00:00','2026-09-01 11:00:00',0,'BOOKED',1); SELECT LAST_INSERT_ID();"))
    booking_id = int(sql(f"INSERT INTO s_booking(booking_no,user_id,venue_id,court_id,sport_type_id,session_slot_id,source,contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,status,payment_status) VALUES('EVIDENCE-{suffix}',{user_id},1,{court_id},1,{slot_id},'ONLINE','本地 Mock','00000000000','本地 Mock','本地 Mock','羽毛球','2026-09-01 10:00:00','2026-09-01 11:00:00',0,'COMPLETED','PAID'); SELECT LAST_INSERT_ID();"))
    review_id = int(sql(f"INSERT INTO s_venue_review(booking_id,venue_id,user_id,sport_type_id,content_status) VALUES({booking_id},1,{user_id},1,'PUBLISHED'); SELECT LAST_INSERT_ID();"))
    extra_targets.append(('REVIEW', review_id))
    review_revision1 = int(sql(f"INSERT INTO s_venue_review_revision(review_id,revision_no,body,overall_score,court_score,environment_score,service_score,anonymous,moderation_status) VALUES({review_id},1,'本地 Mock 评价原文',5,4,3,2,TRUE,'APPROVED'); SELECT LAST_INSERT_ID();"))
    review_revision2 = int(sql(f"INSERT INTO s_venue_review_revision(review_id,revision_no,body,overall_score,court_score,environment_score,service_score,anonymous,moderation_status) VALUES({review_id},2,'本地 Mock 评价后续编辑',1,2,3,4,FALSE,'PENDING'); SELECT LAST_INSERT_ID();"))
    tag_name = 'Mock证据标签-' + suffix
    tag_id = int(sql(f"INSERT INTO s_review_tag(name,normalized_name,status) VALUES('{tag_name}','evidence-{suffix}','ACTIVE'); SELECT LAST_INSERT_ID();"))
    sql(f"INSERT INTO s_venue_review_tag_rel(revision_id,tag_id,position) VALUES({review_revision1},{tag_id},1); UPDATE s_venue_review SET current_revision_id={review_revision2},public_revision_id={review_revision1} WHERE id={review_id};")

    # 图片经真实上传 Adapter 写入，平台返回字节必须与上传的图片完全一致。
    image = (Path(__file__).resolve().parents[2] / 'miniapp/assets/venues/venue-1.jpg').read_bytes()
    boundary = 'evidence-' + suffix
    payload = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="mock-evidence.jpg"\r\nContent-Type: image/jpeg\r\n\r\n').encode() + image + f'\r\n--{boundary}--\r\n'.encode()
    request = urllib.request.Request(args.base + '/mini/content/uploads', data=payload,
        headers={'X-Mini-Token': author['token'], 'Content-Type': 'multipart/form-data; boundary=' + boundary}, method='POST')
    with urllib.request.urlopen(request) as response:
        uploaded = json.load(response)
    assert uploaded['code'] == 0
    upload_id = uploaded['data']['id']
    media_id = int(sql(f"INSERT INTO s_venue_review_media(revision_id,position,storage_provider,object_key,mime_type,size_bytes,sha256,moderation_status,cleanup_status) SELECT {review_revision1},1,'LOCAL',object_key,mime_type,size_bytes,sha256,'APPROVED','ACTIVE' FROM s_ugc_upload WHERE id={upload_id}; SELECT LAST_INSERT_ID();"))
    sql(f"UPDATE s_ugc_upload SET status='ATTACHED' WHERE id={upload_id};")

    reply_id = int(sql(f"INSERT INTO s_venue_review_reply(review_id,venue_id,content_status) VALUES({review_id},1,'PUBLISHED'); SELECT LAST_INSERT_ID();"))
    extra_targets.append(('REVIEW_REPLY', reply_id))
    reply_revision = int(sql(f"INSERT INTO s_venue_review_reply_revision(reply_id,revision_no,body,moderation_status) VALUES({reply_id},1,'本地 Mock 官方回复原文','APPROVED'); SELECT LAST_INSERT_ID();"))
    sql(f"UPDATE s_venue_review_reply SET current_revision_id={reply_revision},public_revision_id={reply_revision} WHERE id={reply_id};")
    comment_id = int(sql(f"INSERT INTO s_post_comment(post_id,author_user_id,content_status) VALUES({post_id},{user_id},'PUBLISHED'); SELECT LAST_INSERT_ID();"))
    extra_targets.append(('COMMENT', comment_id))
    comment_revision = int(sql(f"INSERT INTO s_post_comment_revision(comment_id,revision_no,body,moderation_status) VALUES({comment_id},1,'本地 Mock 评论原文','APPROVED'); SELECT LAST_INSERT_ID();"))
    sql(f"UPDATE s_post_comment SET current_revision_id={comment_revision},public_revision_id={comment_revision} WHERE id={comment_id};")

    def image_bytes(path, token, mini=False):
        headers = {'X-Mini-Token' if mini else 'X-Admin-Token': token}
        with urllib.request.urlopen(urllib.request.Request(args.base + path, headers=headers)) as response:
            return response.read()

    # 隐藏但未删除的本人媒体可读，其他用户和公开入口不得访问；平台始终受权限保护。
    sql(f"UPDATE s_venue_review SET content_status='HIDDEN' WHERE id={review_id};")
    assert hashlib.sha256(image_bytes(f'/mini/content/media/reviews/{media_id}', author['token'], mini=True)).digest() == hashlib.sha256(image).digest()
    assert call(f'/mini/content/media/reviews/{media_id}', token=reporter['token'], mini=True)[0] == 404
    assert call(f'/public/content/media/reviews/{media_id}')[0] == 404
    assert hashlib.sha256(image_bytes(f'/platform/content-media/REVIEW/file/{media_id}', admin)).digest() == hashlib.sha256(image).digest()
    sql(f"UPDATE s_venue_review SET content_status='PUBLISHED' WHERE id={review_id};")
    print('PASS: 非公开评价图片仅作者和平台能读取，其他用户及公开媒体入口拒绝')

    for target_type, target_id, table, source_revision, body_text in (
            ('REVIEW', review_id, 's_venue_review', review_revision1, '本地 Mock 评价原文'),
            ('REVIEW_REPLY', reply_id, 's_venue_review_reply', reply_revision, '本地 Mock 官方回复原文'),
            ('COMMENT', comment_id, 's_post_comment', comment_revision, '本地 Mock 评论原文')):
        ok(f'/mini/content/reports?targetType={target_type}&targetId={target_id}&reasonCode=OTHER',
           token=reporter['token'], mini=True, method='POST')
        case_id = int(sql(f"SELECT id FROM s_content_report WHERE target_type='{target_type}' AND target_id={target_id};"))
        # 独立夹具模拟删除终态，明确不在此回归中验证各业务删除命令。
        sql(f"UPDATE {table} SET content_status='DELETED',deleted_at=UTC_TIMESTAMP(3) WHERE id={target_id};")
        case_path = f'/platform/content-governance/reports/{case_id}/evidence'
        item = ok(case_path, token=admin)
        assert item['caseRecord']['targetType'] == target_type and item['currentStatus'] == 'DELETED'
        assert item['evidenceSource'] == 'REPORT_SUBMISSION' and item['evidenceRevisionId'] == source_revision
        source = next(row for row in item['revisions'] if row['revisionId'] == source_revision)
        assert source['body'] == body_text
        for token, mini in ((None, False), (venue, False), (reporter['token'], True)):
            assert call(case_path, token=token, mini=mini)[0] in (401, 403)
        if target_type == 'REVIEW':
            assert [source[key] for key in ('overallScore', 'courtScore', 'environmentScore', 'serviceScore')] == [5,4,3,2]
            assert source['anonymous'] is True and source['tags'] == tag_name and source['purgedMediaCount'] == 0
            assert item['currentRevisionId'] == review_revision2 and len(item['revisions']) == 2
            media = ok(f'/platform/content-media/REVIEW/{source_revision}', token=admin)
            assert len(media) == 1 and media[0]['id'] == media_id and media[0]['mediaType'] == 'IMAGE'
            file_path = media[0]['url']
            with urllib.request.urlopen(urllib.request.Request(args.base + file_path, headers={'X-Admin-Token': admin})) as response:
                assert hashlib.sha256(response.read()).digest() == hashlib.sha256(image).digest()
            for token, mini in ((None, False), (venue, False), (reporter['token'], True)):
                assert call(file_path, token=token, mini=mini)[0] in (401,403)
            assert call(f'/public/content/media/reviews/{media_id}')[0] == 404
            print('PASS: 删除评价的提交版本、不同后续版本、四项评分、匿名、标签和真实图片字节正确；媒体仅平台读取')
        else:
            assert source['overallScore'] is None and source['purgedMediaCount'] == 0
            print(f'PASS: {target_type} 自有删除对象历史原文、提交版本及平台权限正确')

    sql(f"UPDATE s_user_account SET contact_name='本地 Mock 联系人',phone='00000000000' WHERE id={user_id};")
    ok(f'/mini/content/reports?targetType=USER&targetId={user_id}&reasonCode=OTHER',
       token=reporter['token'], mini=True, method='POST')
    extra_targets.append(('USER', user_id))
    ok(f'/platform/content-governance/restrictions?userId={user_id}&capability=COMMENT&days=1&reason=' + urllib.parse.quote('本地 Mock 账号治理证据'), token=admin, method='POST')
    case_id = int(sql(f"SELECT id FROM s_content_report WHERE target_type='USER' AND target_id={user_id};"))
    item = ok(f'/platform/content-governance/reports/{case_id}/evidence', token=admin)
    assert item['evidenceSource'] == 'ACCOUNT_TARGET' and item['evidenceRevisionId'] is None
    assert item['currentStatus'] == 'ACTIVE' and item['revisions'] == []
    assert any(row['action'] == 'RESTRICT' for row in item['decisions'])
    encoded = json.dumps(item, ensure_ascii=False)
    for private_value in ('00000000000','本地 Mock 联系人','contactName','phone','memberCode','balance'):
        assert private_value not in encoded
    for token, mini in ((None, False), (venue, False), (reporter['token'], True)):
        assert call(f'/platform/content-governance/reports/{case_id}/evidence', token=token, mini=mini)[0] in (401,403)
    print('PASS: USER案件仅返回账号状态与治理处置，不暴露联系人、手机号或会员资金，平台权限受保护')
    assert ok('/auth/mini/close-account', token=author['token'], mini=True, method='POST') is True
    # 模拟遗留错误恢复状态，已注销作者也必须阻止媒体再次公开。
    sql(f"UPDATE s_venue_review SET content_status='PUBLISHED',deleted_at=NULL WHERE id={review_id};")
    assert call(f'/public/content/media/reviews/{media_id}')[0] == 404
    assert call(f'/mini/content/media/reviews/{media_id}', token=reporter['token'], mini=True)[0] == 404
    assert hashlib.sha256(image_bytes(f'/platform/content-media/REVIEW/file/{media_id}', admin)).digest() == hashlib.sha256(image).digest()
    item = ok(f'/platform/content-governance/reports/{case_id}/evidence', token=admin)
    assert item['currentStatus'] == 'DISABLED'
    print('PASS: 作者注销后即使遗留评价误恢复，图片也不公开；平台保留证据仍可读取，USER状态为DISABLED')

finally:
    # 只按本次自动生成 ID 清理，先案件/日志，再子内容、版本，最后预约和供给夹具。
    targets = extra_targets + ([('POST', post_id)] if post_id is not None else [])
    for target_type, target_id in targets:
        sql(f"DELETE a FROM s_content_appeal a JOIN s_content_moderation_log l ON l.id=a.moderation_log_id WHERE l.content_type='{target_type}' AND l.content_id={target_id}; DELETE FROM s_content_report WHERE target_type='{target_type}' AND target_id={target_id}; DELETE FROM s_content_moderation_log WHERE content_type='{target_type}' AND content_id={target_id}; DELETE FROM s_user_action_event WHERE target_type='{target_type}' AND target_id={target_id};")
    if comment_id is not None:
        sql(f"UPDATE s_post_comment SET current_revision_id=NULL,public_revision_id=NULL WHERE id={comment_id}; DELETE FROM s_post_comment_revision WHERE comment_id={comment_id}; DELETE FROM s_post_comment WHERE id={comment_id};")
    if reply_id is not None:
        sql(f"UPDATE s_venue_review_reply SET current_revision_id=NULL,public_revision_id=NULL WHERE id={reply_id}; DELETE FROM s_venue_review_reply_revision WHERE reply_id={reply_id}; DELETE FROM s_venue_review_reply WHERE id={reply_id};")
    if review_id is not None:
        sql(f"DELETE m FROM s_venue_review_media m JOIN s_venue_review_revision r ON r.id=m.revision_id WHERE r.review_id={review_id}; DELETE t FROM s_venue_review_tag_rel t JOIN s_venue_review_revision r ON r.id=t.revision_id WHERE r.review_id={review_id}; UPDATE s_venue_review SET current_revision_id=NULL,public_revision_id=NULL WHERE id={review_id}; DELETE FROM s_venue_review_revision WHERE review_id={review_id}; DELETE FROM s_venue_review WHERE id={review_id};")
    if tag_id is not None:
        sql(f'DELETE FROM s_review_tag WHERE id={tag_id};')
    if booking_id is not None:
        sql(f'DELETE FROM s_booking WHERE id={booking_id};')
    if slot_id is not None:
        sql(f'DELETE FROM s_session_slot WHERE id={slot_id};')
    if court_id is not None:
        sql(f'DELETE FROM s_court WHERE id={court_id};')
    if post_id is not None:
        sql(f"DELETE FROM s_post_media WHERE revision_id IN (SELECT id FROM s_post_revision WHERE post_id={post_id}); UPDATE s_post SET current_revision_id=NULL,public_revision_id=NULL WHERE id={post_id}; DELETE FROM s_post_revision WHERE post_id={post_id}; DELETE FROM s_post WHERE id={post_id};")
    sql(f"DELETE FROM s_user_content_restriction WHERE user_id={user_id}; UPDATE s_user_account SET contact_name=NULL,phone=NULL,legal_hold=FALSE WHERE id={user_id};")
    if upload_id is not None:
        object_key = sql(f'SELECT object_key FROM s_ugc_upload WHERE id={upload_id};')
        if args.upload_directory:
            media_root = Path(args.upload_directory).resolve().parent / 'ugc-media'
            media_root = media_root.resolve()
            target = (media_root / object_key).resolve()
            assert object_key and Path(object_key).name == object_key and target.parent == media_root
            assert sql(f"SELECT (SELECT COUNT(*) FROM s_post_media WHERE object_key='{object_key}')+(SELECT COUNT(*) FROM s_venue_review_media WHERE object_key='{object_key}');") == '0'
            target.unlink(missing_ok=True)
            sql(f'DELETE FROM s_ugc_upload WHERE id={upload_id};')
            print('CLEANUP: 本次唯一上传文件及暂存行已精确清理')
        else:
            sql(f"UPDATE s_ugc_upload SET status='STAGED',expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 DAY WHERE id={upload_id};")
            print('CLEANUP: 本次上传过期交原清理任务')
    print('CLEANUP: 本次案件/内容/标签/预约/场地夹具按精确ID清理；注销Mock账号保留')
