# 检测异常专用分支，由隔离运行脚本 source。
curl -fsS -X PUT "$base/platform/content-settings/update" -H "X-Admin-Token: $platform_token" \
  -H 'Content-Type: application/json' -d '{"postEnabled":false,"commentEnabled":false,"reviewEnabled":false}' \
  | jq -e '.data.postEnabled == false' >/dev/null
text_post=$(curl -fsS -X POST "$base/mini/posts/create" -H "X-Mini-Token: $mini_token" \
  -H 'Content-Type: application/json' -d '{"body":"检测异常保留待审","sportTypeId":1}')
print -r -- "$text_post" | jq -e '.data.moderationStatus == "PENDING"' >/dev/null
text_id=$(print -r -- "$text_post" | jq -er '.data.id')
text_revision=$(print -r -- "$text_post" | jq -er '.data.revisionId')
curl -fsS -H "X-Mini-Token: $mini_token" "$base/mini/posts/list" | jq -e '.data == []' >/dev/null
upload_id=$(curl -fsS -X POST "$base/mini/content/uploads" -H "X-Mini-Token: $mini_token" \
  -F "file=@${backend_dir}/../miniapp/assets/venues/venue-1.jpg;type=image/jpeg" | jq -er '.data.id')
media_post=$(curl -fsS -X POST "$base/mini/posts/create" -H "X-Mini-Token: $mini_token" \
  -H 'Content-Type: application/json' -d "{\"body\":\"媒体检测异常\",\"sportTypeId\":1,\"uploadIds\":[$upload_id]}")
print -r -- "$media_post" | jq -e '.data.moderationStatus == "PENDING"' >/dev/null
media_post_id=$(print -r -- "$media_post" | jq -er '.data.id')
media_revision=$(print -r -- "$media_post" | jq -er '.data.revisionId')
for attempt in {1..20}; do
  detection_status=$(docker exec "$mysql_container" mysql -uroot -preview_root -Ns sports_booking \
    -e "SELECT status FROM s_content_detection_job WHERE content_type='POST' AND revision_id=$media_revision")
  [[ "$detection_status" != QUEUED ]] && break
  sleep 1
done
expected="$P4_DETECTION_OUTCOME"
[[ "$expected" == TIMEOUT ]] && expected=TIMED_OUT
[[ "$expected" == FAIL ]] && expected=FAILED
[[ "$detection_status" == "$expected" ]] || { print -u2 '检测结果未按配置收敛'; return 1; }
curl -fsS -H "X-Mini-Token: $mini_token" "$base/mini/posts/list" | jq -e '.data == []' >/dev/null
curl -fsS -X POST "$base/platform/content-moderation/posts/$text_id" -H "X-Admin-Token: $platform_token" \
  -H 'Content-Type: application/json' -d "{\"revisionId\":$text_revision,\"action\":\"APPROVE\",\"reason\":\"人工复核通过\"}" \
  | jq -e '.data == true' >/dev/null
curl -fsS -X POST "$base/platform/content-moderation/posts/$media_post_id" -H "X-Admin-Token: $platform_token" \
  -H 'Content-Type: application/json' -d "{\"revisionId\":$media_revision,\"action\":\"APPROVE\",\"reason\":\"人工复核媒体通过\"}" \
  | jq -e '.data == true' >/dev/null
curl -fsS -H "X-Mini-Token: $mini_token" "$base/mini/posts/$media_post_id" \
  | jq -e '.data.moderationStatus == "APPROVED" and .data.media[0].moderationStatus == "APPROVED"' >/dev/null
curl -fsS -X POST "$base/mini/posts/$text_id/comments" -H "X-Mini-Token: $mini_token" \
  -H 'Content-Type: application/json' -d '{"body":"评论检测也保留待审"}' \
  | jq -e '.data.moderationStatus == "PENDING"' >/dev/null
print "P4 detection: $P4_DETECTION_OUTCOME keeps new text/media/comments pending until an audited manual decision"
