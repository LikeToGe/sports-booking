<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Download, Search } from "@lucide/vue";
import { api, localDateTime, money, percent, preciseYuan, today } from "../api";
import { auth, hasPermission } from "../auth";
import { statusLabel, statusTone } from "../labels";
import type { OperationReport, PlatformMemberOperation, PlatformVenue, ReportBooking, ReportMemberFlow, ReportReceipt, ServiceFeeSettlement } from "../types";

type View = "operations" | "bookings" | "member-flows" | "receipts" | "member-operations" | "service-fees";

const activeView = ref<View>("operations");
const endDate = ref(today());
const startDate = ref(`${endDate.value.slice(0, 7)}-01`);
const billingMonth = ref(endDate.value.slice(0, 7));
const venueId = ref("");
const venues = ref<PlatformVenue[]>([]);
const report = ref<OperationReport>(emptyReport());
const bookings = ref<ReportBooking[]>([]);
const memberFlows = ref<ReportMemberFlow[]>([]);
const receipts = ref<ReportReceipt[]>([]);
const memberOperations = ref<PlatformMemberOperation[]>([]);
const settlement = ref<ServiceFeeSettlement>(emptySettlement());
const error = ref("");
const loading = ref(false);
const exporting = ref(false);
const isPlatform = computed(() => auth.principal?.role === "PLATFORM_ADMIN");
const canExport = computed(() => !isPlatform.value && hasPermission("REPORT_EXPORT"));
const title = computed(() => isPlatform.value ? "经营总报表" : "经营报表");
const views = computed<Array<{ value: View; label: string }>>(() => isPlatform.value ? [
  { value: "operations", label: "经营汇总" },
  { value: "member-operations", label: "会员经营" },
  { value: "service-fees", label: "月度结算" },
] : [
  { value: "operations", label: "经营汇总" },
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

function emptySettlement(): ServiceFeeSettlement {
  return { billingMonth: billingMonth.value, currentServiceFeeRateBasisPoints: null, billableBookingCount: 0,
    bookingAmountFen: 0, serviceFeeYuan: "0", venues: [], bookings: [] };
}

function query() {
  const params = new URLSearchParams({ startDate: startDate.value, endDate: endDate.value });
  if (isPlatform.value && venueId.value) params.set("venueId", venueId.value);
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
    } else if (activeView.value === "operations") {
      report.value = await api<OperationReport>(`${isPlatform.value ? "/platform" : "/venue-admin"}/reports/operations?${query()}`);
    } else if (activeView.value === "bookings") {
      bookings.value = await api<ReportBooking[]>(`/venue-admin/reports/bookings?${query()}`);
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

onMounted(async () => {
  if (isPlatform.value) {
    try { venues.value = await api<PlatformVenue[]>("/platform/venues/list"); }
    catch (reason) { error.value = reason instanceof Error ? reason.message : "场馆列表加载失败"; return; }
  }
  await load();
});
</script>

<template>
  <section class="page">
    <header class="page-header"><div><h1>{{ title }}</h1><p>{{ scopeName }} · 汇总与明细使用相同日期口径。</p></div></header>
    <div class="tabs report-tabs" role="tablist" aria-label="报表类型">
      <button v-for="view in views" :key="view.value" class="button" :class="{ secondary: activeView !== view.value }" type="button" role="tab" :aria-selected="activeView === view.value" @click="setView(view.value)">{{ view.label }}</button>
    </div>
    <form v-if="activeView !== 'service-fees'" class="toolbar report-filters" @submit.prevent="load">
      <label v-if="isPlatform && activeView !== 'member-operations'" class="field"><span>场馆</span><select v-model="venueId"><option value="">全部场馆</option><option v-for="item in venues" :key="item.venue.id" :value="String(item.venue.id)">{{ item.venue.name }}</option></select></label>
      <label class="field"><span>开始日期</span><input v-model="startDate" type="date" :min="maxStartDate" :max="endDate" required></label>
      <label class="field"><span>结束日期</span><input v-model="endDate" type="date" :min="startDate" :max="today()" required></label>
      <button class="button" type="submit" :disabled="loading"><Search :size="18" aria-hidden="true" />{{ loading ? "查询中" : "查询" }}</button>
      <button v-if="canExport && ['bookings', 'member-flows', 'receipts'].includes(activeView)" class="button secondary" type="button" :disabled="exporting" @click="download"><Download :size="18" aria-hidden="true" />{{ exporting ? "导出中" : "导出 Excel" }}</button>
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
      <section class="data-panel" aria-label="经营日报"><header class="data-panel-head"><div><h2>经营日报</h2><p>{{ startDate }} 至 {{ endDate }}</p></div><span class="record-count">{{ report.daily.length }} 天</span></header><div class="table-wrap"><table><thead><tr><th>日期</th><th>线上经营</th><th>线下登记</th><th>利用率</th><th>取消率</th><th>待核销</th><th>线下未记录</th><th>线下未付</th><th>新增会员</th><th>充值流入</th><th>余额消费</th></tr></thead><tbody><tr v-for="day in report.daily" :key="day.reportDate"><td data-label="日期"><strong>{{ localDate(day.reportDate) }}</strong></td><td data-label="线上经营">{{ money(day.onlineBookingAmountFen) }}</td><td data-label="线下登记">{{ money(day.offlineRegisteredAmountFen) }}</td><td data-label="利用率">{{ percent(day.utilizationRateBasisPoints) }}</td><td data-label="取消率">{{ percent(day.cancellationRateBasisPoints) }}</td><td data-label="待核销">{{ day.pendingVerificationCount }}</td><td data-label="线下未记录">{{ day.offlineUnrecordedCount }}</td><td data-label="线下未付">{{ day.offlineUnpaidCount }}</td><td data-label="新增会员">{{ day.newMemberCount }}</td><td data-label="充值流入">{{ money(day.rechargeInflowFen) }}</td><td data-label="余额消费">{{ money(day.balanceConsumptionFen) }}</td></tr></tbody></table></div><div v-if="!loading && !report.daily.length" class="empty-state"><strong>所选日期内没有经营数据</strong><span>调整日期或场馆后重新查询。</span></div></section>
    </template>

    <section v-else-if="activeView === 'bookings'" class="data-panel" aria-label="预约明细"><header class="data-panel-head"><div><h2>预约明细</h2><p>线上经营与线下登记构成</p></div><span class="record-count">{{ bookings.length }} 笔</span></header><div class="table-wrap"><table><thead><tr><th>预约单号</th><th>来源</th><th>联系人</th><th>场地与场次</th><th>状态</th><th>预约金额</th><th>线下登记</th><th>余额消费</th><th>外部支付</th></tr></thead><tbody><tr v-for="item in bookings" :key="item.bookingNo"><td data-label="预约单号"><strong>{{ item.bookingNo }}</strong></td><td data-label="来源">{{ item.source === "ONLINE" ? "线上" : "线下" }}</td><td data-label="联系人">{{ item.contactName }} · {{ item.contactPhone }}</td><td data-label="场地与场次">{{ item.courtName }} · {{ item.sportName }}<br><span class="muted">{{ item.sessionDate }} {{ item.startTime }}-{{ item.endTime }}</span></td><td data-label="状态"><span class="status" :data-tone="statusTone(item.status)">{{ statusLabel(item.status) }}</span></td><td data-label="预约金额">{{ money(item.amountFen) }}</td><td data-label="线下登记">{{ money(item.offlineRegisteredAmountFen) }}</td><td data-label="余额消费">{{ money(item.balanceConsumptionFen) }}</td><td data-label="外部支付">{{ money(item.externalPaymentFen) }}</td></tr></tbody></table></div><div v-if="!loading && !bookings.length" class="empty-state"><strong>没有预约明细</strong></div></section>

    <section v-else-if="activeView === 'member-flows'" class="data-panel" aria-label="会员流水"><header class="data-panel-head"><div><h2>会员流水</h2><p>充值、赠送与余额变动明细</p></div><span class="record-count">{{ memberFlows.length }} 笔</span></header><div class="table-wrap"><table><thead><tr><th>时间</th><th>会员码</th><th>业务</th><th>关联单号</th><th>操作人</th><th>方向</th><th>金额</th></tr></thead><tbody><tr v-for="item in memberFlows" :key="item.flowId"><td data-label="时间">{{ localDateTime(item.occurredAt) }}</td><td data-label="会员码">{{ item.memberCode }}</td><td data-label="业务">{{ statusLabel(item.businessType) }}</td><td data-label="关联单号">{{ item.bookingNo || item.rechargeNo || "—" }}</td><td data-label="操作人">{{ item.operatorName || "系统" }}</td><td data-label="方向">{{ item.direction === "CREDIT" ? "入账" : "出账" }}</td><td data-label="金额">{{ money(item.amountFen) }}</td></tr></tbody></table></div><div v-if="!loading && !memberFlows.length" class="empty-state"><strong>没有会员流水</strong></div></section>

    <section v-else-if="activeView === 'receipts'" class="data-panel" aria-label="每日收款"><header class="data-panel-head"><div><h2>每日真实收款</h2><p>按实际成功日期、操作员工与渠道汇总</p></div><span class="record-count">{{ receipts.length }} 行</span></header><div class="table-wrap"><table><thead><tr><th>日期</th><th>操作员工</th><th>渠道</th><th>业务</th><th>笔数</th><th>金额</th></tr></thead><tbody><tr v-for="(item, index) in receipts" :key="`${item.receiptDate}-${item.operatorName}-${item.channel}-${item.businessType}-${index}`"><td data-label="日期">{{ localDate(item.receiptDate) }}</td><td data-label="操作员工">{{ item.operatorName || "系统" }}</td><td data-label="渠道">{{ channelLabel(item.channel) }}</td><td data-label="业务">{{ statusLabel(item.businessType) }}</td><td data-label="笔数">{{ item.count }}</td><td data-label="金额"><strong>{{ money(item.amountFen) }}</strong></td></tr></tbody></table></div><div v-if="!loading && !receipts.length" class="empty-state"><strong>没有真实收款记录</strong></div></section>

    <section v-else-if="activeView === 'member-operations'" class="data-panel" aria-label="平台会员经营"><header class="data-panel-head"><div><h2>场馆会员经营</h2><p>平台仅展示场馆级汇总，不展示个人明细</p></div><span class="record-count">{{ memberOperations.length }} 家</span></header><div class="table-wrap"><table><thead><tr><th>场馆</th><th>顾客</th><th>会员</th><th>新增会员</th><th>复购</th><th>复购率</th><th>充值流入</th><th>余额消费</th><th>可用余额</th><th>冻结余额</th></tr></thead><tbody><tr v-for="item in memberOperations" :key="item.venueId"><td data-label="场馆"><strong>{{ item.venueName }}</strong></td><td data-label="顾客">{{ item.customerCount }}</td><td data-label="会员">{{ item.memberCount }}</td><td data-label="新增会员">{{ item.newMemberCount }}</td><td data-label="复购">{{ item.repeatCustomerCount }}</td><td data-label="复购率">{{ percent(item.repeatPurchaseRateBasisPoints) }}</td><td data-label="充值流入">{{ money(item.rechargeInflowFen) }}</td><td data-label="余额消费">{{ money(item.balanceConsumptionFen) }}</td><td data-label="可用余额">{{ money(item.memberAvailableBalanceFen) }}</td><td data-label="冻结余额">{{ money(item.memberFrozenBalanceFen) }}</td></tr></tbody></table></div></section>

    <template v-else>
      <dl class="report-summary" aria-live="polite"><div><dt>可计费预约</dt><dd>{{ settlement.billableBookingCount }}</dd></div><div><dt>预约总金额</dt><dd>{{ money(settlement.bookingAmountFen) }}</dd></div><div><dt>平台服务费</dt><dd>{{ preciseYuan(settlement.serviceFeeYuan) }}</dd></div><div v-if="settlement.currentServiceFeeRateBasisPoints !== null"><dt>当前服务费率</dt><dd>{{ percent(settlement.currentServiceFeeRateBasisPoints) }}</dd></div></dl>
      <section v-if="isPlatform && !venueId" class="data-panel" aria-label="场馆服务费汇总"><header class="data-panel-head"><div><h2>场馆汇总</h2><p>{{ billingMonth }}</p></div><span class="record-count">{{ settlement.venues.length }} 家</span></header><div class="table-wrap"><table><thead><tr><th>场馆</th><th>当前费率</th><th>可计费预约</th><th>预约总金额</th><th>平台服务费</th></tr></thead><tbody><tr v-for="venue in settlement.venues" :key="venue.venueId"><td data-label="场馆"><strong>{{ venue.venueName }}</strong></td><td data-label="当前费率">{{ percent(venue.currentServiceFeeRateBasisPoints) }}</td><td data-label="可计费预约">{{ venue.billableBookingCount }}</td><td data-label="预约总金额">{{ money(venue.bookingAmountFen) }}</td><td data-label="平台服务费">{{ preciseYuan(venue.serviceFeeYuan) }}</td></tr></tbody></table></div></section>
      <section class="data-panel" aria-label="平台服务费明细"><header class="data-panel-head"><div><h2>逐笔明细</h2><p>{{ billingMonth }} · {{ scopeName }}</p></div><span class="record-count">{{ settlement.bookings.length }} 笔</span></header><div class="table-wrap"><table><thead><tr><th>预约单号</th><th v-if="isPlatform">场馆</th><th>场地与场次</th><th>状态</th><th>预约金额</th><th>费率快照</th><th>平台服务费</th></tr></thead><tbody><tr v-for="booking in settlement.bookings" :key="booking.bookingNo"><td data-label="预约单号"><strong>{{ booking.bookingNo }}</strong></td><td v-if="isPlatform" data-label="场馆">{{ booking.venueName }}</td><td data-label="场地与场次">{{ booking.courtName }}<br><span class="muted">{{ localDateTime(booking.startAt) }}</span></td><td data-label="状态"><span class="status" :data-tone="statusTone(booking.status)">{{ statusLabel(booking.status) }}</span></td><td data-label="预约金额">{{ money(booking.amountFen) }}</td><td data-label="费率快照">{{ percent(booking.serviceFeeRateBasisPoints) }}</td><td data-label="平台服务费"><strong>{{ preciseYuan(booking.serviceFeeYuan) }}</strong></td></tr></tbody></table></div></section>
    </template>
  </section>
</template>

<style scoped>
.report-filters { padding-block-end: var(--space-md); }
.report-tabs { width: 100%; }
.report-tabs .button, .report-filters .button { min-height: 2.75rem; }
.report-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 0; overflow: hidden; border: 1px solid var(--color-rule-2); border-radius: var(--radius-panel); background: var(--color-surface); }
.report-summary > div { min-width: 0; min-height: 4.5rem; padding: var(--space-sm) var(--space-md); border-block-end: 1px solid var(--color-rule-2); border-inline-end: 1px solid var(--color-rule-2); }
.report-summary dt { color: var(--color-muted); font-size: var(--text-xs); font-weight: 600; }
.report-summary dd { margin: var(--space-xs) 0 0; overflow-wrap: anywhere; font-family: var(--font-numeric); font-size: var(--text-lg); font-variant-numeric: tabular-nums; font-weight: 600; }
@media (max-width: 59.999rem) { .report-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
