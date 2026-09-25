<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Ban, CalendarDays, CircleAlert, CircleCheck, CircleX, Clock3, RefreshCw, X } from "@lucide/vue";
import { api, money, percent, today } from "../api";
import { hasPermission } from "../auth";
import { statusLabel, statusTone } from "../labels";
import type { OperationReport, SessionView, SportType } from "../types";

const selectedDate = ref(today());
const selectedSportId = ref("");
const sessions = ref<SessionView[]>([]);
const sports = ref<SportType[]>([]);
const error = ref("");
const actionError = ref("");
const loading = ref(false);
const selected = ref<SessionView>();
const drawer = ref<HTMLDialogElement>();
const contactName = ref("");
const contactPhone = ref("");
const paymentStatus = ref("UNPAID");
const paymentReason = ref("");
const trendDays = ref<7 | 30>(7);
const report = ref<OperationReport>();
const canViewSessions = hasPermission("SESSION_VIEW");
const canEditSessions = hasPermission("SESSION_EDIT");
const canEditBookings = hasPermission("BOOKING_EDIT");
const canEditOfflinePayment = hasPermission("OFFLINE_PAYMENT_STATUS_EDIT");

const courts = computed(() => [...new Map(sessions.value.map(item => [item.courtId, item.courtName])).entries()]
  .map(([id, name]) => ({ id, name })));
const times = computed(() => [...new Set(sessions.value.map(item => item.startAt))]);
const sessionMap = computed(() => new Map(sessions.value.map(item => [`${item.startAt}:${item.courtId}`, item])));
const trendRows = computed(() => report.value?.daily.slice(-trendDays.value) || []);
const trendPoints = computed(() => {
  const values = trendRows.value.map(day => day.onlineBookingAmountFen + day.offlineRegisteredAmountFen);
  const max = Math.max(...values, 1);
  return values.map((value, index) => `${values.length === 1 ? 300 : index * 600 / (values.length - 1)},${150 - value * 130 / max}`).join(" ");
});

async function loadReport() {
  try {
    report.value = await api<OperationReport>(`/venue-admin/reports/dashboard?days=${trendDays.value}`);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "经营指标加载失败";
  }
}

async function setTrend(days: 7 | 30) {
  trendDays.value = days;
  await loadReport();
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const query = new URLSearchParams({ date: selectedDate.value });
    if (selectedSportId.value) query.set("sportTypeId", selectedSportId.value);
    [sessions.value, sports.value] = await Promise.all([
      api<SessionView[]>(`/venue-admin/sessions/list?${query}`),
      sports.value.length ? Promise.resolve(sports.value) : api<SportType[]>("/venue-admin/sports/list"),
    ]);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "日视图加载失败";
  } finally {
    loading.value = false;
  }
}

function time(value: string) {
  return value.slice(11, 16);
}

function cellLabel(slot: SessionView) {
  if (slot.status === "OPEN" || slot.status === "CLOSED" || slot.status === "HELD") return statusLabel(slot.status);
  if (slot.bookingSource === "OFFLINE") return `线下 · ${statusLabel(slot.paymentStatus || "UNRECORDED")}`;
  return statusLabel(slot.bookingStatus || slot.status);
}

function cellTone(slot: SessionView) {
  return statusTone(slot.paymentStatus || slot.bookingStatus || slot.status);
}

function cellIcon(slot: SessionView) {
  const state = slot.paymentStatus || slot.bookingStatus || slot.status;
  if (["OPEN", "COMPLETED", "PAID"].includes(state)) return CircleCheck;
  if (["CANCELLED", "CLOSED"].includes(state)) return CircleX;
  if (["NO_SHOW", "PAYMENT_FAILED", "REFUND_FAILED"].includes(state)) return CircleAlert;
  if (state === "ENDED_UNTRACKED") return Ban;
  return Clock3;
}

function openActions(slot: SessionView) {
  selected.value = slot;
  contactName.value = slot.contactName || "";
  contactPhone.value = slot.contactPhone || "";
  paymentStatus.value = slot.paymentStatus === "UNRECORDED" ? "UNPAID" : slot.paymentStatus || "UNPAID";
  paymentReason.value = "";
  actionError.value = "";
  drawer.value?.showModal();
}

async function createOffline() {
  if (!selected.value) return;
  await perform(async () => api("/venue-admin/bookings/offline", {
    method: "POST",
    body: JSON.stringify({ sessionSlotId: selected.value?.id, contactName: contactName.value,
      contactPhone: contactPhone.value, paymentStatus: paymentStatus.value }),
  }));
}

async function savePaymentStatus() {
  if (!selected.value?.bookingId) return;
  await perform(async () => api(`/venue-admin/bookings/${selected.value?.bookingId}/payment-status`, {
    method: "PUT", body: JSON.stringify({ paymentStatus: paymentStatus.value, reason: paymentReason.value }),
  }));
}

async function changeSlot(action: "open" | "close") {
  if (!selected.value) return;
  await perform(async () => api(`/venue-admin/sessions/${selected.value?.id}/${action}`, { method: "POST" }));
}

async function perform(action: () => Promise<unknown>) {
  actionError.value = "";
  try {
    await action();
    drawer.value?.close();
    await load();
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : "操作失败";
  }
}

onMounted(() => Promise.all([canViewSessions ? load() : Promise.resolve(), loadReport()]));
</script>

<template>
  <section class="page">
    <header class="page-header"><div><h1>今日运营</h1><p>{{ canViewSessions ? "查看经营趋势，并按时间和场地处理当日运营。" : "查看场馆核心经营指标与趋势。" }}</p></div></header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <dl v-if="report" class="dashboard-metrics" aria-label="经营核心指标">
      <div><dt>线上预约经营金额</dt><dd>{{ money(report.onlineBookingAmountFen) }}</dd></div><div><dt>线下登记经营金额</dt><dd>{{ money(report.offlineRegisteredAmountFen) }}</dd></div><div><dt>场地利用率</dt><dd>{{ percent(report.utilizationRateBasisPoints) }}</dd></div><div><dt>线上待核销</dt><dd>{{ report.pendingVerificationCount }}</dd></div><div><dt>线下待登记</dt><dd>{{ report.offlineUnrecordedCount + report.offlineUnpaidCount }}</dd></div><div><dt>新增会员</dt><dd>{{ report.newMemberCount }}</dd></div><div><dt>充值实付流入</dt><dd>{{ money(report.rechargeInflowFen) }}</dd></div><div><dt>会员余额消费</dt><dd>{{ money(report.balanceConsumptionFen) }}</dd></div>
    </dl>
    <section v-if="report" class="data-panel trend-panel" aria-labelledby="trend-title">
      <header class="data-panel-head"><div><h2 id="trend-title">经营趋势</h2><p>线上经营与线下登记金额合计</p></div><div class="tabs" role="tablist" aria-label="趋势区间"><button class="button" :class="{ secondary: trendDays !== 7 }" type="button" role="tab" :aria-selected="trendDays === 7" @click="setTrend(7)">7 天</button><button class="button" :class="{ secondary: trendDays !== 30 }" type="button" role="tab" :aria-selected="trendDays === 30" @click="setTrend(30)">30 天</button></div></header>
      <svg class="trend-chart" viewBox="0 0 600 160" role="img" aria-labelledby="trend-title trend-desc" preserveAspectRatio="none"><desc id="trend-desc">所选日期内每天线上与线下经营金额合计折线</desc><line x1="0" y1="150" x2="600" y2="150" /><polyline :points="trendPoints" /></svg>
      <details class="trend-data"><summary>查看趋势数据表</summary><div class="table-wrap"><table><thead><tr><th>日期</th><th>线上经营</th><th>线下登记</th><th>合计</th></tr></thead><tbody><tr v-for="day in trendRows" :key="day.reportDate"><td data-label="日期">{{ day.reportDate }}</td><td data-label="线上经营">{{ money(day.onlineBookingAmountFen) }}</td><td data-label="线下登记">{{ money(day.offlineRegisteredAmountFen) }}</td><td data-label="合计">{{ money(day.onlineBookingAmountFen + day.offlineRegisteredAmountFen) }}</td></tr></tbody></table></div></details>
    </section>
    <template v-if="canViewSessions">
    <div class="filter-bar" aria-label="日视图筛选">
      <label class="field"><span>日期</span><input v-model="selectedDate" type="date" @change="load"></label>
      <label class="field"><span>运动</span><select v-model="selectedSportId" @change="load"><option value="">全部运动</option><option v-for="sport in sports" :key="sport.id" :value="String(sport.id)">{{ sport.name }}</option></select></label>
      <button class="icon-button filter-action" type="button" aria-label="刷新日视图" title="刷新日视图" :disabled="loading" @click="load"><RefreshCw :size="19" aria-hidden="true" /></button>
    </div>
    <section class="data-panel" aria-label="时间和场地日视图">
      <header class="data-panel-head"><div><h2>时间 × 场地</h2><p>{{ selectedDate }}，点击场次执行操作</p></div><span class="record-count">{{ sessions.length }} 场</span></header>
      <div v-if="sessions.length" class="day-grid-wrap">
        <div class="day-grid" :style="{ '--court-count': String(courts.length) }">
          <div class="grid-corner">时间</div><div v-for="court in courts" :key="court.id" class="grid-court">{{ court.name }}</div>
          <template v-for="startAt in times" :key="startAt">
            <time class="grid-time" :datetime="startAt">{{ time(startAt) }}</time>
            <template v-for="court in courts" :key="court.id">
              <button v-if="sessionMap.get(`${startAt}:${court.id}`)" class="slot-cell" :data-tone="cellTone(sessionMap.get(`${startAt}:${court.id}`)!)" type="button" @click="openActions(sessionMap.get(`${startAt}:${court.id}`)!)">
                <strong class="slot-state"><component :is="cellIcon(sessionMap.get(`${startAt}:${court.id}`)!)" :size="16" aria-hidden="true" />{{ cellLabel(sessionMap.get(`${startAt}:${court.id}`)!) }}</strong>
                <span>{{ sessionMap.get(`${startAt}:${court.id}`)?.sportName }} · {{ money(sessionMap.get(`${startAt}:${court.id}`)?.priceFen) }}</span>
              </button>
              <span v-else class="grid-empty">—</span>
            </template>
          </template>
        </div>
      </div>
      <div v-else-if="!loading" class="empty-state"><CalendarDays :size="28" aria-hidden="true" /><strong>这一天没有场次</strong><span>可在“具体场次”中临时加场。</span></div>
      <div v-else class="empty-state" aria-live="polite"><span>正在加载日视图...</span></div>
    </section>

    <dialog ref="drawer" class="drawer">
      <form method="dialog" class="modal-head"><h2>场次操作</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <div v-if="selected" class="drawer-body">
        <p v-if="actionError" class="error-banner" role="alert">{{ actionError }}</p>
        <div class="detail-list">
          <div><span>时间</span><strong>{{ time(selected.startAt) }}–{{ time(selected.endAt) }}</strong></div>
          <div><span>场地</span><strong>{{ selected.courtName }} · {{ selected.sportName }}</strong></div>
          <div><span>状态</span><strong>{{ cellLabel(selected) }}</strong></div>
        </div>
        <form v-if="selected.status === 'OPEN' && canEditBookings" class="drawer-form" @submit.prevent="createOffline">
          <h3>创建线下预约</h3>
          <label class="field"><span>联系人</span><input v-model.trim="contactName" required></label>
          <label class="field"><span>手机号</span><input v-model.trim="contactPhone" inputmode="tel" required></label>
          <label class="field"><span>登记收款状态</span><select v-model="paymentStatus"><option value="UNPAID">未支付</option><option value="PAID">已支付</option></select></label>
          <button class="button" type="submit">创建线下预约</button>
        </form>
        <form v-else-if="selected.bookingSource === 'OFFLINE' && selected.bookingId && canEditOfflinePayment" class="drawer-form" @submit.prevent="savePaymentStatus">
          <h3>保存收款状态</h3>
          <label class="field"><span>新状态</span><select v-model="paymentStatus"><option v-if="selected.paymentStatus === 'UNRECORDED'" value="UNPAID">未支付</option><option v-if="['UNRECORDED', 'UNPAID'].includes(selected.paymentStatus || '')" value="PAID">已支付</option><option v-if="selected.bookingStatus === 'CANCELLED' && selected.paymentStatus === 'PAID'" value="REFUNDED">已退款</option></select></label>
          <label class="field"><span>修改原因</span><textarea v-model.trim="paymentReason" required></textarea></label>
          <button class="button" type="submit">保存收款状态</button>
        </form>
        <button v-if="canEditSessions && selected.status === 'OPEN'" class="button danger" type="button" @click="changeSlot('close')">关闭场次</button>
        <button v-if="canEditSessions && selected.status === 'CLOSED'" class="button secondary" type="button" @click="changeSlot('open')">重新开放场次</button>
      </div>
    </dialog>
    </template>
  </section>
</template>

<style scoped>
.dashboard-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0; border: 1px solid var(--color-rule-2); background: var(--color-surface); }
.dashboard-metrics > div { min-width: 0; padding: var(--space-md); border-inline-end: 1px solid var(--color-rule-2); border-block-end: 1px solid var(--color-rule-2); }
.dashboard-metrics dt { color: var(--color-muted); font-size: var(--text-xs); }
.dashboard-metrics dd { margin: var(--space-xs) 0 0; overflow-wrap: anywhere; font-family: var(--font-numeric); font-size: var(--text-lg); font-weight: 600; font-variant-numeric: tabular-nums; }
.trend-chart { width: 100%; height: 10rem; border-block: 1px solid var(--color-rule-2); }
.trend-chart line { stroke: var(--color-rule); stroke-width: 1; }
.trend-chart polyline { fill: none; stroke: var(--color-signal); stroke-width: 3; vector-effect: non-scaling-stroke; }
.trend-data { padding: var(--space-md); }
.trend-data summary { cursor: pointer; font-weight: 600; }
@media (max-width: 59.999rem) { .dashboard-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
