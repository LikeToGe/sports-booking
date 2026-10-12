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
const priorityCount = computed(() => {
  if (!report.value) return 0;
  return report.value.pendingVerificationCount + report.value.offlineUnrecordedCount + report.value.offlineUnpaidCount;
});

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
  if (slot.status === "BOOKED" && slot.bookingSource === "ONLINE" && slot.bookingStatus === "CONFIRMED") return "info";
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
  <section class="page dashboard-page" :class="{ 'without-schedule': !canViewSessions }">
    <div class="dashboard-header">
    <header class="page-header"><div><h1>今日运营</h1><p>{{ canViewSessions ? "查看经营趋势，并按时间和场地处理当日运营。" : "查看场馆核心经营指标与趋势。" }}</p></div></header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    </div>
    <section v-if="report" class="ops-focus" aria-label="今日重点">
      <div class="ops-focus-lead"><span>接下来要处理</span><strong>{{ priorityCount ? `还有 ${priorityCount} 项待处理` : "当前没有待处理事项" }}</strong><small>履约、收款与会员经营</small></div>
      <div class="ops-focus-item" data-tone="warning"><span>待核销</span><strong>{{ report.pendingVerificationCount }}</strong><small>线上预约</small></div>
      <div class="ops-focus-item" data-tone="danger"><span>待登记</span><strong>{{ report.offlineUnrecordedCount + report.offlineUnpaidCount }}</strong><small>线下收款</small></div>
      <div class="ops-focus-item" data-tone="active"><span>新增会员</span><strong>{{ report.newMemberCount }}</strong><small>场馆会员</small></div>
      <div class="ops-focus-item"><span>会员余额消费</span><strong>{{ money(report.balanceConsumptionFen) }}</strong><small>按会员消费口径</small></div>
    </section>
    <dl v-if="report" class="dashboard-metrics" aria-label="经营核心指标">
      <div><dt>线上预约经营金额</dt><dd>{{ money(report.onlineBookingAmountFen) }}</dd></div><div><dt>线下登记经营金额</dt><dd>{{ money(report.offlineRegisteredAmountFen) }}</dd></div><div><dt>场地利用率</dt><dd>{{ percent(report.utilizationRateBasisPoints) }}</dd></div><div><dt>充值实付流入</dt><dd>{{ money(report.rechargeInflowFen) }}</dd></div>
    </dl>
    <section v-if="report" class="data-panel trend-panel" aria-labelledby="trend-title">
      <header class="data-panel-head"><div><h2 id="trend-title">经营趋势</h2><p>线上经营与线下登记金额合计</p></div><div class="tabs" role="tablist" aria-label="趋势区间"><button class="button" :class="{ secondary: trendDays !== 7 }" type="button" role="tab" :aria-selected="trendDays === 7" @click="setTrend(7)">7 天</button><button class="button" :class="{ secondary: trendDays !== 30 }" type="button" role="tab" :aria-selected="trendDays === 30" @click="setTrend(30)">30 天</button></div></header>
      <svg class="trend-chart" viewBox="0 0 600 160" role="img" aria-labelledby="trend-title trend-desc" preserveAspectRatio="none"><desc id="trend-desc">所选日期内每天线上与线下经营金额合计折线</desc><line x1="0" y1="150" x2="600" y2="150" /><polyline :points="trendPoints" /></svg>
      <details class="trend-data"><summary>查看趋势数据表</summary><div class="table-wrap"><table><thead><tr><th>日期</th><th>线上经营</th><th>线下登记</th><th>合计</th></tr></thead><tbody><tr v-for="day in trendRows" :key="day.reportDate"><td data-label="日期">{{ day.reportDate }}</td><td data-label="线上经营">{{ money(day.onlineBookingAmountFen) }}</td><td data-label="线下登记">{{ money(day.offlineRegisteredAmountFen) }}</td><td data-label="合计">{{ money(day.onlineBookingAmountFen + day.offlineRegisteredAmountFen) }}</td></tr></tbody></table></div></details>
    </section>
    <template v-if="canViewSessions">
    <div class="dashboard-schedule">
    <div class="filter-bar" aria-label="日视图筛选">
      <label class="field"><span>日期</span><input v-model="selectedDate" type="date" @change="load"></label>
      <label class="field"><span>运动</span><select v-model="selectedSportId" @change="load"><option value="">全部运动</option><option v-for="sport in sports" :key="sport.id" :value="String(sport.id)">{{ sport.name }}</option></select></label>
      <button class="icon-button filter-action" type="button" aria-label="刷新日视图" title="刷新日视图" :disabled="loading" @click="load"><RefreshCw :size="19" aria-hidden="true" /></button>
    </div>
    <section class="data-panel" aria-label="时间和场地日视图" :aria-busy="loading">
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
              <span v-else class="grid-empty">无场次</span>
            </template>
          </template>
        </div>
      </div>
      <div v-else-if="!loading" class="empty-state"><CalendarDays :size="28" aria-hidden="true" /><strong>这一天没有场次</strong><span>可在“具体场次”中临时加场。</span></div>
      <div v-else class="schedule-skeleton" aria-live="polite" aria-label="正在加载日视图"><span v-for="item in 6" :key="item"></span></div>
      <div v-if="sessions.length" class="mobile-session-list">
        <button v-for="slot in sessions" :key="slot.id" class="mobile-session" type="button" @click="openActions(slot)">
          <time :datetime="slot.startAt">{{ time(slot.startAt) }}–{{ time(slot.endAt) }}</time>
          <strong>{{ slot.courtName }}</strong>
          <span class="status status-with-icon" :data-tone="cellTone(slot)"><component :is="cellIcon(slot)" :size="14" aria-hidden="true" />{{ cellLabel(slot) }}</span>
          <small>{{ slot.sportName }} · {{ money(slot.priceFen) }}</small>
        </button>
      </div>
    </section>

    <dialog ref="drawer" class="drawer">
      <form method="dialog" class="modal-head"><h2>场次操作</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <div v-if="selected" class="drawer-body">
        <p v-if="actionError" class="error-banner" role="alert">{{ actionError }}</p>
        <div class="detail-list">
          <div><span>时间</span><strong>{{ time(selected.startAt) }} - {{ time(selected.endAt) }}</strong></div>
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
    </div>
    </template>
  </section>
</template>

<style scoped>
.dashboard-page { grid-template-columns: minmax(0, 1fr) 16rem; grid-template-areas: "header header" "metrics metrics" "schedule focus" "trend trend"; align-items: start; }
.dashboard-header { grid-area: header; display: grid; gap: var(--space-md); }

.dashboard-page > .dashboard-metrics { grid-area: metrics; }
.dashboard-page > .trend-panel { grid-area: trend; min-width: 0; }
.dashboard-schedule { grid-area: schedule; display: grid; align-content: start; gap: var(--space-md); min-width: 0; }
.dashboard-schedule > .data-panel { min-width: 0; }
.ops-focus { grid-area: focus; display: grid; padding: var(--space-lg); border: 1px solid var(--color-rule); border-radius: var(--radius-card); background: var(--color-surface); }
.ops-focus-lead, .ops-focus-item { display: grid; align-content: start; gap: var(--space-xs); padding-block: var(--space-md); }
.ops-focus-lead { padding-block-start: 0; }
.ops-focus-lead span { font-size: var(--text-base); font-weight: 600; }
.ops-focus-lead strong { font-size: var(--text-sm); font-weight: 500; color: var(--color-muted); }
.ops-focus-lead small, .ops-focus-item small { color: var(--color-muted); font-size: var(--text-xs); }
.ops-focus-item { border-block-start: 1px solid var(--color-rule); grid-template-columns: minmax(0, 1fr) auto; align-items: baseline; }
.ops-focus-item span { font-size: var(--text-sm); }
.ops-focus-item small { grid-column: 1 / -1; }
.ops-focus-item strong { font-size: var(--text-lg); font-weight: 600; font-variant-numeric: tabular-nums; }
.ops-focus-item[data-tone="warning"] strong, .ops-focus-item[data-tone="danger"] strong { color: var(--color-warning-strong); }
.dashboard-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0; border: 1px solid var(--color-rule); background: var(--color-surface); }
.dashboard-metrics > div { min-width: 0; padding: var(--space-lg); border-inline-end: 1px solid var(--color-rule); }
.dashboard-metrics > div:last-child { border: 0; }
.dashboard-metrics dt { color: var(--color-muted); font-size: var(--text-xs); }
.dashboard-metrics dd { margin: var(--space-sm) 0 0; overflow-wrap: anywhere; color: var(--color-ink); font-size: var(--text-2xl); font-weight: 600; font-variant-numeric: tabular-nums; }
.trend-chart { width: 100%; height: 10rem; padding: var(--space-md); }
.trend-chart line { stroke: var(--color-rule); stroke-width: 1; }
.trend-chart polyline { fill: none; stroke: var(--color-primary); stroke-width: 3; vector-effect: non-scaling-stroke; }
.trend-panel :deep(.data-panel-head > .tabs) { display: flex; flex-shrink: 0; width: auto; }
.trend-panel :deep(.tabs .button) { min-width: 56px; padding-inline: var(--space-xs); }
.mobile-session-list { display: none; }
.trend-data { padding: var(--space-md); }
.trend-data summary { cursor: pointer; font-weight: 600; }
.schedule-skeleton { display: grid; gap: var(--space-sm); padding: var(--space-lg); }
.schedule-skeleton span { display: block; height: var(--day-row-height); border-radius: var(--radius-control); background: var(--color-paper-2); }
.without-schedule { grid-template-areas: "header header" "metrics metrics" "trend focus"; }
@media (max-width: 75rem) {
  .dashboard-page, .without-schedule { grid-template-columns: 1fr; grid-template-areas: "header" "metrics" "schedule" "focus" "trend"; }
  .ops-focus { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-md); }
  .ops-focus-lead { grid-column: 1 / -1; }
}
@media (max-width: 40rem) {
  .day-grid-wrap { display: none; }
  .mobile-session-list { display: grid; padding: 0 var(--space-md); }
  .mobile-session { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: var(--space-xs); padding: var(--space-md) 0; border: 0; border-bottom: 1px solid var(--color-rule); background: transparent; color: var(--color-ink); text-align: left; cursor: pointer; }
  .mobile-session time { font-weight: 600; font-variant-numeric: tabular-nums; }
  .mobile-session strong { text-align: right; font-size: var(--text-sm); }
  .mobile-session small { color: var(--color-muted); text-align: right; }
  .ops-focus { grid-template-columns: 1fr; }

  .dashboard-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dashboard-metrics > div { padding: var(--space-md); }
  .dashboard-metrics dd { font-size: var(--text-xl); }
  .ops-focus { padding: var(--space-md); }
}
</style>
