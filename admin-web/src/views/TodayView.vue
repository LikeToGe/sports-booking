<script setup lang="ts">
import { requireText } from "../validation";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { Ban, CalendarDays, CircleAlert, CircleCheck, CircleX, ClipboardList, Clock3, ArrowRight, RefreshCw, X } from "@lucide/vue";
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
const reportLoading = ref(false);
const reportError = ref("");
const saving = ref(false);
const reportDays = ref<7 | 30>(7);
let sessionsRequest = 0;
let reportRequest = 0;
const selected = ref<SessionView>();
const detailDialog = ref<HTMLDialogElement>();
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

// 此刻按场馆所在的上海时区计算，与 today() 的日期口径一致，格式为 YYYY-MM-DDTHH:mm。
const now = ref(shanghaiNow());
let clockTimer: number | undefined;
const isToday = computed(() => selectedDate.value === now.value.slice(0, 10));
const nowTime = computed(() => now.value.slice(11, 16));

// 一个场地可支持多种运动，表头按当日场次出现过的运动逐个标出。
const courts = computed(() => {
  const courtMap = new Map<number, { id: number; name: string; sportNames: string[] }>();
  for (const slot of sessions.value) {
    const court = courtMap.get(slot.courtId) || { id: slot.courtId, name: slot.courtName, sportNames: [] };
    if (!court.sportNames.includes(slot.sportName)) court.sportNames.push(slot.sportName);
    courtMap.set(slot.courtId, court);
  }
  return [...courtMap.values()];
});
const times = computed(() => [...new Set(sessions.value.map(item => item.startAt))].sort());
// 同一开场时间可能对应不同时长的场次，行结束时间取其中最晚的场次结束时间。
const rowEnds = computed(() => {
  const ends = new Map<string, string>();
  for (const slot of sessions.value) {
    const current = ends.get(slot.startAt);
    if (!current || slot.endAt > current) ends.set(slot.startAt, slot.endAt);
  }
  return ends;
});

// 当日占用按场次数统计（已预约 / 未关闭场次），与报表按可售分钟计算的场地利用率口径不同。
const occupancy = computed(() => {
  let sellable = 0;
  let booked = 0;
  for (const slot of sessions.value) {
    if (slot.status === "CLOSED") continue;
    sellable += 1;
    if (slot.status === "BOOKED") booked += 1;
  }
  return { sellable, booked, rate: sellable ? Math.round(booked * 100 / sellable) : 0 };
});

// 下一轮开场只看今天此刻之后最近的开场时间，已关闭场次不计入。
const nextRound = computed(() => {
  if (!isToday.value) return undefined;
  const upcoming = sessions.value.filter(slot => slot.status !== "CLOSED" && slot.startAt.slice(0, 16) > now.value);
  if (!upcoming.length) return undefined;
  const startAt = upcoming.map(slot => slot.startAt).sort()[0]!;
  return { time: time(startAt), count: upcoming.filter(slot => slot.startAt === startAt).length,
    minutes: minutesOf(startAt) - minutesOf(now.value) };
});

// 即将到场展示今天未来 2 小时内开场的已预约场次，点击进入同一场次操作弹窗。
const arrivals = computed(() => {
  if (!isToday.value) return [];
  const nowMinutes = minutesOf(now.value);
  return sessions.value
    .filter(slot => slot.status === "BOOKED" && slot.startAt.slice(0, 16) > now.value && minutesOf(slot.startAt) - nowMinutes <= 120)
    .sort((left, right) => left.startAt.localeCompare(right.startAt))
    .slice(0, 5);
});
const dateParts = computed(() => {
  const date = new Date(`${selectedDate.value}T12:00:00`);
  return { day: selectedDate.value.slice(8), month: Number(selectedDate.value.slice(5, 7)),
    weekday: new Intl.DateTimeFormat("zh-CN", { weekday: "long" }).format(date) };
});
const reportRange = computed(() => {
  const daily = report.value?.daily;
  return daily?.length ? `${daily[0]!.reportDate.slice(5).replace("-", ".")} — ${daily[daily.length - 1]!.reportDate.slice(5).replace("-", ".")}` : "暂无统计数据";
});
const sessionMap = computed(() => new Map(sessions.value.map(item => [`${item.startAt}:${item.courtId}`, item])));
const trendRows = computed(() => report.value?.daily.slice(-reportDays.value) || []);
const trendBars = computed(() => {
  const values = trendRows.value.map(day => day.onlineBookingAmountFen + day.offlineRegisteredAmountFen);
  const max = Math.max(...values, 1);
  const step = 280 / Math.max(values.length, 1);
  return values.map((value, index) => ({ x: index * step + step * .15, width: step * .7, height: Math.max(value * 84 / max, 2) }));
});

function shanghaiNow() {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(new Date()).replace(" ", "T");
}

function minutesOf(value: string) {
  return Number(value.slice(11, 13)) * 60 + Number(value.slice(14, 16));
}

// 今天已结束的时段淡化，进行中的时段在时间列标出此刻线，其他日期不显示。
function rowPhase(startAt: string) {
  if (!isToday.value) return "";
  const endAt = rowEnds.value.get(startAt) || startAt;
  if (endAt.slice(0, 10) === now.value.slice(0, 10) && endAt.slice(0, 16) <= now.value) return "past";
  return startAt.slice(0, 16) <= now.value ? "now" : "";
}

function nowProgress(startAt: string) {
  const endAt = rowEnds.value.get(startAt) || startAt;
  const start = minutesOf(startAt);
  const end = endAt.slice(0, 10) === startAt.slice(0, 10) ? minutesOf(endAt) : minutesOf(endAt) + 1440;
  return `${Math.min(Math.max((minutesOf(now.value) - start) * 100 / Math.max(end - start, 1), 0), 100)}%`;
}

function courtStrip(courtId: number) {
  return times.value.filter(startAt => sessionMap.value.has(`${startAt}:${courtId}`))
    .map(startAt => sessionMap.value.get(`${startAt}:${courtId}`)!.status === "BOOKED");
}

function untilText(minutes: number) {
  if (minutes < 60) return `${minutes} 分钟后`;
  const rest = minutes % 60;
  return rest ? `${Math.floor(minutes / 60)} 小时 ${rest} 分后` : `${minutes / 60} 小时后`;
}


async function loadReport() {
  const request = ++reportRequest;
  const days = trendDays.value;
  reportLoading.value = true;
  reportError.value = "";
  try {
    const result = await api<OperationReport>(`/venue-admin/reports/dashboard?days=${days}`);
    if (request !== reportRequest) return;
    report.value = result;
    reportDays.value = days;
  } catch (reason) {
    if (request === reportRequest) reportError.value = reason instanceof Error ? reason.message : "经营指标加载失败";
  } finally {
    if (request === reportRequest) reportLoading.value = false;
  }
}

async function setTrend(days: 7 | 30) {
  trendDays.value = days;
  await loadReport();
}

async function load() {
  if (!selectedDate.value) {
    ++sessionsRequest;
    sessions.value = [];
    selected.value = undefined;
    loading.value = false;
    error.value = "请选择日期";
    return;
  }
  const request = ++sessionsRequest;
  loading.value = true;
  error.value = "";
  try {
    const query = new URLSearchParams({ date: selectedDate.value });
    if (selectedSportId.value) query.set("sportTypeId", selectedSportId.value);
    const [slots, sportTypes] = await Promise.all([
      api<SessionView[]>(`/venue-admin/sessions/list?${query}`),
      sports.value.length ? Promise.resolve(sports.value) : api<SportType[]>("/venue-admin/sports/list"),
    ]);
    if (request !== sessionsRequest) return;
    sessions.value = slots;
    sports.value = sportTypes;
    selected.value = slots.find(slot => slot.id === selected.value?.id);
  } catch (reason) {
    if (request === sessionsRequest) {
      sessions.value = [];
      selected.value = undefined;
      error.value = reason instanceof Error ? reason.message : "日视图加载失败";
    }
  } finally {
    if (request === sessionsRequest) loading.value = false;
  }
}

function time(value: string) {
  return value.slice(11, 16);
}

// 颜色与图标跟随当前展示的场次状态，避免已支付覆盖已取消等履约结果。
function cellStatus(slot: SessionView) {
  if (["OPEN", "CLOSED", "HELD"].includes(slot.status)) return slot.status;
  if (slot.bookingSource === "OFFLINE") return slot.paymentStatus || "UNRECORDED";
  return slot.bookingStatus || slot.status;
}

function cellLabel(slot: SessionView) {
  const state = cellStatus(slot);
  if (!["OPEN", "CLOSED", "HELD"].includes(slot.status) && slot.bookingSource === "OFFLINE") return `线下 · ${statusLabel(state)}`;
  return statusLabel(state);
}

function cellTone(slot: SessionView) {
  if (slot.status === "BOOKED" && slot.bookingSource === "ONLINE" && slot.bookingStatus === "CONFIRMED") return "info";
  return statusTone(cellStatus(slot));
}

function cellIcon(slot: SessionView) {
  const state = cellStatus(slot);
  if (["OPEN", "COMPLETED", "PAID"].includes(state)) return CircleCheck;
  if (["CANCELLED", "CLOSED"].includes(state)) return CircleX;
  if (["NO_SHOW", "PAYMENT_FAILED", "REFUND_FAILED"].includes(state)) return CircleAlert;
  if (state === "ENDED_UNTRACKED") return Ban;
  return Clock3;
}

// 排场色块按实际展示状态分类：线下按登记收款、线上按履约状态，异常结果优先，不让历史收款覆盖已取消等履约结果。
function cellKind(slot: SessionView) {
  if (slot.status === "OPEN") return "open";
  if (slot.status === "CLOSED") return "closed";
  if (slot.status === "HELD") return "held";
  if (cellTone(slot) === "danger") return "alert";
  if (slot.bookingSource === "OFFLINE") {
    if (slot.paymentStatus === "PAID") return "offline-paid";
    return ["UNPAID", "UNRECORDED"].includes(slot.paymentStatus || "UNRECORDED") ? "offline-due" : "done";
  }
  if (slot.bookingStatus === "CONFIRMED") return "due";
  if (slot.bookingStatus === "PENDING_PAYMENT") return "held";
  return "done";
}

function openActions(slot: SessionView) {
  selected.value = slot;
  contactName.value = slot.contactName || "";
  contactPhone.value = slot.contactPhone || "";
  paymentStatus.value = slot.paymentStatus === "UNRECORDED" ? "UNPAID" : slot.paymentStatus || "UNPAID";
  paymentReason.value = "";
  actionError.value = "";
  detailDialog.value?.showModal();
}

async function createOffline() {
  if (!selected.value) return;
  await perform(async () => {
    requireText(contactName.value, "联系人姓名");
    requireText(contactPhone.value, "联系电话");
    return api("/venue-admin/bookings/offline", {
      method: "POST",
      body: JSON.stringify({ sessionSlotId: selected.value?.id, contactName: contactName.value,
        contactPhone: contactPhone.value, paymentStatus: paymentStatus.value }),
    });
  });
}

async function savePaymentStatus() {
  if (!selected.value?.bookingId) return;
  await perform(async () => {
    requireText(paymentReason.value, "收款状态修改原因", 255);
    return api(`/venue-admin/bookings/${selected.value?.bookingId}/payment-status`, {
      method: "PUT", body: JSON.stringify({ paymentStatus: paymentStatus.value, reason: paymentReason.value }),
    });
  });
}

async function changeSlot(action: "open" | "close") {
  if (!selected.value) return;
  await perform(async () => api(`/venue-admin/sessions/${selected.value?.id}/${action}`, { method: "POST" }));
}

async function perform(action: () => Promise<unknown>) {
  if (saving.value) return;
  saving.value = true;
  actionError.value = "";
  try {
    await action();
    detailDialog.value?.close();
    await load();
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : "操作失败";
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  clockTimer = window.setInterval(() => { now.value = shanghaiNow(); }, 30_000);
  return Promise.all([canViewSessions ? load() : Promise.resolve(), loadReport()]);
});
onUnmounted(() => window.clearInterval(clockTimer));
</script>

<template>
  <section class="page dashboard-page" :class="{ 'without-schedule': !canViewSessions }">
    <header class="today-head">
      <div class="today-title">
        <p class="today-crumb">场馆工作台 · 今日运营</p>
        <h1>今日运营</h1>
        <p class="today-meta">
          <span>{{ dateParts.month }} 月 {{ dateParts.day }} 日 {{ dateParts.weekday }}</span>
          <span v-if="isToday" class="live-now"><time :datetime="now">此刻 {{ nowTime }}</time></span>
          <span v-if="nextRound">下一轮 {{ nextRound.time }} 开场 {{ nextRound.count }} 场 · {{ untilText(nextRound.minutes) }}</span>
        </p>
      </div>
      <div class="today-actions">
        <RouterLink v-if="canViewSessions" to="/sessions" class="button secondary"><Clock3 :size="16" aria-hidden="true" />管理具体场次</RouterLink>
        <RouterLink v-if="hasPermission('BOOKING_VIEW')" to="/bookings" class="button"><ClipboardList :size="16" aria-hidden="true" />查看预约</RouterLink>
      </div>
    </header>

    <section class="kpi-row" aria-label="经营核心指标" :aria-busy="reportLoading">
      <div v-if="canViewSessions" class="kpi kpi-hero" :aria-busy="loading">
        <span class="kpi-label">{{ isToday ? "今日" : `${dateParts.month} 月 ${dateParts.day} 日` }}场次占用</span>
        <strong class="kpi-value">{{ occupancy.rate }}%</strong>
        <span class="kpi-note">已预约 {{ occupancy.booked }} / 未关闭 {{ occupancy.sellable }} 场</span>
        <span class="kpi-ring" role="img" :aria-label="`占用 ${occupancy.rate}%`" :style="{ '--rate': String(occupancy.rate) }"></span>
      </div>
      <div class="kpi">
        <span class="kpi-label">线上预约经营 · 近 {{ reportDays }} 天</span>
        <strong class="kpi-value">{{ report ? money(report.onlineBookingAmountFen) : "—" }}</strong>
        <span class="kpi-note">按场次日期统计 · {{ reportRange }}</span>
        <span v-if="trendBars.length" class="kpi-spark" aria-hidden="true"><i v-for="(bar, index) in trendBars" :key="index" :style="{ height: `${bar.height * 100 / 84}%` }"></i></span>
      </div>
      <div class="kpi">
        <span class="kpi-label">线下登记经营金额</span>
        <strong class="kpi-value">{{ report ? money(report.offlineRegisteredAmountFen) : "—" }}</strong>
        <span class="kpi-note">人工登记，非平台实收</span>
      </div>
      <div class="kpi">
        <span class="kpi-label">会员余额消费</span>
        <strong class="kpi-value">{{ report ? money(report.balanceConsumptionFen) : "—" }}</strong>
        <span class="kpi-note">{{ report ? `新增会员 ${report.newMemberCount} 位` : "近 7 / 30 天统计" }}</span>
      </div>
    </section>

    <div class="courtside-layout">
      <section v-if="canViewSessions" class="data-panel schedule-panel" aria-label="时间和场地日视图" :aria-busy="loading">
        <header class="data-panel-head schedule-head">
          <div class="schedule-title"><h2>场地日视图</h2><span class="record-count">{{ loading ? "加载中…" : `${courts.length} 块场地 · ${sessions.length} 场` }}</span></div>
          <div class="schedule-tools" aria-label="日视图筛选">
            <label class="field"><span class="visually-hidden">日期</span><input v-model="selectedDate" type="date" required @change="load"></label>
            <label class="field"><span class="visually-hidden">运动</span><select v-model="selectedSportId" @change="load"><option value="">全部运动</option><option v-for="sport in sports" :key="sport.id" :value="String(sport.id)">{{ sport.name }}</option></select></label>
            <button class="icon-button" type="button" aria-label="刷新日视图" title="刷新日视图" :disabled="loading" @click="load"><RefreshCw :size="18" :class="{ spin: loading }" aria-hidden="true" /></button>
          </div>
        </header>
        <div class="schedule-legend"><span data-kind="open">可预约</span><span data-kind="due">线上待核销</span><span data-kind="offline-paid">线下已支付</span><span data-kind="offline-due">线下未支付 / 未记录</span><span data-kind="held">支付占位</span><span data-kind="done">已完成及其他</span><span data-kind="alert">取消 / 异常</span><span data-kind="closed">已关闭</span></div>
        <p v-if="error" class="error-banner schedule-error" role="alert">{{ error }}<button class="inline-command" type="button" @click="load">重试</button></p>
        <div v-if="loading" class="schedule-skeleton" aria-live="polite" aria-label="正在加载日视图"><span v-for="item in 5" :key="item"></span></div>
        <div v-else-if="sessions.length" class="day-grid-wrap" tabindex="0" role="region" aria-label="排场，可左右滚动查看所有场地">
          <div class="day-grid" :style="{ '--court-count': String(courts.length) }">
            <div class="grid-corner">时间</div>
            <div v-for="(court, index) in courts" :key="court.id" class="grid-court" :class="{ 'is-selected': selected?.courtId === court.id }">
              <strong>{{ String(index + 1).padStart(2, '0') }}</strong>
              <span class="court-name">{{ court.name }}</span>
              <span class="court-sports"><span v-for="sportName in court.sportNames" :key="sportName" class="sport-tag">{{ sportName }}</span></span>
              <span class="court-strip" aria-hidden="true"><i v-for="(booked, slotIndex) in courtStrip(court.id)" :key="slotIndex" :class="{ booked }"></i></span>
            </div>
            <template v-for="startAt in times" :key="startAt">
              <time class="grid-time" :class="{ 'is-selected': selected?.startAt === startAt, 'is-past': rowPhase(startAt) === 'past', 'is-now': rowPhase(startAt) === 'now' }" :style="rowPhase(startAt) === 'now' ? { '--now-progress': nowProgress(startAt) } : undefined" :datetime="startAt">{{ time(startAt) }}<span v-if="rowPhase(startAt) === 'now'" class="now-marker">此刻 {{ nowTime }}</span></time>
              <template v-for="court in courts" :key="court.id">
                <button v-if="sessionMap.get(`${startAt}:${court.id}`)" class="slot-cell" :class="{ 'is-selected': selected?.id === sessionMap.get(`${startAt}:${court.id}`)?.id, 'is-past': rowPhase(startAt) === 'past' }" :data-kind="cellKind(sessionMap.get(`${startAt}:${court.id}`)!)" :data-status="sessionMap.get(`${startAt}:${court.id}`)?.status" :aria-pressed="selected?.id === sessionMap.get(`${startAt}:${court.id}`)?.id" :aria-label="`${court.name} ${time(startAt)}–${time(sessionMap.get(`${startAt}:${court.id}`)!.endAt)} ${cellLabel(sessionMap.get(`${startAt}:${court.id}`)!)} ${money(sessionMap.get(`${startAt}:${court.id}`)?.priceFen)}`" type="button" @click="openActions(sessionMap.get(`${startAt}:${court.id}`)!)">
                  <strong class="slot-state"><component :is="cellIcon(sessionMap.get(`${startAt}:${court.id}`)!)" :size="13" aria-hidden="true" />{{ cellLabel(sessionMap.get(`${startAt}:${court.id}`)!) }}</strong>
                  <span class="slot-bottom"><span>{{ time(sessionMap.get(`${startAt}:${court.id}`)!.endAt) }} 结束</span><strong>{{ money(sessionMap.get(`${startAt}:${court.id}`)?.priceFen) }}</strong></span>
                </button>
                <span v-else class="grid-empty">无场次</span>
              </template>
            </template>
          </div>
        </div>
        <div v-else-if="!error" class="empty-state"><CalendarDays :size="28" aria-hidden="true" /><strong>这一天没有场次</strong><span>可调整日期或运动筛选。</span><RouterLink v-if="canEditSessions" to="/sessions" class="button secondary">管理具体场次</RouterLink></div>
        <footer class="schedule-footer"><span>线下金额为人工登记，不代表平台实际收款。点击场次查看详情与操作。</span><RouterLink v-if="canViewSessions" to="/sessions">管理具体场次<ArrowRight :size="14" aria-hidden="true" /></RouterLink></footer>
      </section>

      <aside class="operations-rail" aria-label="待处理与经营概况" :aria-busy="reportLoading">
        <section v-if="report" class="rail-card" aria-labelledby="todo-title">
          <header class="rail-head"><h2 id="todo-title">待处理</h2><span>{{ priorityCount ? `共 ${priorityCount} 项` : "暂无" }} · 近 {{ reportDays }} 天</span></header>
          <ul class="todo-list">
            <li><span class="todo-count" data-kind="due">{{ report.pendingVerificationCount }}</span><div><strong>线上待核销</strong><span>顾客到场后完成核销</span></div><RouterLink v-if="hasPermission('BOOKING_VIEW')" to="/bookings">处理<ArrowRight :size="13" aria-hidden="true" /></RouterLink></li>
            <li><span class="todo-count" data-kind="held">{{ report.offlineUnpaidCount }}</span><div><strong>线下未支付</strong><span>登记实际收款状态</span></div><RouterLink v-if="hasPermission('BOOKING_VIEW')" to="/bookings">处理<ArrowRight :size="13" aria-hidden="true" /></RouterLink></li>
            <li><span class="todo-count">{{ report.offlineUnrecordedCount }}</span><div><strong>线下待登记</strong><span>补录收款事实</span></div><RouterLink v-if="hasPermission('BOOKING_VIEW')" to="/bookings">处理<ArrowRight :size="13" aria-hidden="true" /></RouterLink></li>
          </ul>
        </section>

        <section v-if="isToday && canViewSessions" class="rail-card" aria-labelledby="arrivals-title">
          <header class="rail-head"><h2 id="arrivals-title">即将到场</h2><span>未来 2 小时</span></header>
          <div v-if="arrivals.length" class="arrival-list">
            <button v-for="slot in arrivals" :key="slot.id" class="arrival" type="button" @click="openActions(slot)">
              <time :datetime="slot.startAt">{{ time(slot.startAt) }}</time>
              <span><strong>{{ slot.courtName }}</strong><small>{{ slot.sportName }}{{ slot.contactName ? ` · ${slot.contactName}` : slot.bookingSource === "ONLINE" ? " · 线上预约" : "" }}</small></span>
              <span class="status" :data-tone="cellTone(slot)">{{ cellLabel(slot) }}</span>
            </button>
          </div>
          <p v-else class="rail-empty">未来 2 小时没有已预约场次。</p>
        </section>

        <section class="rail-card overview" aria-labelledby="overview-title">
          <header class="rail-head"><div><h2 id="overview-title">经营概况</h2><span class="report-range">{{ reportRange }}</span></div><div class="segmented" role="group" aria-label="统计区间"><span class="segment-indicator" :style="{ transform: trendDays === 30 ? 'translateX(100%)' : 'translateX(0)' }"></span><button v-for="days in ([7, 30] as const)" :key="days" type="button" :aria-pressed="trendDays === days" @click="setTrend(days)">近 {{ days }} 天</button></div></header>
          <p v-if="reportLoading" class="report-loading" role="status">正在加载近 {{ trendDays }} 天数据…</p>
          <p v-if="reportError" class="error-banner report-error" role="alert">{{ reportError }}<button type="button" class="inline-command" @click="loadReport">重试</button></p>
          <div v-if="report" :key="reportDays" class="overview-content">
            <p v-if="reportLoading || reportError" class="report-period">当前显示近 {{ reportDays }} 天的上次成功数据 · {{ reportRange }}</p>
            <div class="overview-total"><span>线上经营 + 线下登记</span><strong>{{ money(report.onlineBookingAmountFen + report.offlineRegisteredAmountFen) }}</strong></div>
            <svg class="trend-chart" viewBox="0 0 280 90" role="img" aria-labelledby="trend-desc" preserveAspectRatio="none"><desc id="trend-desc">所选区间内每天线上经营与线下登记金额合计，明细见下方趋势数据表</desc><defs><linearGradient id="trend-latest" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#00E5FF" /><stop offset="1" stop-color="#A855F7" /></linearGradient></defs><rect v-for="(bar, index) in trendBars" :key="index" :class="{ latest: index === trendBars.length - 1 }" :x="bar.x" :y="90 - bar.height" :width="bar.width" :height="bar.height" rx="2" /></svg>
            <div v-if="trendRows.length" class="chart-axis"><span>{{ trendRows[0]!.reportDate.slice(5) }}</span><span>{{ trendRows[trendRows.length - 1]!.reportDate.slice(5) }}</span></div>
            <dl class="overview-split" aria-label="经营补充指标">
              <div><dt>场地利用率</dt><dd>{{ percent(report.utilizationRateBasisPoints) }}</dd><small>按可售分钟计算</small></div>
              <div><dt>充值实付流入</dt><dd>{{ money(report.rechargeInflowFen) }}</dd><small>不含充值赠送金额</small></div>
            </dl>
          </div>
          <div v-else-if="reportLoading" class="schedule-skeleton" aria-label="正在加载经营概况"><span v-for="item in 3" :key="item"></span></div>
          <details v-if="report" class="trend-data"><summary>查看趋势数据表</summary><div class="table-wrap"><table><thead><tr><th>日期</th><th class="numeric-column">线上经营</th><th class="numeric-column">线下登记</th><th class="numeric-column">合计</th></tr></thead><tbody><tr v-for="day in trendRows" :key="day.reportDate"><td data-label="日期">{{ day.reportDate }}</td><td data-label="线上经营" class="numeric-column">{{ money(day.onlineBookingAmountFen) }}</td><td data-label="线下登记" class="numeric-column">{{ money(day.offlineRegisteredAmountFen) }}</td><td data-label="合计" class="numeric-column">{{ money(day.onlineBookingAmountFen + day.offlineRegisteredAmountFen) }}</td></tr></tbody></table></div></details>
        </section>
      </aside>
    </div>
    <dialog ref="detailDialog" class="modal" aria-labelledby="todayview-detailDialog-title">
      <form method="dialog" class="modal-head session-modal-head"><div><h2 id="todayview-detailDialog-title">{{ selected ? `${time(selected.startAt)} – ${time(selected.endAt)}` : "场次操作" }}</h2><p v-if="selected">{{ selected.courtName }} · {{ selected.sportName }} · {{ selected.startAt.slice(0, 10) }}</p></div><span v-if="selected" class="status" :data-tone="cellTone(selected)">{{ cellLabel(selected) }}</span><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <div v-if="selected" class="modal-body" :aria-busy="saving">
        <p v-if="actionError" class="error-banner" role="alert">{{ actionError }}</p>
        <div class="detail-list">
          <div><span>时间</span><strong>{{ time(selected.startAt) }} - {{ time(selected.endAt) }}</strong></div>
          <div><span>场地</span><strong>{{ selected.courtName }} · {{ selected.sportName }}</strong></div>
          <div><span>场次金额</span><strong>{{ money(selected.priceFen) }}</strong></div>
        </div>
        <form v-if="selected.status === 'OPEN' && canEditBookings" class="modal-form" @submit.prevent="createOffline">
          <h3>创建线下预约</h3>
          <label class="field"><span>联系人</span><input v-model.trim="contactName" required></label>
          <label class="field"><span>手机号</span><input v-model.trim="contactPhone" inputmode="tel" required></label>
          <label class="field"><span>登记收款状态</span><select v-model="paymentStatus"><option value="UNPAID">未支付</option><option value="PAID">已支付</option></select></label>
          <button class="button" type="submit" :disabled="saving">{{ saving ? "创建中…" : "创建线下预约" }}</button>
        </form>
        <form v-else-if="selected.bookingSource === 'OFFLINE' && selected.bookingId && canEditOfflinePayment" class="modal-form" @submit.prevent="savePaymentStatus">
          <h3>保存收款状态</h3>
          <label class="field"><span>新状态</span><select v-model="paymentStatus"><option v-if="selected.paymentStatus === 'UNRECORDED'" value="UNPAID">未支付</option><option v-if="['UNRECORDED', 'UNPAID'].includes(selected.paymentStatus || '')" value="PAID">已支付</option><option v-if="selected.bookingStatus === 'CANCELLED' && selected.paymentStatus === 'PAID'" value="REFUNDED">已退款</option></select></label>
          <label class="field"><span>修改原因</span><textarea v-model.trim="paymentReason" required></textarea></label>
          <button class="button" type="submit" :disabled="saving">{{ saving ? "保存中…" : "保存收款状态" }}</button>
        </form>
        <p class="form-note">线下预约只登记收款事实，不代表平台实际收款或退款。</p>
        <button v-if="canEditSessions && selected.status === 'OPEN'" class="button danger" type="button" :disabled="saving" @click="changeSlot('close')">关闭场次</button>
        <button v-if="canEditSessions && selected.status === 'CLOSED'" class="button secondary" type="button" :disabled="saving" @click="changeSlot('open')">重新开放场次</button>
      </div>
    </dialog>
  </section>
</template>

<style scoped>
.dashboard-page { gap: 18px; }

/* 页头：标题、当日身份与此刻信息，主操作靠右。 */
.today-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 16px; }
.today-title { display: grid; gap: 4px; min-width: 0; }
.today-crumb { color: var(--color-muted); font-size: 12px; }
.today-title h1 { font-size: 28px; font-weight: 800; letter-spacing: .01em; }
.today-meta { display: flex; flex-wrap: wrap; gap: 4px 14px; color: var(--color-muted); font-size: 13px; }
.live-now { display: inline-flex; align-items: center; gap: 6px; color: var(--color-live); font-weight: 600; font-variant-numeric: tabular-nums; }
.live-now::before { content: ""; width: 7px; height: 7px; border-radius: 50%; background: var(--color-live); box-shadow: 0 0 10px var(--color-live); animation: live-pulse 1.6s infinite; }
.today-actions { display: flex; flex-wrap: wrap; gap: 10px; }

/* 指标行：首卡以霓虹渐变与环形占用作为页面焦点。 */
.kpi-row { display: grid; grid-template-columns: minmax(0, 1.3fr) repeat(3, minmax(0, 1fr)); gap: 14px; }
.kpi { position: relative; display: grid; align-content: start; gap: 6px; min-width: 0; min-height: 132px; padding: 18px 20px; overflow: hidden; border-radius: var(--radius-card); background: var(--color-surface-glass); box-shadow: var(--shadow-raised); backdrop-filter: blur(10px); }
.kpi-label { color: var(--color-muted); font-size: 12px; }
.kpi-value { font: 700 30px/1.1 var(--font-numeric); letter-spacing: -.02em; overflow-wrap: anywhere; text-shadow: 0 0 18px rgb(0 229 255 / 45%); }
.kpi-note { color: var(--color-muted); font-size: 12px; }
.kpi-hero { padding-right: 120px; background: linear-gradient(135deg, rgb(0 229 255 / 22%), rgb(168 85 247 / 28%)), #0E1630; box-shadow: var(--shadow-glow); }
.kpi-hero .kpi-label, .kpi-hero .kpi-note { color: #A9B8E6; }
.kpi-ring { position: absolute; top: 50%; right: 22px; width: 78px; height: 78px; border-radius: 50%; background: conic-gradient(#00E5FF, #A855F7 calc(var(--rate) * 1%), rgb(120 160 255 / 14%) 0); filter: drop-shadow(0 0 10px rgb(0 229 255 / 50%)); transform: translateY(-50%); -webkit-mask: radial-gradient(circle, transparent 30px, #000 31px); mask: radial-gradient(circle, transparent 30px, #000 31px); }
.kpi-spark { display: flex; align-items: flex-end; gap: 3px; height: 26px; margin-top: 4px; }
.kpi-spark i { flex: 1; min-height: 2px; border-radius: 2px; background: rgb(0 229 255 / 22%); }
.kpi-spark i:last-child { background: var(--color-neon); box-shadow: 0 0 8px var(--color-neon-glow); }

/* 排场与右侧经营区。 */
.courtside-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; align-items: start; gap: 18px; min-width: 0; }
.schedule-panel { background: var(--color-surface-glass); backdrop-filter: blur(10px); }
.schedule-head { flex-wrap: wrap; padding: 18px 20px 14px; border: 0; }
.schedule-head .schedule-title { display: flex; align-items: baseline; gap: 10px; }
.schedule-head h2 { font-size: 15px; font-weight: 700; }
.schedule-tools { display: flex; align-items: center; gap: 8px; }
.schedule-tools .field:first-child { width: 150px; }
.schedule-tools .field:nth-child(2) { width: 120px; }
.schedule-tools input, .schedule-tools select { min-height: 34px; font-size: 12px; }
.schedule-legend { display: flex; flex-wrap: wrap; gap: 8px 16px; padding: 0 20px 14px; color: var(--color-muted); font-size: 12px; }
.schedule-legend span { display: inline-flex; align-items: center; gap: 6px; }
.schedule-legend span::before { content: ""; width: 10px; height: 10px; border-radius: 3px; }
.schedule-legend [data-kind="open"]::before { background: var(--color-success-soft); box-shadow: inset 0 0 0 1px var(--color-success); }
.schedule-legend [data-kind="due"]::before { background: linear-gradient(135deg, #00E5FF, #3B82F6); }
.schedule-legend [data-kind="offline-paid"]::before { background: linear-gradient(135deg, #A855F7, #6D28D9); }
.schedule-legend [data-kind="offline-due"]::before { box-shadow: inset 0 0 0 1.5px var(--color-live); }
.schedule-legend [data-kind="held"]::before { background: var(--color-warning-soft); box-shadow: inset 0 0 0 1px var(--color-warning); }
.schedule-legend [data-kind="done"]::before { background: rgb(120 160 255 / 12%); }
.schedule-legend [data-kind="alert"]::before { background: var(--color-danger-soft); box-shadow: inset 0 0 0 1px var(--color-danger); }
.schedule-legend [data-kind="closed"]::before { background: repeating-linear-gradient(135deg, transparent 0 2px, var(--color-subtle) 2px 3px); }
.schedule-error { margin: 0 20px 20px; }
/* 此刻线从时间列横贯整行，overflow: clip 让它不撑大横向滚动范围。 */
.day-grid { grid-template-columns: 70px repeat(var(--court-count), minmax(132px, 1fr)); min-width: 100%; overflow: clip; border-top: 1px solid var(--color-rule); }
.grid-corner, .grid-court { position: static; min-height: 0; border-right: 0; border-bottom-color: var(--color-rule); background: rgb(16 24 52 / 90%); }
.grid-corner { position: sticky; left: 0; color: var(--color-muted); font-size: 11px; }
.grid-court { display: grid; place-items: stretch; align-content: start; gap: 4px; padding: 12px 10px; border-left: 1px solid var(--color-rule-soft); text-align: left; }
.grid-court strong { font: 700 16px/1 var(--font-score); }
.court-name { color: var(--color-ink-2); font-size: 12px; font-weight: 500; overflow-wrap: anywhere; }
.court-sports { display: flex; flex-wrap: wrap; gap: 4px; }
.sport-tag { padding: 0 6px; border-radius: 4px; background: var(--color-primary-soft); color: var(--color-highlight-ink); font-size: 10px; font-weight: 600; line-height: 16px; white-space: nowrap; }
.court-strip { display: flex; gap: 2px; margin-top: 6px; }
.court-strip i { flex: 1; height: 4px; border-radius: 1px; background: var(--color-rule); }
.court-strip i.booked { background: var(--color-neon); box-shadow: 0 0 6px var(--color-neon-glow); }
.grid-court.is-selected { background: var(--color-primary-soft); box-shadow: inset 0 -2px var(--color-neon); }
.grid-time { min-height: 74px; place-items: start end; padding: 10px 10px 0; border-right: 0; background: transparent; font: 500 12px var(--font-numeric); }
.grid-time.is-selected { background: var(--color-primary-soft); color: var(--color-highlight-ink); font-weight: 700; }
.grid-time.is-past { color: var(--color-subtle); }
.grid-time.is-now { color: var(--color-live); font-weight: 700; }
.grid-time.is-now::before { content: ""; position: absolute; top: var(--now-progress); left: 54px; width: 8px; height: 8px; border-radius: 50%; background: var(--color-live); box-shadow: 0 0 10px var(--color-live); transform: translateY(-3px); pointer-events: none; }
.grid-time.is-now::after { content: ""; position: absolute; top: var(--now-progress); left: 60px; width: 200vw; height: 2px; background: var(--color-live); box-shadow: 0 0 8px var(--color-live); pointer-events: none; }
.now-marker { display: block; margin-top: 4px; padding: 0 5px; border-radius: 4px; background: var(--color-live); color: #FFFFFF; font: 700 10px/18px var(--font-numeric); white-space: nowrap; }
.grid-empty { min-height: 74px; border-left: 1px solid var(--color-rule-soft); background: transparent; color: var(--color-subtle); font-size: 11px; }
.slot-cell { min-height: 62px; margin: 5px; padding: 8px 10px; align-content: space-between; gap: 8px; border: 0; border-radius: 8px; font-size: 12px; transition: transform var(--dur-micro) var(--ease-out), box-shadow var(--dur-short) var(--ease-out), opacity var(--dur-short) var(--ease-out); }
.slot-state { padding-right: 4px; font-weight: 600; }
.slot-cell .slot-bottom { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: baseline; gap: 6px; width: 100%; color: inherit; font-size: 11px; }
.slot-bottom > span { color: inherit; opacity: .78; font-size: 11px; }
.slot-bottom strong { margin-left: auto; color: inherit; font: 700 13px/1.1 var(--font-numeric); white-space: nowrap; }
.slot-cell[data-kind="open"] { background: var(--color-success-soft); color: var(--color-success); box-shadow: inset 0 0 0 1px rgb(51 240 180 / 22%); }
.slot-cell[data-kind="due"] { background: linear-gradient(135deg, #00E5FF, #3B82F6); color: var(--color-neon-ink); box-shadow: 0 0 18px -6px rgb(0 229 255 / 70%); }
.slot-cell[data-kind="offline-paid"] { background: linear-gradient(135deg, #A855F7, #6D28D9); color: #FFFFFF; box-shadow: 0 0 18px -6px rgb(168 85 247 / 70%); }
.slot-cell[data-kind="offline-due"] { background: transparent; color: var(--color-live); box-shadow: inset 0 0 0 1.5px var(--color-live); }
.slot-cell[data-kind="held"] { background: var(--color-warning-soft); color: var(--color-warning); }
.slot-cell[data-kind="done"] { background: rgb(120 160 255 / 6%); color: #6A78A0; }
.slot-cell[data-kind="alert"] { background: var(--color-danger-soft); color: var(--color-danger-strong); box-shadow: inset 0 0 0 1px rgb(255 92 122 / 30%); }
.slot-cell[data-kind="closed"] { background: repeating-linear-gradient(135deg, transparent 0 6px, rgb(120 160 255 / 10%) 6px 7px); color: var(--color-subtle); box-shadow: inset 0 0 0 1px var(--color-rule); }
.slot-cell.is-past { opacity: .5; }
.slot-cell.is-selected { box-shadow: 0 0 0 2px var(--color-paper), 0 0 0 4px var(--color-neon); }
.schedule-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 20px; border-top: 1px solid var(--color-rule); color: var(--color-muted); font-size: 12px; }
.schedule-footer a { display: inline-flex; align-items: center; flex-shrink: 0; gap: 6px; min-height: 28px; color: var(--color-highlight-ink); text-decoration: none; font-size: 12px; font-weight: 600; }

.operations-rail { display: grid; gap: 14px; min-width: 0; }
.rail-card { min-width: 0; overflow: hidden; border-radius: var(--radius-card); background: var(--color-surface-glass); box-shadow: var(--shadow-raised); backdrop-filter: blur(10px); }
.rail-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; padding: 16px 20px 12px; }
.rail-head h2 { font-size: 15px; font-weight: 700; }
.rail-head > span, .report-range { color: var(--color-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.todo-list { margin: 0; padding: 0; list-style: none; }
.todo-list li { display: flex; align-items: center; gap: 12px; padding: 12px 20px; border-top: 1px solid var(--color-rule-soft); }
.todo-list li > div { display: grid; flex: 1; min-width: 0; gap: 2px; }
.todo-list strong { font-size: 13px; font-weight: 600; }
.todo-list li > div > span { color: var(--color-muted); font-size: 12px; }
.todo-list a { display: inline-flex; align-items: center; gap: 4px; min-height: 32px; color: var(--color-ink-2); font-size: 12px; font-weight: 600; text-decoration: none; }
.todo-count { display: grid; flex: none; place-items: center; width: 34px; height: 34px; border-radius: 9px; background: rgb(120 160 255 / 10%); color: var(--color-ink-2); font: 700 14px var(--font-numeric); }
.todo-count[data-kind="due"] { background: var(--color-primary-soft); color: var(--color-highlight-ink); }
.todo-count[data-kind="held"] { background: var(--color-warning-soft); color: var(--color-warning); }
.arrival-list { display: grid; gap: 8px; padding: 0 16px 16px; }
.arrival { display: grid; grid-template-columns: 48px minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 52px; padding: 8px 12px; border: 0; border-radius: 10px; background: rgb(120 160 255 / 6%); box-shadow: inset 0 0 0 1px var(--color-rule-soft); color: var(--color-ink); text-align: left; }
.arrival time { font: 700 14px var(--font-numeric); }
.arrival > span:nth-child(2) { display: grid; min-width: 0; }
.arrival strong { overflow: hidden; font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.arrival small { overflow: hidden; color: var(--color-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.rail-empty { padding: 0 20px 18px; color: var(--color-muted); font-size: 12px; }
.segmented { position: relative; display: flex; flex-shrink: 0; padding: 3px; border-radius: 10px; background: rgb(120 160 255 / 8%); }
.segmented button { position: relative; z-index: 1; width: 62px; min-height: 28px; padding: 0; border: 0; background: transparent; color: var(--color-muted); font-size: 12px; transition: color var(--dur-short) var(--ease-out); }
.segmented button[aria-pressed="true"] { color: var(--color-highlight-ink); font-weight: 700; }
.segment-indicator { position: absolute; top: 3px; bottom: 3px; left: 3px; width: 62px; border-radius: 7px; background: var(--color-primary-soft); box-shadow: inset 0 0 0 1px rgb(0 229 255 / 35%); transition: transform var(--dur-short) var(--ease-move); }
.report-loading, .report-period { margin: 0 20px 12px; color: var(--color-muted); font-size: 11px; }
.report-error { margin: 0 20px 12px; }
.overview-content { animation: content-enter var(--dur-short) var(--ease-out); }
.overview-total { display: grid; gap: 4px; padding: 0 20px; }
.overview-total span { color: var(--color-muted); font-size: 12px; }
.overview-total strong { font: 700 28px/1.1 var(--font-numeric); overflow-wrap: anywhere; text-shadow: 0 0 18px rgb(0 229 255 / 40%); }
.trend-chart { display: block; width: calc(100% - 40px); height: 90px; margin: 14px 20px 0; }
.trend-chart rect { fill: rgb(0 229 255 / 22%); }
.trend-chart rect.latest { fill: url(#trend-latest); }
.chart-axis { display: flex; justify-content: space-between; margin: 6px 20px 0; color: var(--color-muted); font-size: 11px; }
.overview-split { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 16px 0 0; border-top: 1px solid var(--color-rule-soft); }
.overview-split > div { padding: 12px 20px; }
.overview-split > div + div { border-left: 1px solid var(--color-rule-soft); }
.overview-split dt { color: var(--color-muted); font-size: 11px; }
.overview-split dd { margin: 2px 0; font: 700 17px/1.2 var(--font-numeric); overflow-wrap: anywhere; }
.overview-split small { color: var(--color-subtle); font-size: 11px; }
.trend-data { padding: 12px 20px 16px; border-top: 1px solid var(--color-rule-soft); color: var(--color-muted); font-size: 12px; }
.trend-data summary { cursor: pointer; min-height: 28px; }
.trend-data .table-wrap { margin-top: 12px; }
.trend-data table { min-width: 480px; }
.schedule-skeleton { display: grid; gap: 12px; padding: 20px; }
.schedule-skeleton span { display: block; height: 62px; border-radius: 8px; background: rgb(120 160 255 / 8%); animation: loading-pulse 1s ease-in-out infinite alternate; }
.session-modal-head { align-items: flex-start; }
.session-modal-head > div { display: grid; flex: 1; min-width: 0; gap: 4px; }
.session-modal-head h2 { font: 700 24px/1 var(--font-numeric); }
.session-modal-head p { color: var(--color-frame-muted); font-size: 12px; }
.form-note { color: var(--color-muted); font-size: 12px; }
.without-schedule .kpi-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.without-schedule .courtside-layout { grid-template-columns: minmax(0, 1fr); }
.without-schedule .operations-rail { grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr)); }
@keyframes live-pulse { 50% { opacity: .3; } }
@keyframes loading-pulse { to { opacity: .5; } }
@media (max-width: 1320px) { .kpi-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 1150px) {
  .courtside-layout { grid-template-columns: minmax(0, 1fr); }
  .operations-rail { grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr)); align-items: start; }
}
@media (max-width: 600px) {
  .kpi-row, .without-schedule .kpi-row { grid-template-columns: minmax(0, 1fr); }
  .today-actions { width: 100%; }
  .today-actions .button { flex: 1; }
  .schedule-tools { width: 100%; gap: 6px; }
  .schedule-tools .field:first-child { flex: 1.2; width: 0; }
  .schedule-tools .field:nth-child(2) { flex: 1; width: 0; }
  .schedule-head, .schedule-legend { padding-inline: 16px; }
  .schedule-footer { align-items: start; padding-inline: 16px; }
  .trend-data table { min-width: 0; }
  .trend-data .table-wrap { padding: 0; }
}
@media (prefers-reduced-motion: reduce) { .live-now::before { animation: none; } }
@media (hover: hover) and (pointer: fine) {
  .slot-cell:hover { transform: translateY(-1px); }
  .arrival:hover { background: rgb(120 160 255 / 12%); }
  .todo-list a:hover { color: var(--color-ink); }
}
</style>
