<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { CircleCheck, Clock3, RefreshCw, Search, TriangleAlert } from "@lucide/vue";
import { api, localDateTime, money } from "../api";

interface PlatformRefund {
  id: number;
  businessNo: string;
  bookingId: number;
  bookingNo: string;
  venueId: number;
  venueName: string;
  amountFen: number;
  status: "PROCESSING" | "SUCCESS" | "FAILED" | "CLOSED";
  retryCount: number;
  nextRetryAt: string | null;
  lastFailureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

const refunds = ref<PlatformRefund[]>([]);
const keyword = ref("");
const status = ref("");
const error = ref("");
const message = ref("");
const loading = ref(false);
const retryingId = ref<number | null>(null);
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return refunds.value.filter(refund =>
    (!status.value || refund.status === status.value)
    && (!query || [refund.businessNo, refund.bookingNo, refund.venueName].some(value => value.toLowerCase().includes(query))),
  );
});

function statusText(value: PlatformRefund["status"]) {
  return { PROCESSING: "处理中", SUCCESS: "退款成功", FAILED: "退款失败", CLOSED: "已关闭" }[value];
}

function statusTone(value: PlatformRefund["status"]) {
  if (value === "SUCCESS") return "active";
  if (value === "PROCESSING") return "warning";
  return "danger";
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    refunds.value = await api<PlatformRefund[]>("/platform/refunds/list");
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "退款异常加载失败";
  } finally {
    loading.value = false;
  }
}

async function retry(refund: PlatformRefund) {
  retryingId.value = refund.id;
  error.value = "";
  message.value = "";
  try {
    await api(`/platform/refunds/${refund.id}/retry`, { method: "POST" });
    message.value = `退款 ${refund.businessNo} 已按原业务号发起重试`;
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "退款重试失败";
  } finally {
    retryingId.value = null;
  }
}

onMounted(load);
</script>

<template>
  <section class="page platform-refunds-page">
    <header class="page-header">
      <div><h1>退款异常</h1><p>查看外部退款处理中与失败记录，并复用原退款业务号重试。</p></div>
      <button class="button secondary" type="button" :disabled="loading" @click="load"><RefreshCw :size="18" aria-hidden="true" />{{ loading ? "正在刷新" : "刷新列表" }}</button>
    </header>

    <p v-if="error" class="error-banner" role="alert">{{ error }} <button class="inline-command" type="button" @click="load">重新加载</button></p>
    <p v-if="message" class="saved-message" aria-live="polite"><CircleCheck :size="17" aria-hidden="true" />{{ message }}</p>

    <div class="filter-bar" aria-label="退款异常筛选">
      <label class="field filter-search"><span>业务号、预约号或场馆</span><input v-model.trim="keyword" type="search" placeholder="输入完整或部分内容"><Search class="field-icon" :size="17" aria-hidden="true" /></label>
      <label class="field"><span>退款状态</span><select v-model="status"><option value="">全部异常</option><option value="PROCESSING">处理中</option><option value="FAILED">退款失败</option></select></label>
    </div>

    <section class="data-panel" aria-label="退款异常列表" :aria-busy="loading">
      <header class="data-panel-head"><div><h2>外部退款记录</h2><p>重试不会创建新的退款业务号</p></div><span class="record-count">{{ filtered.length }} 条</span></header>
      <div v-if="filtered.length" class="table-wrap">
        <table>
          <thead><tr><th>退款业务号</th><th>预约 / 场馆</th><th class="numeric-column">金额</th><th>状态</th><th>重试</th><th>失败原因 / 下次重试</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="refund in filtered" :key="refund.id">
              <td data-label="退款业务号"><strong class="business-no">{{ refund.businessNo }}</strong><span class="subline">更新 {{ localDateTime(refund.updatedAt) }}</span></td>
              <td data-label="预约 / 场馆"><strong>{{ refund.bookingNo }}</strong><span class="subline">{{ refund.venueName }}</span></td>
              <td data-label="金额" class="numeric-column numeric">{{ money(refund.amountFen) }}</td>
              <td data-label="状态"><span class="status status-with-icon" :data-tone="statusTone(refund.status)"><CircleCheck v-if="refund.status === 'SUCCESS'" :size="15" aria-hidden="true" /><Clock3 v-else-if="refund.status === 'PROCESSING'" :size="15" aria-hidden="true" /><TriangleAlert v-else :size="15" aria-hidden="true" />{{ statusText(refund.status) }}</span></td>
              <td data-label="重试" class="numeric">{{ refund.retryCount }} 次</td>
              <td data-label="失败原因 / 下次重试"><span>{{ refund.lastFailureReason || "暂无失败原因" }}</span><span class="subline">{{ refund.nextRetryAt ? `下次 ${localDateTime(refund.nextRetryAt)}` : "暂无自动重试时间" }}</span></td>
              <td data-label="操作"><button class="button secondary retry-action" type="button" :disabled="retryingId === refund.id || !['PROCESSING', 'FAILED'].includes(refund.status)" :aria-busy="retryingId === refund.id" @click="retry(refund)"><RefreshCw :size="16" aria-hidden="true" />{{ retryingId === refund.id ? "重试中" : "重试退款" }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state" aria-live="polite"><RefreshCw class="spin" :size="26" aria-hidden="true" /><strong>正在加载退款记录</strong></div>
      <div v-else class="empty-state"><CircleCheck v-if="!refunds.length" :size="28" aria-hidden="true" /><Search v-else :size="28" aria-hidden="true" /><strong>{{ refunds.length ? "没有匹配的退款记录" : "当前没有退款异常" }}</strong><span>{{ refunds.length ? "调整业务号、场馆或状态后重试。" : "处理中或失败的外部退款会显示在这里。" }}</span></div>
    </section>
  </section>
</template>

<style scoped>
.field-icon {
  display: none;
}

.business-no,
.subline {
  display: block;
}

.business-no {
  font-variant-numeric: tabular-nums;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.subline {
  color: var(--color-muted);
  font-size: var(--text-xs);
}

.retry-action {
  width: 7rem;
}
</style>
