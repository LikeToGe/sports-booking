# 由隔离运行脚本 source，使用该脚本建立的临时账号、容器和端口。
base='http://127.0.0.1:8091/api'
mini_json() {
  curl -sS -X "$1" "$base$2" -H "X-Mini-Token: $mini_token" \
    -H 'Content-Type: application/json' -d "${3:-null}"
}
other_json() {
  curl -sS -X "$1" "$base$2" -H "X-Mini-Token: $other_token" \
    -H 'Content-Type: application/json' -d "${3:-null}"
}
admin_json() {
  curl -sS -X "$1" "$base$2" -H "X-Admin-Token: $platform_token" \
    -H 'Content-Type: application/json' -d "${3:-null}"
}
wait_detection() {
  local pending
  for attempt in {1..20}; do
    pending=$(docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
      -e "SELECT COUNT(*) FROM s_content_detection_job WHERE status='QUEUED'")
    [[ "$pending" == 0 ]] && return
    sleep 1
  done
  print -u2 '媒体检测队列未收敛'
  return 1
}

other_login=$(curl -fsS -X POST "$base/auth/mini/mock-login" -H 'Content-Type: application/json' \
  -d '{"code":"p4-runtime-participant"}')
other_token=$(print -r -- "$other_login" | jq -er '.data.token')
other_user_id=$(print -r -- "$other_login" | jq -er '.data.profile.id')
other_json POST /mini/content/agreement '{"rulesVersion":"1","privacyVersion":"1"}' \
  | jq -e '.data.accepted == true' >/dev/null

upload_id=$(curl -fsS -X POST "$base/mini/content/uploads" -H "X-Mini-Token: $mini_token" \
  -F "file=@${backend_dir}/../miniapp/assets/venues/venue-1.jpg;type=image/jpeg" \
  | jq -er '.data | select(.mimeType == "image/jpeg" and (.sha256 | length) == 64) | .id')
other_json POST /mini/posts/create "{\"body\":\"不能绑定他人上传\",\"sportTypeId\":1,\"uploadIds\":[$upload_id]}" \
  | jq -e '.code != 0' >/dev/null
media_post=$(mini_json POST /mini/posts/create \
  "{\"body\":\"有图动态\",\"sportTypeId\":1,\"uploadIds\":[$upload_id]}")
media_post_id=$(print -r -- "$media_post" | jq -er '.data.id')
media_revision_id=$(print -r -- "$media_post" | jq -er '.data.revisionId')
media_id=$(print -r -- "$media_post" | jq -er '.data.media[0].id')
other_json GET "/mini/posts/$media_post_id" | jq -e '.code != 0' >/dev/null
wait_detection
admin_json POST "/platform/content-moderation/posts/$media_post_id" \
  "{\"revisionId\":$media_revision_id,\"action\":\"APPROVE\",\"reason\":\"图片审核通过\"}" \
  | jq -e '.data == true' >/dev/null
curl -fsS -H "X-Mini-Token: $other_token" "$base/mini/content/media/posts/$media_id" >/dev/null
mini_json PUT "/mini/posts/$media_post_id" '{"body":"只修改文字保留图片","sportTypeId":1}' \
  | jq -e '.data.media | length == 1' >/dev/null
media_revision_id=$(admin_json GET /platform/content-moderation/posts/list \
  | jq -er --argjson id "$media_post_id" '.data[] | select(.reviewId == $id) | .revisionId')
admin_json POST "/platform/content-moderation/posts/$media_post_id" \
  "{\"revisionId\":$media_revision_id,\"action\":\"APPROVE\",\"reason\":\"修改审核通过\"}" \
  | jq -e '.data == true' >/dev/null

# 评论默认待审；只允许同动态一级父评论；保留旧版和删除占位。
comment_id=$(other_json POST "/mini/posts/$media_post_id/comments" '{"body":"第一条评论"}' | jq -er '.data.id')
comment_revision_id=$(admin_json GET /platform/content-moderation/comments/list \
  | jq -er --argjson id "$comment_id" '.data[] | select(.reviewId == $id) | .revisionId')
mini_json GET "/mini/posts/$media_post_id/comments" | jq -e '.data == []' >/dev/null
admin_json POST "/platform/content-moderation/comments/$comment_id" \
  "{\"revisionId\":$comment_revision_id,\"action\":\"APPROVE\",\"reason\":\"评论通过\"}" \
  | jq -e '.data == true' >/dev/null
other_json PUT "/mini/posts/comments/$comment_id" '{"body":"修改后评论"}' | jq -e '.code == 0' >/dev/null
mini_json GET "/mini/posts/$media_post_id/comments" | jq -e '.data[0].body == "第一条评论"' >/dev/null
reply_comment_id=$(mini_json POST "/mini/posts/$media_post_id/comments" \
  "{\"body\":\"一级回复\",\"parentCommentId\":$comment_id}" | jq -er '.data.id')
reply_comment_revision=$(admin_json GET /platform/content-moderation/comments/list \
  | jq -er --argjson id "$reply_comment_id" '.data[] | select(.reviewId == $id) | .revisionId')
admin_json POST "/platform/content-moderation/comments/$reply_comment_id" \
  "{\"revisionId\":$reply_comment_revision,\"action\":\"APPROVE\",\"reason\":\"回复通过\"}" \
  | jq -e '.data == true' >/dev/null
other_json POST "/mini/posts/$media_post_id/comments" \
  "{\"body\":\"不允许第三层\",\"parentCommentId\":$reply_comment_id}" | jq -e '.code != 0' >/dev/null
mini_json DELETE "/mini/posts/comments/$comment_id" | jq -e '.code != 0' >/dev/null
other_json DELETE "/mini/posts/comments/$comment_id" | jq -e '.data == true' >/dev/null
mini_json GET "/mini/posts/$media_post_id/comments" \
  | jq -e --argjson id "$comment_id" '.data | any(.id == $id and .body == "评论已删除")' >/dev/null
other_json POST "/mini/posts/$media_post_id/likes?liked=true" | jq -e '.data == 1' >/dev/null
other_json POST "/mini/posts/$media_post_id/likes?liked=true" | jq -e '.data == 1' >/dev/null
other_json POST "/mini/posts/$media_post_id/likes?liked=false" | jq -e '.data == 0' >/dev/null
mini_json POST "/mini/posts/blocks/$other_user_id" | jq -e '.data == true' >/dev/null
other_json GET "/mini/posts/$media_post_id" | jq -e '.code != 0' >/dev/null
curl -sS -H "X-Mini-Token: $other_token" "$base/mini/content/media/posts/$media_id" \
  | jq -e '.code != 0' >/dev/null
mini_json DELETE "/mini/posts/blocks/$other_user_id" | jq -e '.data == true' >/dev/null

# 举报/申诉归属与保留，处置后的媒体退出公开、到期且解除保留后清理。
other_json POST "/mini/content/reports?targetType=POST&targetId=$media_post_id&reasonCode=SPAM" \
  | jq -e '.data == true' >/dev/null
other_json POST "/mini/content/reports?targetType=POST&targetId=$media_post_id&reasonCode=SPAM" \
  | jq -e '.code != 0' >/dev/null
report_id=$(admin_json GET /platform/content-governance/reports \
  | jq -er --argjson id "$media_post_id" '.data[] | select(.targetId == $id) | .id')
admin_json POST "/platform/content-moderation/posts/$media_post_id" \
  "{\"revisionId\":$media_revision_id,\"action\":\"HIDE\",\"reason\":\"测试申诉\"}" \
  | jq -e '.data == true' >/dev/null
log_id=$(mini_json GET /mini/content/decisions \
  | jq -er --argjson id "$media_post_id" '.data[] | select(.targetId == $id and .action == "HIDE") | .id')
other_json POST "/mini/content/appeals?moderationLogId=$log_id&body=appeal" | jq -e '.code != 0' >/dev/null
mini_json POST "/mini/content/appeals?moderationLogId=$log_id&body=appeal" | jq -e '.data == true' >/dev/null
mini_json POST "/mini/content/appeals?moderationLogId=$log_id&body=duplicate" | jq -e '.code != 0' >/dev/null
appeal_id=$(admin_json GET /platform/content-governance/appeals | jq -er '.data[0].id')
admin_json POST "/platform/content-governance/appeals/$appeal_id/decide?status=REVERSED&reason=restore" \
  | jq -e '.data == true' >/dev/null
other_json GET "/mini/posts/$media_post_id" | jq -e '.code == 0' >/dev/null
mini_json DELETE "/mini/posts/$media_post_id" | jq -e '.data == true' >/dev/null
docker exec "$mysql_container" mysql -uroot -preview_root sports_booking -e \
  "UPDATE s_post_media m JOIN s_post_revision r ON r.id=m.revision_id SET m.purge_after=UTC_TIMESTAMP(3)-INTERVAL 1 DAY WHERE r.post_id=$media_post_id"
sleep 2
remaining=$(docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
  -e "SELECT COUNT(*) FROM s_post_media m JOIN s_post_revision r ON r.id=m.revision_id WHERE r.post_id=$media_post_id AND cleanup_status='PURGED'")
[[ "$remaining" == 0 ]] || { print -u2 '保留中的对象被清理'; return 1; }
admin_json POST "/platform/content-governance/targets/POST/$media_post_id/release-hold" | jq -e '.code != 0' >/dev/null
admin_json POST "/platform/content-governance/reports/$report_id/resolve?status=RESOLVED&resolution=resolved" \
  | jq -e '.data == true' >/dev/null
admin_json POST "/platform/content-governance/targets/POST/$media_post_id/release-hold" | jq -e '.data == true' >/dev/null
for attempt in {1..15}; do
  remaining=$(docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
    -e "SELECT COUNT(*) FROM s_post_media m JOIN s_post_revision r ON r.id=m.revision_id WHERE r.post_id=$media_post_id AND cleanup_status!='PURGED'")
  [[ "$remaining" == 0 ]] && break
  sleep 1
done
[[ "$remaining" == 0 ]] || { print -u2 '解除保留后的到期对象未清理'; return 1; }
print 'P4 community: media ownership, detection, versions, comments, likes, blocks, appeals, and retention passed'

# 找搭子只绑定本人预约，成员隐私和容量在真实事务内裁决。
docker exec -i "$mysql_container" mysql -uroot -preview_root sports_booking <<SQL
INSERT INTO s_session_slot (id,venue_id,court_id,sport_type_id,slot_date,start_at,end_at,price_fen,status)
SELECT 999998,v.id,c.id,cs.sport_type_id,DATE(UTC_TIMESTAMP()+INTERVAL 5 DAY),
       UTC_TIMESTAMP()+INTERVAL 5 DAY,UTC_TIMESTAMP()+INTERVAL 5 DAY+INTERVAL 1 HOUR,6000,'BOOKED'
FROM s_venue v JOIN s_court c ON c.venue_id=v.id JOIN s_court_sport cs ON cs.court_id=c.id
ORDER BY v.id,c.id,cs.sport_type_id LIMIT 1;
INSERT INTO s_booking (id,booking_no,user_id,venue_id,court_id,sport_type_id,session_slot_id,source,
 contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,
 external_amount_fen,service_fee_rate_basis_points,status,payment_status)
SELECT 999998,'P4-RUNTIME-RECRUITMENT',${mini_user_id},s.venue_id,s.court_id,s.sport_type_id,s.id,'ONLINE',
 '本地测试','00000000000',v.name,c.name,st.name,s.start_at,s.end_at,6000,6000,0,'CONFIRMED','PAID'
FROM s_session_slot s JOIN s_venue v ON v.id=s.venue_id JOIN s_court c ON c.id=s.court_id
JOIN s_sport_type st ON st.id=s.sport_type_id WHERE s.id=999998;
SQL
recruit_post_body=$(docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
  -e "SELECT JSON_OBJECT('body','测试找搭子','sportTypeId',sport_type_id,'venueId',venue_id,'bookingId',id) FROM s_booking WHERE id=999998")
recruit_post=$(mini_json POST /mini/posts/create "$recruit_post_body")
recruit_post_id=$(print -r -- "$recruit_post" | jq -er '.data.id')
recruit_revision_id=$(print -r -- "$recruit_post" | jq -er '.data.revisionId')
admin_json POST "/platform/content-moderation/posts/$recruit_post_id" \
  "{\"revisionId\":$recruit_revision_id,\"action\":\"APPROVE\",\"reason\":\"招募动态通过\"}" \
  | jq -e '.data == true' >/dev/null
recruitment_id=$(mini_json POST "/mini/posts/$recruit_post_id/recruitment" \
  '{"bookingId":999998,"level":"CASUAL","maxMembers":1,"feeMode":"FREE","aaAmountFen":0}' | jq -er '.data.id')
other_json GET "/mini/posts/$recruit_post_id/recruitment" \
  | jq -e '.data.courtName == null and .data.members == []' >/dev/null
other_json GET "/mini/posts/$recruit_post_id" | jq -e '.data.bookingId == null' >/dev/null
mini_json PUT "/mini/posts/$recruit_post_id/comments-enabled?enabled=false" | jq -e '.code != 0' >/dev/null
other_json POST "/mini/posts/recruitments/$recruitment_id/apply" | jq -e '.data == true' >/dev/null
other_json POST "/mini/posts/recruitments/$recruitment_id/apply" | jq -e '.data == true' >/dev/null
mini_json POST "/mini/posts/recruitments/$recruitment_id/members/$other_user_id/decide?approve=true" \
  | jq -e '.data == true' >/dev/null
mini_json POST "/mini/posts/recruitments/$recruitment_id/members/$other_user_id/decide?approve=true" \
  | jq -e '.data == true' >/dev/null
other_json GET "/mini/posts/$recruit_post_id/recruitment" \
  | jq -e '.data.status == "FULL" and .data.approvedCount == 1 and .data.courtName != null' >/dev/null
other_json POST "/mini/posts/recruitments/$recruitment_id/leave" | jq -e '.data == true' >/dev/null
mini_json GET "/mini/posts/$recruit_post_id/recruitment" | jq -e '.data.status == "RECRUITING"' >/dev/null
other_json POST "/mini/posts/recruitments/$recruitment_id/apply" | jq -e '.data == true' >/dev/null
mini_json POST "/mini/posts/recruitments/$recruitment_id/members/$other_user_id/decide?approve=true" \
  | jq -e '.data == true' >/dev/null
mini_json POST /auth/mini/close-account | jq -e '.code != 0' >/dev/null

# 注销必须拒绝冻结余额；清零后隐去内容并使旧 Token 与同身份登录失效。
other_post=$(other_json POST /mini/posts/create '{"body":"注销前的动态","sportTypeId":1}')
other_post_id=$(print -r -- "$other_post" | jq -er '.data.id')
other_revision_id=$(print -r -- "$other_post" | jq -er '.data.revisionId')
admin_json POST "/platform/content-moderation/posts/$other_post_id" \
  "{\"revisionId\":$other_revision_id,\"action\":\"APPROVE\",\"reason\":\"测试公开\"}" \
  | jq -e '.data == true' >/dev/null
docker exec -i "$mysql_container" mysql -uroot -preview_root sports_booking <<SQL
INSERT INTO s_venue_customer (id,venue_id,user_id,status,member_code)
VALUES (999997,1,${other_user_id},'CUSTOMER','P4CLOSETEST');
INSERT INTO s_member_recharge (id,recharge_no,venue_customer_id,plan_name,paid_amount_fen,gift_amount_fen,channel,status)
VALUES (999997,'P4-CLOSE-BALANCE',999997,'测试充值',100,0,'MOCK','SUCCESS');
INSERT INTO s_member_balance_lot (venue_customer_id,source_recharge_id,type,original_amount_fen,
 available_amount_fen,frozen_amount_fen,status)
VALUES (999997,999997,'PAID',100,0,100,'ACTIVE');
SQL
other_json POST /auth/mini/close-account | jq -e '.code != 0' >/dev/null
docker exec "$mysql_container" mysql -uroot -preview_root sports_booking \
  -e 'UPDATE s_member_balance_lot SET frozen_amount_fen=0,status="EXPIRED" WHERE source_recharge_id=999997'
other_json POST /auth/mini/close-account | jq -e '.data == true' >/dev/null
other_json GET /auth/mini/profile/detail | jq -e '.code != 0' >/dev/null
curl -sS -X POST "$base/auth/mini/mock-login" -H 'Content-Type: application/json' \
  -d '{"code":"p4-runtime-participant"}' | jq -e '.code != 0' >/dev/null
mini_json GET "/mini/posts/$other_post_id" | jq -e '.code != 0' >/dev/null
mini_json GET "/mini/posts/$recruit_post_id/recruitment" \
  | jq -e '.data.status == "RECRUITING" and .data.approvedCount == 0' >/dev/null
events_count=$(admin_json GET /platform/content-events | jq -er '.data | length')
(( events_count > 0 )) || { print -u2 '缺少用户动作事件'; return 1; }
notification_id=$(mini_json GET /mini/content/notifications | jq -er '.data[0].id')
mini_json POST "/mini/content/notifications/$notification_id/read" | jq -e '.data == true' >/dev/null
mini_json GET /mini/content/notifications \
  | jq -e --argjson id "$notification_id" '.data | any(.id == $id and .readAt != null)' >/dev/null
print 'P4 community: recruitment capacity, member privacy, notifications, events, and account closure passed'

# 限权处置关联审计，用户仅能申诉本人记录，撤销只解除本条限制。
admin_json POST "/platform/content-governance/restrictions?userId=$mini_user_id&capability=COMMENT&days=1&reason=restriction" \
  | jq -e '.data == true' >/dev/null
mini_json POST "/mini/posts/$recruit_post_id/comments" '{"body":"限权期间拒绝"}' | jq -e '.code != 0' >/dev/null
restriction_log=$(mini_json GET /mini/content/decisions | jq -er '.data[] | select(.action == "RESTRICT") | .id')
mini_json POST "/mini/content/appeals?moderationLogId=$restriction_log&body=review" | jq -e '.data == true' >/dev/null
restriction_appeal=$(admin_json GET /platform/content-governance/appeals \
  | jq -er --argjson id "$restriction_log" '.data[] | select(.moderationLogId == $id) | .id')
admin_json POST "/platform/content-governance/appeals/$restriction_appeal/decide?status=REVERSED&reason=revoked" \
  | jq -e '.data == true' >/dev/null

# 重签只影响后续写入，旧接受事实保留。
admin_json PUT /platform/content-agreement \
  '{"rulesVersion":"2","privacyVersion":"2","rulesText":"local test rules v2","privacyText":"local test privacy v2","forceReaccept":true}' \
  | jq -e '.data.rulesVersion == "2"' >/dev/null
mini_json POST "/mini/posts/$recruit_post_id/comments" '{"body":"需要重新接受"}' \
  | jq -e '.msg == "请先接受当前社区规则与隐私说明"' >/dev/null
mini_json POST /mini/content/agreement '{"rulesVersion":"1","privacyVersion":"1"}' | jq -e '.code != 0' >/dev/null
mini_json POST /mini/content/agreement '{"rulesVersion":"2","privacyVersion":"2"}' | jq -e '.data.accepted == true' >/dev/null
acceptances=$(docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
  -e "SELECT COUNT(*) FROM s_content_agreement_acceptance WHERE user_id=$mini_user_id")
[[ "$acceptances" == 2 ]] || { print -u2 '协议接受历史被覆盖'; return 1; }
mini_json POST /mini/posts/create '{"body":"第五条动态","sportTypeId":1}' | jq -e '.code == 0' >/dev/null
mini_json POST /mini/posts/create '{"body":"第六条动态应被拒绝","sportTypeId":1}' | jq -e '.code != 0' >/dev/null
for number in {1..29}; do
  mini_json POST "/mini/posts/$recruit_post_id/comments" "{\"body\":\"固定评论频率 $number\"}" \
    | jq -e '.code == 0' >/dev/null
done
mini_json POST "/mini/posts/$recruit_post_id/comments" '{"body":"评论超出每小时限制"}' | jq -e '.code != 0' >/dev/null
for number in {1..120}; do
  mini_json POST "/mini/posts/$recruit_post_id/likes?liked=true" | jq -e '.data == 1' >/dev/null
done
mini_json POST "/mini/posts/$recruit_post_id/likes?liked=true" | jq -e '.code != 0' >/dev/null
print 'P4 community: restriction appeals, protocol reacceptance, and fixed post/comment/like limits passed'
