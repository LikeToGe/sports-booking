<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { Check, Eye, EyeOff, RefreshCw, ShieldCheck, Trash2, X } from "@lucide/vue";
import { api, localDateTime, mediaObjectUrl } from "../api";

interface PendingReview {
  contentType: "REVIEW" | "REVIEW_REPLY" | "POST" | "COMMENT";
  replyId?: number;
  reviewId: number;
  revisionId: number;
  venueId: number;
  venueName: string;
  body: string;
  publicBody?: string;
  contentStatus: "PENDING" | "PUBLISHED" | "HIDDEN";
  moderationStatus: "PENDING" | "APPROVED" | "REJECTED";
  overallScore: number;
  submittedAt: string;
}

const reviews = ref<PendingReview[]>([]);
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const message = ref("");
const selected = ref<PendingReview | null>(null);
type Action = "APPROVE" | "REJECT" | "HIDE" | "RESTORE" | "DELETE";
const actionLabels: Record<Action, string> = { APPROVE: "通过", REJECT: "拒绝", HIDE: "隐藏", RESTORE: "恢复", DELETE: "删除" };
const action = ref<Action>("APPROVE");
const reason = ref("");
const decisionDialog = ref<HTMLDialogElement | null>(null);
const mediaDialog = ref<HTMLDialogElement | null>(null);
const previews = ref<{ mediaType: string; url: string }[]>([]);
const keyword = ref("");
const contentTypeFilter = ref("");
const moderationFilter = ref("");
const filteredReviews = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return reviews.value.filter(review => {
    const content = [review.contentType, review.venueName, review.body, review.publicBody, String(review.reviewId)].filter(Boolean).join(" ").toLowerCase();
    return (!query || content.includes(query))
      && (!contentTypeFilter.value || review.contentType === contentTypeFilter.value)
      && (!moderationFilter.value || review.moderationStatus === moderationFilter.value);
  });
});
function clearPreviews() {
  previews.value.forEach(media => URL.revokeObjectURL(media.url));
  previews.value = [];
}
async function preview(row: PendingReview) {
  clearPreviews(); error.value = "";
  try {
    const media = await api<{ mediaType: string; url: string }[]>(`/platform/content-media/${row.contentType}/${row.revisionId}`);
    for (const item of media) previews.value.push({ mediaType: item.mediaType, url: await mediaObjectUrl(item.url) });
    mediaDialog.value?.showModal();
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "媒体加载失败"; }
}
onUnmounted(clearPreviews);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [reviewRows, postRows, commentRows] = await Promise.all([
      api<PendingReview[]>("/platform/content-moderation/reviews"),
      api<PendingReview[]>("/platform/content-moderation/posts/list"),
      api<PendingReview[]>("/platform/content-moderation/comments/list"),
    ]);
    reviews.value = [...reviewRows, ...postRows, ...commentRows].sort((a, b) =>
      Number(b.moderationStatus === "PENDING") - Number(a.moderationStatus === "PENDING")
      || a.submittedAt.localeCompare(b.submittedAt));
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "评价审核列表加载失败";
  } finally {
    loading.value = false;
  }
}

function contentLabel(review: PendingReview) {
  return review.contentType === "REVIEW_REPLY" ? `官方回复 #${review.replyId}`
    : review.contentType === "POST" ? `动态 #${review.reviewId}`
    : review.contentType === "COMMENT" ? `评论 #${review.reviewId}` : `评价 #${review.reviewId}`;
}

function statusLabel(review: PendingReview) {
  if (review.moderationStatus === "PENDING") {
    if (review.contentStatus === "PUBLISHED") return "新版待审，旧版公开";
    if (review.contentStatus === "HIDDEN") return "新版待审，旧版隐藏";
    return "待审";
  }
  if (review.moderationStatus === "REJECTED") {
    if (review.contentStatus === "PUBLISHED") return "新版未通过，旧版公开";
    if (review.contentStatus === "HIDDEN") return "新版未通过，旧版隐藏";
    return "未通过";
  }
  return { PENDING: "未公开", PUBLISHED: "已公开", HIDDEN: "已隐藏" }[review.contentStatus];
}

function openDecision(review: PendingReview, nextAction: Action) {
  selected.value = review;
  action.value = nextAction;
  reason.value = "";
  error.value = "";
  decisionDialog.value?.showModal();
}

async function submitDecision() {
  if (!selected.value || !reason.value.trim() || saving.value) return;
  saving.value = true;
  error.value = "";
  try {
    const path = selected.value.contentType === "REVIEW_REPLY"
      ? `/platform/content-moderation/replies/${selected.value.replyId}`
      : selected.value.contentType === "COMMENT"
        ? `/platform/content-moderation/comments/${selected.value.reviewId}`
      : selected.value.contentType === "POST"
        ? `/platform/content-moderation/posts/${selected.value.reviewId}`
        : `/platform/content-moderation/reviews/${selected.value.reviewId}`;
    await api(path, {
      method: "POST",
      body: JSON.stringify({ revisionId: selected.value.revisionId, action: action.value, reason: reason.value.trim() }),
    });
    message.value = `${contentLabel(selected.value)} 已${actionLabels[action.value]}`;
    decisionDialog.value?.close();
    await load();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "审核提交失败";
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>内容治理</h1></div>
      <button class="button secondary" type="button" :disabled="loading" @click="load"><RefreshCw :size="18" aria-hidden="true" />刷新列表</button>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="message" class="saved-message" aria-live="polite"><ShieldCheck :size="17" aria-hidden="true" />{{ message }}</p>
    <div class="moderation-filters" aria-label="内容审核筛选">
      <label class="field filter-search"><span>关键词</span><input v-model.trim="keyword" type="search" placeholder="场馆、正文或内容编号"></label>
      <label class="field"><span>内容类型</span><select v-model="contentTypeFilter"><option value="">全部类型</option><option value="REVIEW">评价</option><option value="REVIEW_REPLY">官方回复</option><option value="POST">动态</option><option value="COMMENT">评论</option></select></label>
      <label class="field"><span>审核状态</span><select v-model="moderationFilter"><option value="">全部状态</option><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已拒绝</option></select></label>
      <button class="button secondary" type="button" @click="keyword = ''; contentTypeFilter = ''; moderationFilter = ''">重置筛选</button>
    </div>

    <section class="data-panel" aria-label="内容处置列表" :aria-busy="loading">
      <header class="data-panel-head"><div><h2>内容处置</h2><p>先筛选待审内容，再执行审核动作</p></div><span class="record-count">{{ filteredReviews.length }} 条</span></header>
      <div v-if="filteredReviews.length" class="table-wrap">
        <table>
          <thead><tr><th>内容</th><th>正文</th><th>状态</th><th>评分</th><th>提交时间</th><th>操作</th></tr></thead>
          <tbody><tr v-for="review in filteredReviews" :key="`${review.contentType}-${review.revisionId}`">
            <td data-label="内容"><strong>{{ contentLabel(review) }}</strong><br>{{ review.venueName || "—" }}</td>
            <td data-label="正文" class="review-body"><strong>当前版本</strong><br>{{ review.body }}<div v-if="review.publicBody && review.publicBody !== review.body" class="muted">公开版本：{{ review.publicBody }}</div></td>
            <td data-label="状态">{{ statusLabel(review) }}</td>
            <td data-label="评分" class="numeric">{{ review.overallScore == null ? "—" : review.overallScore + " 星" }}</td>
            <td data-label="提交时间">{{ localDateTime(`${review.submittedAt}Z`) }}</td>
            <td data-label="操作"><div class="row-actions">
              <button v-if="review.contentType === 'POST' || review.contentType === 'REVIEW'" class="button secondary" @click="preview(review)"><Eye :size="16" />媒体</button>
              <button v-if="review.moderationStatus === 'PENDING'" class="button secondary" type="button" @click="openDecision(review, 'APPROVE')"><Check :size="16" aria-hidden="true" />通过</button>
              <button v-if="review.moderationStatus === 'PENDING'" class="button secondary" type="button" @click="openDecision(review, 'REJECT')"><X :size="16" aria-hidden="true" />拒绝</button>
              <button v-if="review.contentStatus === 'PUBLISHED'" class="button secondary" type="button" @click="openDecision(review, 'HIDE')"><EyeOff :size="16" aria-hidden="true" />隐藏</button>
              <button v-if="review.contentStatus === 'HIDDEN'" class="button secondary" type="button" @click="openDecision(review, 'RESTORE')"><Eye :size="16" aria-hidden="true" />恢复</button>
              <button class="button secondary" type="button" @click="openDecision(review, 'DELETE')"><Trash2 :size="16" aria-hidden="true" />删除</button>
            </div></td>
          </tr></tbody>
        </table>
      </div>
      <div v-else class="empty-state" aria-live="polite"><ShieldCheck :size="26" aria-hidden="true" /><strong>{{ loading ? "正在加载" : reviews.length ? "没有匹配的内容" : "暂无内容" }}</strong><span>{{ reviews.length ? "调整筛选条件后重新查看。" : "新的待审核内容会显示在这里。" }}</span></div>
    </section>

    <dialog ref="decisionDialog" class="decision-dialog" @close="selected = null">
      <form method="dialog" @submit.prevent="submitDecision">
        <header><h2>{{ actionLabels[action] }}{{ selected ? contentLabel(selected) : "内容" }}</h2><button class="icon-button" type="button" aria-label="关闭" @click="decisionDialog?.close()"><X :size="18" aria-hidden="true" /></button></header>
        <p class="dialog-review">{{ selected?.body }}</p>
        <p v-if="selected?.publicBody && selected.publicBody !== selected.body" class="dialog-review">当前公开：{{ selected.publicBody }}</p>
        <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
        <label class="field"><span>处置原因</span><textarea v-model="reason" maxlength="255" required rows="3" /></label>
        <footer><button class="button secondary" type="button" @click="decisionDialog?.close()">取消</button><button class="button primary" type="submit" :disabled="saving || !reason.trim()">{{ saving ? "提交中" : "确认" }}</button></footer>
      </form>
    </dialog>
    <dialog ref="mediaDialog" class="decision-dialog" @close="clearPreviews"><header class="media-head"><h2>媒体预览</h2><button class="icon-button" aria-label="关闭" @click="mediaDialog?.close()"><X :size="18" /></button></header><div v-for="media in previews" :key="media.url" class="media-preview"><video v-if="media.mediaType === 'VIDEO'" :src="media.url" controls /><img v-else :src="media.url" alt="待审用户图片" /></div><p v-if="!previews.length">此版本没有媒体</p></dialog>
  </section>
</template>

<style scoped>
.moderation-filters { display: flex; flex-wrap: wrap; align-items: end; gap: var(--space-sm); padding: var(--space-md); border: 1px solid var(--color-rule); border-radius: var(--radius-card); background: var(--color-surface); }
.moderation-filters .field { flex: 1 1 12rem; min-width: 10rem; }
.moderation-filters .filter-search { flex: 2 1 20rem; }
.review-body { min-width: 15rem; max-width: 32rem; white-space: normal; overflow-wrap: anywhere; }
.row-actions { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.row-actions .button { width: auto; min-height: 2.25rem; }
.decision-dialog { width: min(30rem, calc(100vw - 2rem)); max-height: calc(100dvh - 2rem); padding: var(--space-lg); border: 1px solid var(--color-rule); background: var(--color-surface); color: var(--color-ink); }
.decision-dialog::backdrop { background: rgb(0 0 0 / 55%); }
.decision-dialog form { display: grid; gap: var(--space-md); }
.decision-dialog header, .decision-dialog footer { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); }
.decision-dialog footer { justify-content: flex-end; }
.dialog-review { max-height: 9rem; overflow: auto; overflow-wrap: anywhere; }
.decision-dialog textarea { width: 100%; min-height: 6rem; resize: vertical; }
.media-head { display: flex; justify-content: space-between; align-items: center; }
.media-preview img,.media-preview video { display: block; width: 100%; max-height: 24rem; object-fit: contain; margin-top: var(--space-md); }
</style>
