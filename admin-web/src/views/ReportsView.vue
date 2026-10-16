<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Download, Search } from "@lucide/vue";
import { api, localDateTime, money, percent, preciseYuan, today } from "../api";
import { auth, hasPermission } from "../auth";
import { statusLabel, statusTone } from "../labels";
import type { OperationReport, PlatformMemberOperation, PlatformVenue, ReportBooking, ReportMemberFlow, ReportReceipt, ReportUtilization as BaseReportUtilization, ServiceFeeSettlement } from "../types";

type DataStatus = "KNOWN" | "UNAVAILABLE" | "DATA_INSUFFICIENT";
type UtilizationCell = BaseReportUtilization["heatmap"][number] & { dataStatus: DataStatus };
type UtilizationCourt = BaseReportUtilization["courts"][number] & { dataStatus: DataStatus };
type ReportUtilization = Omit<BaseReportUtilization, "heatmap" | "courts"> & {
  heatmap: UtilizationCell[]; courts: UtilizationCourt[]; futureHeatmap: UtilizationCell[];
  futureCourts: UtilizationCourt[]; courtDetailsIncluded: boolean; sports: Record<string, string>;
};

type View = "operations" | "utilization" | "bookings" | "member-flows" | "receipts" | "member-operations" | "service-fees";

const activeView = ref<View>("operations");
const endDate = ref(today());
const startDate = ref(`${endDate.value.slice(0, 7)}-01`);
const billingMonth = ref(endDate.value.slice(0, 7));
const venueId = ref("");
const venues = ref<PlatformVenue[]>([]);
const report = ref<OperationReport>(emptyReport());
const trendPoints = computed(() => {
  const values = report.value.daily.map(day => day.onlineBookingAmountFen + day.offlineRegisteredAmountFen);
  const max = Math.max(...values, 1);
  return values.map((value, index) => `${values.length === 1 ? 300 : index * 600 / (values.length - 1)},${150 - value * 130 / max}`).join(" ");
});
const bookings = ref<ReportBooking[]>([]);
const memberFlows = ref<ReportMemberFlow[]>([]);
const receipts = ref<ReportReceipt[]>([]);
const memberOperations = ref<PlatformMemberOperation[]>([]);
const settlement = ref<ServiceFeeSettlement>(emptySettlement());
const utilization = ref<ReportUtilization>(emptyUtilization());
const utilizationPeriod = ref("history");
const courtOptions = ref<Record<string, string>>({});
const sportOptions = ref<Record<string, string>>({});
const displayedCourts = computed(() => utilizationPeriod.value === "history" ? utilization.value.courts : utilization.value.futureCourts);
const courtId = ref("");
const sportTypeId = ref("");
const drillStart = ref("");
const drillEnd = ref("");
const error = ref("");
const loading = ref(false);
const exporting = ref(false);
const isPlatform = computed(() => auth.principal?.role === "PLATFORM_ADMIN");
const canExport = computed(() => !isPlatform.value && hasPermission("REPORT_EXPORT"));
const title = computed(() => isPlatform.value ? "经营总报表" : "经营报表");
const views = computed<Array<{ value: View; label: string }>>(() => isPlatform.value ? [
  { value: "operations", label: "经营汇总" },
  { value: "utilization", label: "时段分析" },
  { value: "member-operations", label: "会员经营" },
  { value: "service-fees", label: "月度结算" },
] : [
  { value: "operations", label: "经营汇总" },
  { value: "utilization", label: "时段分析" },
  { value: "bookings", label: "预约明细" },
  { value: "member-flows", label: "会员流水" },
  { value: "receipts", label: "每日收款" },
  { value: "service-fees", label: "月度结算" },
]);
const scopeName = computed(() => {
  if (!isPlatform.value) return auth.principal?.displayName || "当前场馆";
  return venues.value.find(item => String(item.venue.id) === venueId.value)?.venue.name || "全部场馆";
});
const maxStartDate = computed(() => {
  const date = new Date(`${endDate.value}T00:00:00+08:00`);
  date.setDate(date.getDate() - 89);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(date);
});

function emptyReport(): OperationReport {
  return {
    onlineBookingAmountFen: 0, offlineRegisteredAmountFen: 0, availableSessionCount: 0,
    bookedSessionCount: 0, utilizationRateBasisPoints: 0, bookingCount: 0, cancelledCount: 0,
    cancellationRateBasisPoints: 0, pendingVerificationCount: 0, offlineUnrecordedCount: 0,
    offlineUnpaidCount: 0, newMemberCount: 0, rechargeInflowFen: 0, balanceConsumptionFen: 0,
    repeatCustomerCount: 0, repeatPurchaseRateBasisPoints: 0, memberAvailableBalanceFen: 0,
    memberFrozenBalanceFen: 0, daily: [],
  };
}

function emptyUtilization(): ReportUtilization {
  return { startDate: "", endDate: "", timeZone: "Asia/Shanghai", generatedAt: "", methodology: "", heatmap: [], courts: [], lowUtilization: [], futureHeatmap: [], futureCourts: [], courtDetailsIncluded: false, sports: {} };
}

function emptySettlement(): ServiceFeeSettlement {
  return { billingMonth: billingMonth.value, currentServiceFeeRateBasisPoints: null, billableBookingCount: 0,
    bookingAmountFen: 0, serviceFeeYuan: "0", venues: [], bookings: [] };
}

function query() {
  const params = new URLSearchParams({ startDate: startDate.value, endDate: endDate.value });
  if (isPlatform.value && venueId.value) params.set("venueId", venueId.value);
  if (!isPlatform.value && courtId.value && ["utilization", "bookings"].includes(activeView.value)) params.set("courtId", courtId.value);
  if (sportTypeId.value && ["utilization", "bookings"].includes(activeView.value)) params.set("sportTypeId", sportTypeId.value);
  if (activeView.value === "bookings" && drillStart.value) params.set("slotStart", drillStart.value);
  if (activeView.value === "bookings" && drillEnd.value) params.set("slotEnd", drillEnd.value);
  return params;
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    if (activeView.value === "service-fees") {
      const params = new URLSearchParams({ billingMonth: billingMonth.value });
      if (isPlatform.value && venueId.value) params.set("venueId", venueId.value);
      settlement.value = await api<ServiceFeeSettlement>(`${isPlatform.value ? "/platform" : "/venue-admin"}/reports/service-fees?${params}`);
    } else if (activeView.value === "member-operations") {
      memberOperations.value = await api<PlatformMemberOperation[]>(`/platform/reports/member-operations?${query()}`);
    } else if (activeView.value === "utilization") {
      utilization.value = await api<ReportUtilization>(`${isPlatform.value ? "/platform" : "/venue-admin"}/reports/utilization?${query()}`);
      sportOptions.value = { ...sportOptions.value, ...utilization.value.sports };
      for (const court of [...utilization.value.courts, ...utilization.value.futureCourts]) courtOptions.value[court.courtId] = court.courtName;
    } else if (activeView.value === "operations") {
      report.value = await api<OperationReport>(`${isPlatform.value ? "/platform" : "/venue-admin"}/reports/operations?${query()}`);
    } else if (activeView.value === "bookings") {
      bookings.value = await api<ReportBooking[]>(`${courtId.value ? "/venue-admin/reports/bookings-filtered" : "/venue-admin/reports/bookings"}?${query()}`);
    } else if (activeView.value === "member-flows") {
      memberFlows.value = await api<ReportMemberFlow[]>(`/venue-admin/reports/member-flows?${query()}`);
    } else {
      receipts.value = await api<ReportReceipt[]>(`/venue-admin/reports/receipts?${query()}`);
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "报表加载失败";
  } finally {
    loading.value = false;
  }
}

async function setView(view: View) {
  activeView.value = view;
  courtId.value = ""; sportTypeId.value = ""; drillStart.value = ""; drillEnd.value = "";
  if (view === "member-operations") venueId.value = "";
  await load();
}

async function download() {
  if (!["bookings", "member-flows", "receipts"].includes(activeView.value)) return;
  exporting.value = true;
  error.value = "";
  try {
    const base = import.meta.env.VITE_API_BASE || "http://127.0.0.1:8089/api";
    const response = await fetch(`${base}/venue-admin/exports/${activeView.value}?${query()}`, {
      headers: { "X-Admin-Token": auth.token },
    });
    if (!response.ok) throw new Error(`导出失败（${response.status}）`);
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = `${activeView.value}-${startDate.value}-${endDate.value}.xlsx`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "导出失败";
  } finally {
    exporting.value = false;
  }
}

function localDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" })
    .format(new Date(`${value}T00:00:00+08:00`));
}

function channelLabel(value: string) {
  return ({ CASH: "现金", WECHAT: "微信", ALIPAY: "支付宝", BALANCE: "会员余额" } as Record<string, string>)[value] || value;
}

function heatCell(day: number, hour: number): UtilizationCell {
  const cells = utilizationPeriod.value === "history" ? utilization.value.heatmap : utilization.value.futureHeatmap;
  return cells.find(item => item.dayOfWeek === day && item.hourOfDay === hour) || { dayOfWeek: day, hourOfDay: hour, availableMinutes: 0, bookedMinutes: 0, utilizationRateBasisPoints: 0, cancelledCount: 0, dataStatus: "DATA_INSUFFICIENT" };
}
function utilizationLabel(cell: { dataStatus: DataStatus; utilizationRateBasisPoints: number }) {
  return cell.dataStatus === "DATA_INSUFFICIENT" ? "数据不足" : cell.dataStatus === "UNAVAILABLE" ? "不可售" : percent(cell.utilizationRateBasisPoints);
}
function heatTitle(cell: UtilizationCell) {
  return `${utilizationLabel(cell)}；可售 ${cell.availableMinutes} 分钟，已预订 ${cell.bookedMinutes} 分钟`;
}
function openDrilldown(item: ReportUtilization["lowUtilization"][number]) {
  courtId.value = String(item.courtId); drillStart.value = item.startTime; drillEnd.value = item.endTime;
  startDate.value = item.slotDate; endDate.value = item.slotDate; activeView.value = "bookings"; load();
}
function clearDrilldown() { courtId.value = ""; sportTypeId.value = ""; drillStart.value = ""; drillEnd.value = ""; load(); }

onMounted(async () => {
  if (isPlatform.value) {
    try { venues.value = await api<PlatformVenue[]>("/platform/venues/list"); }
    catch (reason) { error.value = reason instanceof Error ? reason.message : "场馆列表加载失败"; return; }
  }
  await load();
});
</script>

<template>
  <section class="page reports-page">
    <header class="page-header"><div><h1>{{ title }}</h1><p>{{ scopeName }} · 汇总与明细使用相同日期口径。</p></div></header>
    <div class="tabs report-tabs" role="tablist" aria-label="报表类型">
      <button v-for="view in views" :key="view.value" class="button" :class="{ secondary: activeView !== view.value }" type="button" role="tab" :aria-selected="activeView === view.value" @click="setView(view.value)">{{ view.label }}</button>
    </div>
    <form v-if="activeView !== 'service-fees'" class="toolbar report-filters" @submit.prevent="load">
      <label v-if="isPlatform && activeView !== 'member-operations'" class="field"><span>场馆</span><select v-model="venueId"><option value="">全部场馆</option><option v-for="item in venues" :key="item.venue.id" :value="String(item.venue.id)">{{ item.venue.name }}</option></select></label>
      <label class="field"><span>开始日期</span><input v-model="startDate" type="date" :min="maxStartDate" :max="endDate" required></label>
      <label class="field"><span>结束日期</span><input v-model="endDate" type="date" :min="startDate" :max="activeView === 'utilization' ? undefined : today()" required></label>
      <label v-if="activeView === 'utilization' && !isPlatform" class="field"><span>场地</span><select v-model="courtId"><option value="">全部场地</option><option v-for="(name, id) in courtOptions" :key="id" :value="id">{{ name }}</option></select></label>
      <label v-if="activeView === 'utilization'" class="field"><span>运动</span><select v-model="sportTypeId"><option value="">全部运动</option><option v-for="(name, id) in sportOptions" :key="id" :value="id">{{ name }}</option></select></label>
      <button class="button" type="submit" :disabled="loading"><Search :size="18" aria-hidden="true" />{{ loading ? "查询中" : "查询" }}</button>
      <button v-if="canExport && !(activeView === 'bookings' && (courtId || sportTypeId || drillStart)) && ['bookings', 'member-flows', 'receipts'].includes(activeView)" class="button secondary" type="button" :disabled="exporting" @click="download"><Download :size="18" aria-hidden="true" />{{ exporting ? "导出中" : "导出 Excel" }}</button>
    </form>
    <form v-else class="toolbar report-filters" @submit.prevent="load">
      <label v-if="isPlatform" class="field"><span>场馆</span><select v-model="venueId"><option value="">全部场馆</option><option v-for="item in venues" :key="item.venue.id" :value="String(item.venue.id)">{{ item.venue.name }}</option></select></label>
      <label class="field"><span>月份</span><input v-model="billingMonth" type="month" required></label>
      <button class="button" type="submit" :disabled="loading"><Search :size="18" aria-hidden="true" />{{ loading ? "查询中" : "查询" }}</button>
    </form>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>

    <template v-if="activeView === 'operations'">
      <dl class="report-summary" aria-live="polite">
        <div><dt>线上预约经营金额</dt><dd>{{ money(report.onlineBookingAmountFen) }}</dd></div><div><dt>线下登记经营金额</dt><dd>{{ money(report.offlineRegisteredAmountFen) }}</dd></div><div><dt>场地利用率</dt><dd>{{ percent(report.utilizationRateBasisPoints) }}</dd></div><div><dt>取消率</dt><dd>{{ percent(report.cancellationRateBasisPoints) }}</dd></div><div><dt>充值实付流入</dt><dd>{{ money(report.rechargeInflowFen) }}</dd></div><div><dt>会员余额消费</dt><dd>{{ money(report.balanceConsumptionFen) }}</dd></div><div><dt>复购率</dt><dd>{{ percent(report.repeatPurchaseRateBasisPoints) }}</dd></div><div><dt>会员可用余额</dt><dd>{{ money(report.memberAvailableBalanceFen) }}</dd></div><div><dt>会员冻结余额</dt><dd>{{ money(report.memberFrozenBalanceFen) }}</dd></div>
      </dl>
      <section v-if="report.daily.length" class="data-panel" aria-label="经营金额趋势"><header class="data-panel-head"><div><h2>经营趋势</h2><p>所选日期的线上经营与线下登记金额合计</p></div></header><svg class="report-trend" viewBox="0 0 600 160" role="img" aria-label="经营金额趋势，逐日金额见下方经营日报" preserveAspectRatio="none"><line x1="0" y1="150" x2="600" y2="150" /><polyline :points="trendPoints" /></svg></section>
      <section class="data-panel" aria-label="经营日报"><header class="data-panel-head"><div><h2>经营日报</h2><p>{{ startDate }} 至 {{ endDate }}</p></div><span class="record-count">{{ report.daily.length }} 天</span></header><div class="table-wrap"><table><thead><tr><th>日期</th><th class="numeric-column">线上经营</th><th class="numeric-column">线下登记</th><th>利用率</th><th>取消率</th><th>待核销</th><th>线下未记录</th><th>线下未付</th><th>新增会员</th><th class="numeric-column">充值流入</th><th class="numeric-column">余额消费</th></tr></thead><tbody><tr v-for="day in report.daily" :key="day.reportDate"><td data-label="日期"><strong>{{ localDate(day.reportDate) }}</strong></td><td data-label="线上经营" class="numeric-column">{{ money(day.onlineBookingAmountFen) }}</td><td data-label="线下登记" class="numeric-column">{{ money(day.offlineRegisteredAmountFen) }}</td><td data-label="利用率">{{ percent(day.utilizationRateBasisPoints) }}</td><td data-label="取消率">{{ percent(day.cancellationRateBasisPoints) }}</td><td data-label="待核销">{{ day.pendingVerificationCount }}</td><td data-label="线下未记录">{{ day.offlineUnrecordedCount }}</td><td data-label="线下未付">{{ day.offlineUnpaidCount }}</td><td data-label="新增会员">{{ day.newMemberCount }}</td><td data-label="充值流入" class="numeric-column">{{ money(day.rechargeInflowFen) }}</td><td data-label="余额消费" class="numeric-column">{{ money(day.balanceConsumptionFen) }}</td></tr></tbody></table></div><div v-if="!loading && !report.daily.length" class="empty-state"><strong>所选日期内没有经营数据</strong><span>调整日期或场馆后重新查询。</span></div></section>
    </template>

    <section v-else-if="activeView === 'utilization'" class="utilization-view">
      <div class="tabs report-period-tabs"><button class="button secondary" :class="{ secondary: utilizationPeriod !== 'history' }" :aria-pressed="utilizationPeriod === 'history'" @click="utilizationPeriod = 'history'">历史利用率（截至昨日）</button><button class="button secondary" :class="{ secondary: utilizationPeriod !== 'future' }" :aria-pressed="utilizationPeriod === 'future'" @click="utilizationPeriod = 'future'">今日及未来供给</button></div>
      <p v-if="utilizationPeriod === 'future'" class="muted">仅展示当前排期与已确认预订，不代表已发生履约；低利用行动只使用历史。</p>
      <section class="data-panel" aria-label="星期小时利用率热力图"><header class="data-panel-head"><div><h2>{{ utilizationPeriod === "history" ? "星期 × 小时利用率" : "星期 × 小时预订分布" }}</h2><p>{{ utilization.startDate }} 至 {{ utilization.endDate }} · {{ utilization.timeZone }} · {{ utilization.methodology }}</p></div><span class="record-count">生成于 {{ utilization.generatedAt ? localDateTime(utilization.generatedAt) : "—" }}</span></header>
        <div class="table-wrap heatmap-wrap"><table class="heatmap"><thead><tr><th>星期 / 小时</th><th v-for="hour in 24" :key="hour">{{ String(hour - 1).padStart(2, "0") }}时</th></tr></thead><tbody><tr v-for="day in 7" :key="day"><th>{{ ["周一", "周二", "周三", "周四", "周五", "周六", "周日"][day - 1] }}</th><td v-for="hour in 24" :key="hour" :class="{ unavailable: heatCell(day, hour - 1).dataStatus !== 'KNOWN' }" :style="{ '--heat': heatCell(day, hour - 1).dataStatus === 'KNOWN' ? heatCell(day, hour - 1).utilizationRateBasisPoints / 150 : 0 }" tabindex="0" :aria-label="`${day} 星期 ${hour - 1} 时，${heatTitle(heatCell(day, hour - 1))}`" :title="heatTitle(heatCell(day, hour - 1))"><span>{{ utilizationLabel(heatCell(day, hour - 1)) }}</span></td></tr></tbody></table></div>
      </section>
      <section v-if="utilization.courtDetailsIncluded" class="data-panel" aria-label="场地表现"><header class="data-panel-head"><div><h2>场地表现</h2><p>按可售分钟计算，取消沿用预约日期口径</p></div><span class="record-count">{{ displayedCourts.length }} 个场地</span></header><div class="table-wrap"><table><thead><tr><th>场地</th><th>可售分钟</th><th>已预订分钟</th><th>利用率</th><th>预约数</th><th>取消数</th></tr></thead><tbody><tr v-for="court in displayedCourts" :key="court.courtId"><td data-label="场地"><strong>{{ court.courtName }}</strong></td><td data-label="可售分钟">{{ court.availableMinutes }}</td><td data-label="已预订分钟">{{ court.bookedMinutes }}</td><td data-label="利用率">{{ utilizationLabel(court) }}</td><td data-label="预约数">{{ court.bookingCount }}</td><td data-label="取消数">{{ court.cancelledCount }}</td></tr></tbody></table></div><div v-if="!loading && !displayedCourts.length" class="empty-state"><strong>所选范围没有可售场次</strong><span>关闭场次不会计入空闲。</span></div></section>
      <section v-if="utilization.courtDetailsIncluded && utilizationPeriod === 'history'" class="data-panel" aria-label="低利用时段"><header class="data-panel-head"><div><h2>低利用时段</h2><p>利用率不超过 30%，点击可保留日期条件查看预约明细</p></div><span class="record-count">{{ utilization.lowUtilization.length }} 条</span></header><div class="table-wrap"><table><thead><tr><th>日期</th><th>场地</th><th>时段</th><th>可售分钟</th><th>已预订分钟</th><th>利用率</th><th>取消数</th><th>操作</th></tr></thead><tbody><tr v-for="item in utilization.lowUtilization" :key="`${item.courtId}-${item.slotDate}-${item.startTime}`"><td data-label="日期">{{ item.slotDate }}</td><td data-label="场地">{{ item.courtName }}</td><td data-label="时段">{{ item.startTime.slice(11, 16) }}–{{ item.endTime.slice(0, 10) !== item.slotDate ? '次日 ' + item.endTime.slice(11, 16) : item.endTime.slice(11, 16) }}</td><td data-label="可售分钟">{{ item.availableMinutes }}</td><td data-label="已预订分钟">{{ item.bookedMinutes }}</td><td data-label="利用率">{{ percent(item.utilizationRateBasisPoints) }}</td><td data-label="取消数">{{ item.cancelledCount }}</td><td data-label="操作"><button v-if="!isPlatform" class="button secondary" type="button" @click="openDrilldown(item)">查看预约</button><span v-else class="muted">场馆权限可下钻</span></td></tr></tbody></table></div><div v-if="!loading && !utilization.lowUtilization.length" class="empty-state"><strong>没有低利用时段</strong><span>当前范围暂无可售但低利用的时段。</span></div></section>
    </section>

    <section v-else-if="activeView === 'bookings'" class="data-panel" aria-label="预约明细"><header class="data-panel-head"><div><h2>预约明细</h2><p>线上经营与线下登记构成</p><p v-if="drillStart">下钻：{{ courtOptions[courtId] }} · {{ drillStart }} 至 {{ drillEnd }} · {{ sportOptions[sportTypeId] || "全部运动" }}<button class="button secondary" @click="clearDrilldown">清除下钻条件</button></p></div><span class="record-count">{{ bookings.length }} 笔</span></header><div class="table-wrap"><table><thead><tr><th>预约单号</th><th>来源</th><th>联系人</th><th>场地与场次</th><th>状态</th><th class="numeric-column">预约金额</th><th class="numeric-column">线下登记</th><th class="numeric-column">余额消费</th><th class="numeric-column">外部支付</th></tr></thead><tbody><tr v-for="item in bookings" :key="item.bookingNo"><td data-label="预约单号"><strong>{{ item.bookingNo }}</strong></td><td data-label="来源">{{ item.source === "ONLINE" ? "线上" : "线下" }}</td><td data-label="联系人">{{ item.contactName }} · {{ item.contactPhone }}</td><td data-label="场地与场次">{{ item.courtName }} · {{ item.sportName }}<br><span class="muted">{{ item.sessionDate }} {{ item.startTime }}-{{ item.endTime }}</span></td><td data-label="状态"><span class="status" :data-tone="statusTone(item.status)">{{ statusLabel(item.status) }}</span></td><td data-label="预约金额" class="numeric-column">{{ money(item.amountFen) }}</td><td data-label="线下登记" class="numeric-column">{{ money(item.offlineRegisteredAmountFen) }}</td><td data-label="余额消费" class="numeric-column">{{ money(item.balanceConsumptionFen) }}</td><td data-label="外部支付" class="numeric-column">{{ money(item.externalPaymentFen) }}</td></tr></tbody></table></div><div v-if="!loading && !bookings.length" class="empty-state"><strong>没有预约明细</strong></div></section>

    <section v-else-if="activeView === 'member-flows'" class="data-panel" aria-label="会员流水"><header class="data-panel-head"><div><h2>会员流水</h2><p>充值、赠送与余额变动明细</p></div><span class="record-count">{{ memberFlows.length }} 笔</span></header><div class="table-wrap"><table><thead><tr><th>时间</th><th>会员码</th><th>业务</th><th>关联单号</th><th>操作人</th><th>方向</th><th class="numeric-column">金额</th></tr></thead><tbody><tr v-for="item in memberFlows" :key="item.flowId"><td data-label="时间">{{ localDateTime(item.occurredAt) }}</td><td data-label="会员码">{{ item.memberCode }}</td><td data-label="业务">{{ statusLabel(item.businessType) }}</td><td data-label="关联单号">{{ item.bookingNo || item.rechargeNo || "—" }}</td><td data-label="操作人">{{ item.operatorName || "系统" }}</td><td data-label="方向">{{ item.direction === "CREDIT" ? "入账" : "出账" }}</td><td data-label="金额" class="numeric-column">{{ money(item.amountFen) }}</td></tr></tbody></table></div><div v-if="!loading && !memberFlows.length" class="empty-state"><strong>没有会员流水</strong></div></section>

    <section v-else-if="activeView === 'receipts'" class="data-panel" aria-label="每日收款"><header class="data-panel-head"><div><h2>每日真实收款</h2><p>按实际成功日期、操作员工与渠道汇总</p></div><span class="record-count">{{ receipts.length }} 行</span></header><div class="table-wrap"><table><thead><tr><th>日期</th><th>操作员工</th><th>渠道</th><th>业务</th><th>笔数</th><th class="numeric-column">金额</th></tr></thead><tbody><tr v-for="(item, index) in receipts" :key="`${item.receiptDate}-${item.operatorName}-${item.channel}-${item.businessType}-${index}`"><td data-label="日期">{{ localDate(item.receiptDate) }}</td><td data-label="操作员工">{{ item.operatorName || "系统" }}</td><td data-label="渠道">{{ channelLabel(item.channel) }}</td><td data-label="业务">{{ statusLabel(item.businessType) }}</td><td data-label="笔数">{{ item.count }}</td><td data-label="金额" class="numeric-column"><strong>{{ money(item.amountFen) }}</strong></td></tr></tbody></table></div><div v-if="!loading && !receipts.length" class="empty-state"><strong>没有真实收款记录</strong></div></section>

    <section v-else-if="activeView === 'member-operations'" class="data-panel" aria-label="平台会员经营"><header class="data-panel-head"><div><h2>场馆会员经营</h2><p>平台仅展示场馆级汇总，不展示个人明细</p></div><span class="record-count">{{ memberOperations.length }} 家</span></header><div class="table-wrap"><table><thead><tr><th>场馆</th><th>顾客</th><th>会员</th><th>新增会员</th><th>复购</th><th>复购率</th><th class="numeric-column">充值流入</th><th class="numeric-column">余额消费</th><th class="numeric-column">可用余额</th><th class="numeric-column">冻结余额</th></tr></thead><tbody><tr v-for="item in memberOperations" :key="item.venueId"><td data-label="场馆"><strong>{{ item.venueName }}</strong></td><td data-label="顾客">{{ item.customerCount }}</td><td data-label="会员">{{ item.memberCount }}</td><td data-label="新增会员">{{ item.newMemberCount }}</td><td data-label="复购">{{ item.repeatCustomerCount }}</td><td data-label="复购率">{{ percent(item.repeatPurchaseRateBasisPoints) }}</td><td data-label="充值流入" class="numeric-column">{{ money(item.rechargeInflowFen) }}</td><td data-label="余额消费" class="numeric-column">{{ money(item.balanceConsumptionFen) }}</td><td data-label="可用余额" class="numeric-column">{{ money(item.memberAvailableBalanceFen) }}</td><td data-label="冻结余额" class="numeric-column">{{ money(item.memberFrozenBalanceFen) }}</td></tr></tbody></table></div></section>

    <template v-else>
      <dl class="report-summary" aria-live="polite"><div><dt>可计费预约</dt><dd>{{ settlement.billableBookingCount }}</dd></div><div><dt>预约总金额</dt><dd>{{ money(settlement.bookingAmountFen) }}</dd></div><div><dt>平台服务费</dt><dd>{{ preciseYuan(settlement.serviceFeeYuan) }}</dd></div><div v-if="settlement.currentServiceFeeRateBasisPoints !== null"><dt>当前服务费率</dt><dd>{{ percent(settlement.currentServiceFeeRateBasisPoints) }}</dd></div></dl>
      <section v-if="isPlatform && !venueId" class="data-panel" aria-label="场馆服务费汇总"><header class="data-panel-head"><div><h2>场馆汇总</h2><p>{{ billingMonth }}</p></div><span class="record-count">{{ settlement.venues.length }} 家</span></header><div class="table-wrap"><table><thead><tr><th>场馆</th><th class="numeric-column">当前费率</th><th>可计费预约</th><th class="numeric-column">预约总金额</th><th class="numeric-column">平台服务费</th></tr></thead><tbody><tr v-for="venue in settlement.venues" :key="venue.venueId"><td data-label="场馆"><strong>{{ venue.venueName }}</strong></td><td data-label="当前费率" class="numeric-column">{{ percent(venue.currentServiceFeeRateBasisPoints) }}</td><td data-label="可计费预约">{{ venue.billableBookingCount }}</td><td data-label="预约总金额" class="numeric-column">{{ money(venue.bookingAmountFen) }}</td><td data-label="平台服务费" class="numeric-column">{{ preciseYuan(venue.serviceFeeYuan) }}</td></tr></tbody></table></div></section>
      <section class="data-panel" aria-label="平台服务费明细"><header class="data-panel-head"><div><h2>逐笔明细</h2><p>{{ billingMonth }} · {{ scopeName }}</p></div><span class="record-count">{{ settlement.bookings.length }} 笔</span></header><div class="table-wrap"><table><thead><tr><th>预约单号</th><th v-if="isPlatform">场馆</th><th>场地与场次</th><th>状态</th><th class="numeric-column">预约金额</th><th class="numeric-column">费率快照</th><th class="numeric-column">平台服务费</th></tr></thead><tbody><tr v-for="booking in settlement.bookings" :key="booking.bookingNo"><td data-label="预约单号"><strong>{{ booking.bookingNo }}</strong></td><td v-if="isPlatform" data-label="场馆">{{ booking.venueName }}</td><td data-label="场地与场次">{{ booking.courtName }}<br><span class="muted">{{ localDateTime(booking.startAt) }}</span></td><td data-label="状态"><span class="status" :data-tone="statusTone(booking.status)">{{ statusLabel(booking.status) }}</span></td><td data-label="预约金额" class="numeric-column">{{ money(booking.amountFen) }}</td><td data-label="费率快照" class="numeric-column">{{ percent(booking.serviceFeeRateBasisPoints) }}</td><td data-label="平台服务费" class="numeric-column"><strong>{{ preciseYuan(booking.serviceFeeYuan) }}</strong></td></tr></tbody></table></div></section>
    </template>
  </section>
</template>

<style scoped>
.report-filters .field { flex: 0 1 200px; }
.heatmap-wrap { overflow-x: auto; }
.heatmap { min-width: 62rem; }
.heatmap td { min-width: 2.6rem; padding: .35rem; text-align: center; background: color-mix(in srgb, var(--color-accent) calc(var(--heat, 0) * 1%), var(--color-surface)); }
.heatmap td.unavailable { color: var(--color-muted); background: var(--color-surface-2); }
.heatmap th { white-space: nowrap; }
.report-tabs { width: 100%; }
.report-tabs .button, .report-filters .button { min-height: 2.75rem; }
.report-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 0; gap: 1px; overflow: hidden; border: 1px solid var(--color-rule); border-radius: var(--radius-card); background: var(--color-rule); box-shadow: var(--shadow-raised); }
.report-summary > div { min-width: 0; min-height: 112px; padding: 22px 24px; background: var(--color-surface); }
.report-summary dt { color: var(--color-muted); font-size: 13px; font-weight: 400; }
.report-summary dd { margin: 10px 0 0; overflow-wrap: anywhere; font-family: var(--font-numeric); font-size: 28px; line-height: 1.3; font-variant-numeric: tabular-nums; font-weight: 600; }
@media (max-width: 59.999rem) { .report-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.utilization-view { display: grid; gap: 20px; min-width: 0; }
.heatmap td:focus-visible { outline: 2px solid var(--color-focus); outline-offset: -2px; }
@media (max-width: 40rem) { .heatmap-wrap { overflow-x: auto; padding: 0; } .heatmap, .heatmap thead { display: table; position: static; width: auto; height: auto; clip-path: none; } .heatmap thead { display: table-header-group; } .heatmap tbody { display: table-row-group; } .heatmap tr { display: table-row; padding: 0; } .heatmap td, .heatmap th { display: table-cell; } .heatmap td::before { display: none; } }
.report-trend { display: block; width: 100%; height: 180px; padding: 20px; }
.report-trend line { stroke: var(--color-rule); }
.report-trend polyline { fill: none; stroke: var(--color-primary); stroke-width: 3; vector-effect: non-scaling-stroke; }
@media (max-width: 40rem) { .report-summary > div { padding: 18px 16px; } .report-summary dd { font-size: 22px; } .report-filters .field { flex: 1 1 8rem; } }
@media (max-width: 30rem) { .report-summary { grid-template-columns: 1fr; } }
</style>
