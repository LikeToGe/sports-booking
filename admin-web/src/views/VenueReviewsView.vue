<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RefreshCw, Save } from "@lucide/vue";
import { api } from "../api";
import { hasPermission } from "../auth";

interface ReviewReply { id: number; body: string; contentStatus: string; moderationStatus: string; }
interface Review { id: number; body: string; overallScore: number; reply?: ReviewReply; }

const reviews = ref<Review[]>([]);
const drafts = ref<Record<number, string>>({});
const loading = ref(false);
const saving = ref(0);
const error = ref("");
const message = ref("");
const canEdit = hasPermission("REVIEW_MANAGE");
const keyword = ref("");
const scoreFilter = ref("");
const filteredReviews = computed(() => reviews.value.filter(review =>
  (!keyword.value.trim() || review.body.toLowerCase().includes(keyword.value.trim().toLowerCase()))
  && (!scoreFilter.value || String(review.overallScore) === scoreFilter.value)));

async function load() {
  loading.value = true;
  error.value = "";
  try {
    reviews.value = await api<Review[]>("/venue-admin/reviews");
    drafts.value = Object.fromEntries(reviews.value.map(review => [review.id, review.reply?.body || ""]));
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "评价加载失败";
  } finally {
    loading.value = false;
  }
}

async function save(review: Review) {
  if (!canEdit || saving.value || !drafts.value[review.id]?.trim()) return;
  saving.value = review.id;
  error.value = "";
  message.value = "";
  try {
    await api(`/venue-admin/reviews/${review.id}/reply`, {
      method: "PUT", body: JSON.stringify({ body: drafts.value[review.id].trim() }),
    });
    message.value = `评价 #${review.id} 的回复已提交审核`;
    await load();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "回复保存失败";
  } finally {
    saving.value = 0;
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>场地评价</h1><p>回复提交后由平台审核，通过后展示在公开评价中。</p></div>
      <button class="button secondary" type="button" :disabled="loading" @click="load"><RefreshCw :size="18" aria-hidden="true" />刷新</button>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="message" class="saved-message" role="status">{{ message }}</p>
    <div class="filter-bar" aria-label="评价筛选"><label class="field filter-search"><span>关键词</span><input v-model.trim="keyword" type="search" placeholder="评价正文"></label><label class="field"><span>评分</span><select v-model="scoreFilter"><option value="">全部评分</option><option v-for="score in [5, 4, 3, 2, 1]" :key="score" :value="String(score)">{{ score }} 星</option></select></label><button class="button secondary" type="button" @click="keyword = ''; scoreFilter = ''">重置筛选</button></div>
    <section class="data-panel" aria-label="评价列表" :aria-busy="loading">
      <header class="data-panel-head"><div><h2>已公开评价</h2><p>回复提交后由平台审核，通过后展示在公开评价中</p></div><span class="record-count">{{ filteredReviews.length }} 条</span></header>
      <div v-if="filteredReviews.length" class="table-wrap">
        <table><thead><tr><th>评价</th><th>评分</th><th>官方回复</th><th>操作</th></tr></thead>
          <tbody><tr v-for="review in filteredReviews" :key="review.id">
            <td data-label="评价" class="review-body">{{ review.body }}</td>
            <td data-label="评分" class="numeric">{{ review.overallScore }} 星</td>
            <td data-label="官方回复" class="reply-cell">
              <textarea v-model="drafts[review.id]" maxlength="1000" rows="3" :disabled="!canEdit" placeholder="回复这条评价" />
              <span v-if="review.reply" class="muted">{{ review.reply.moderationStatus === "PENDING" ? "待平台审核" : review.reply.moderationStatus === "REJECTED" ? "已拒绝" : review.reply.contentStatus === "PUBLISHED" ? "已公开" : "未公开" }}</span>
            </td>
            <td data-label="操作"><button class="button secondary" type="button" :disabled="!canEdit || saving === review.id || !drafts[review.id]?.trim()" @click="save(review)"><Save :size="16" aria-hidden="true" />{{ saving === review.id ? "提交中" : "提交回复" }}</button></td>
          </tr></tbody>
        </table>
      </div>
      <div v-else class="empty-state"><strong>{{ loading ? "正在加载" : reviews.length ? "没有匹配的评价" : "暂无公开评价" }}</strong><span>{{ reviews.length ? "调整筛选条件后重新查看。" : "公开评价会显示在这里。" }}</span></div>
    </section>
  </section>
</template>

<style scoped>
.review-body { min-width: 15rem; max-width: 30rem; white-space: normal; overflow-wrap: anywhere; }
.reply-cell { min-width: 18rem; }
.reply-cell textarea { width: 100%; min-height: 4.5rem; resize: vertical; }
.reply-cell .muted { display: block; margin-top: var(--space-xs); }
</style>
