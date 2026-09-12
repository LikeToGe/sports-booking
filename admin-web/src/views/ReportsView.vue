<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Search } from "@lucide/vue";
import { api, localDateTime, money, percent, preciseYuan, today } from "../api";
import { auth } from "../auth";
import { statusLabel, statusTone } from "../labels";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { OperationReport, PlatformVenue, ServiceFeeSettlement } from "../types";

const activeView = ref<"operations" | "service-fees">("operations");
const endDate = ref(today());
const startDate = ref(`${endDate.value.slice(0, 7)}-01`);
const billingMonth = ref(endDate.value.slice(0, 7));
const venueId = ref("");
const venues = ref<PlatformVenue[]>([]);
const report = ref<OperationReport>(emptyReport());
const settlement = ref<ServiceFeeSettlement>(emptySettlement());
const error = ref("");
const loading = ref(false);
const isPlatform = computed(() => auth.principal?.role === "PLATFORM_ADMIN");
const title = computed(() => isPlatform.value ? "经营总报表" : "经营报表");
const daily = computed(() => report.value.daily);
const serviceFeeBookings = computed(() => settlement.value.bookings);
const {
  page: operationPage,
  pageSize: operationPageSize,
  total: operationTotal,
  pagedItems: pagedDaily,
} = usePagination(daily);
const {
  page: serviceFeePage,
  pageSize: serviceFeePageSize,
  total: serviceFeeTotal,
  pagedItems: pagedServiceFeeBookings,
} = usePagination(serviceFeeBookings);
const scopeName = computed(() => {
  if (!isPlatform.value) return auth.principal?.displayName || "当前场馆";
  return venues.value.find(item => String(item.venue.id) === venueId.value)?.venue.name || "全部场馆";
});

function emptyReport(): OperationReport {
  return {
    bookingCount: 0,
    effectiveBookingCount: 0,
    completedCount: 0,
    cancelledCount: 0,
    noShowCount: 0,
    amountFen: 0,
    daily: [],
  };
}

function emptySettlement(): ServiceFeeSettlement {
  return {
    billingMonth: billingMonth.value,
    currentServiceFeeRateBasisPoints: null,
    billableBookingCount: 0,
    bookingAmountFen: 0,
    serviceFeeYuan: "0",
    venues: [],
    bookings: [],
  };
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    if (activeView.value === "operations") {
      const params = new URLSearchParams({ startDate: startDate.value, endDate: endDate.value });
      if (isPlatform.value && venueId.value) params.set("venueId", venueId.value);
      const endpoint = isPlatform.value ? "/platform/reports/operations" : "/venue-admin/reports/operations";
      report.value = await api<OperationReport>(`${endpoint}?${params}`);
    } else {
      const params = new URLSearchParams({ billingMonth: billingMonth.value });
      if (isPlatform.value && venueId.value) params.set("venueId", venueId.value);
      const endpoint = isPlatform.value ? "/platform/reports/service-fees" : "/venue-admin/reports/service-fees";
      settlement.value = await api<ServiceFeeSettlement>(`${endpoint}?${params}`);
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "报表加载失败";
  } finally {
    loading.value = false;
  }
}

async function setView(view: "operations" | "service-fees") {
  activeView.value = view;
  await load();
}

function localDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(`${value}T00:00:00+08:00`));
}

onMounted(async () => {
  if (isPlatform.value) {
    try {
      venues.value = await api<PlatformVenue[]>("/platform/venues/list");
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : "场馆列表加载失败";
      return;
    }
  }
  await load();
});
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>{{ title }}</h1><p>{{ scopeName }} · {{ activeView === "operations" ? "按场次日期统计有效预约和金额。" : "按场次结束月份核对平台服务费。" }}</p></div>
    </header>

    <div class="tabs report-tabs" role="tablist" aria-label="报表类型">
      <button class="button" :class="{ secondary: activeView !== 'operations' }" type="button" role="tab" :aria-selected="activeView === 'operations'" @click="setView('operations')">经营报表</button>
      <button class="button" :class="{ secondary: activeView !== 'service-fees' }" type="button" role="tab" :aria-selected="activeView === 'service-fees'" @click="setView('service-fees')">月度结算</button>
    </div>

    <form v-if="activeView === 'operations'" class="toolbar report-filters" @submit.prevent="load">
      <label v-if="isPlatform" class="field">
        <span>场馆</span>
        <select v-model="venueId">
          <option value="">全部场馆</option>
          <option v-for="item in venues" :key="item.venue.id" :value="String(item.venue.id)">{{ item.venue.name }}</option>
        </select>
      </label>
      <label class="field"><span>开始日期</span><input v-model="startDate" type="date" required></label>
      <label class="field"><span>结束日期</span><input v-model="endDate" type="date" required></label>
      <button class="button" type="submit" :disabled="loading"><Search :size="18" aria-hidden="true" />{{ loading ? "查询中" : "查询" }}</button>
    </form>

    <form v-else class="toolbar report-filters" @submit.prevent="load">
      <label v-if="isPlatform" class="field">
        <span>场馆</span>
        <select v-model="venueId">
          <option value="">全部场馆</option>
          <option v-for="item in venues" :key="item.venue.id" :value="String(item.venue.id)">{{ item.venue.name }}</option>
        </select>
      </label>
      <label class="field"><span>月份</span><input v-model="billingMonth" type="month" required></label>
      <button class="button" type="submit" :disabled="loading"><Search :size="18" aria-hidden="true" />{{ loading ? "查询中" : "查询" }}</button>
    </form>

    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>

    <dl v-if="activeView === 'operations'" class="report-summary" aria-live="polite">
      <div><dt>预约总数</dt><dd>{{ report.bookingCount }}</dd></div>
      <div><dt>有效预约</dt><dd>{{ report.effectiveBookingCount }}</dd></div>
      <div><dt>有效预约金额</dt><dd>{{ money(report.amountFen) }}</dd></div>
      <div><dt>已完成</dt><dd>{{ report.completedCount }}</dd></div>
      <div><dt>已取消</dt><dd>{{ report.cancelledCount }}</dd></div>
      <div><dt>未到场</dt><dd>{{ report.noShowCount }}</dd></div>
    </dl>

    <section v-if="activeView === 'operations'" class="data-panel" aria-label="经营日报">
      <header class="data-panel-head"><div><h2>经营日报</h2><p>{{ startDate }} 至 {{ endDate }}</p></div><span class="record-count">{{ operationTotal }} 天</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>日期</th><th>预约总数</th><th>有效预约</th><th>有效预约金额</th><th>已完成</th><th>已取消</th><th>未到场</th></tr></thead>
          <tbody>
          <tr v-for="day in pagedDaily" :key="day.reportDate">
            <td data-label="日期"><strong>{{ localDate(day.reportDate) }}</strong></td>
            <td data-label="预约总数">{{ day.bookingCount }}</td>
            <td data-label="有效预约">{{ day.effectiveBookingCount }}</td>
            <td data-label="有效预约金额">{{ money(day.amountFen) }}</td>
            <td data-label="已完成">{{ day.completedCount }}</td>
            <td data-label="已取消">{{ day.cancelledCount }}</td>
            <td data-label="未到场">{{ day.noShowCount }}</td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!loading && !report.daily.length" class="empty-state"><strong>所选日期内没有预约</strong><span>调整日期或场馆后重新查询。</span></div>
      <PaginationBar v-if="report.daily.length" v-model="operationPage" :total="operationTotal" :page-size="operationPageSize" />
    </section>

    <template v-else>
      <dl class="report-summary settlement-summary" aria-live="polite">
        <div><dt>可计费预约</dt><dd>{{ settlement.billableBookingCount }}</dd></div>
        <div><dt>预约总金额</dt><dd>{{ money(settlement.bookingAmountFen) }}</dd></div>
        <div><dt>平台服务费</dt><dd>{{ preciseYuan(settlement.serviceFeeYuan) }}</dd></div>
        <div v-if="settlement.currentServiceFeeRateBasisPoints !== null"><dt>当前服务费率</dt><dd>{{ percent(settlement.currentServiceFeeRateBasisPoints) }}</dd></div>
      </dl>

      <section v-if="isPlatform && !venueId" class="data-panel" aria-label="场馆服务费汇总">
        <header class="data-panel-head"><div><h2>场馆汇总</h2><p>{{ billingMonth }}</p></div><span class="record-count">{{ settlement.venues.length }} 家</span></header>
        <div class="table-wrap">
          <table>
            <thead><tr><th>场馆</th><th>当前费率</th><th>可计费预约</th><th>预约总金额</th><th>平台服务费</th></tr></thead>
            <tbody>
            <tr v-for="venue in settlement.venues" :key="venue.venueId">
              <td data-label="场馆"><strong>{{ venue.venueName }}</strong></td>
              <td data-label="当前费率">{{ percent(venue.currentServiceFeeRateBasisPoints) }}</td>
              <td data-label="可计费预约">{{ venue.billableBookingCount }}</td>
              <td data-label="预约总金额">{{ money(venue.bookingAmountFen) }}</td>
              <td data-label="平台服务费" class="service-fee-value">{{ preciseYuan(venue.serviceFeeYuan) }}</td>
            </tr>
            </tbody>
          </table>
        </div>
        <div v-if="!loading && !settlement.venues.length" class="empty-state"><strong>本月没有可计费预约</strong><span>更换月份后重新查询。</span></div>
      </section>

      <section class="data-panel" aria-label="平台服务费明细">
        <header class="data-panel-head"><div><h2>逐笔明细</h2><p>{{ billingMonth }} · {{ scopeName }}</p></div><span class="record-count">{{ serviceFeeTotal }} 笔</span></header>
        <div class="table-wrap">
          <table>
            <thead><tr><th>预约单号</th><th v-if="isPlatform">场馆</th><th>场地与场次</th><th>状态</th><th>预约金额</th><th>费率快照</th><th>平台服务费</th></tr></thead>
            <tbody>
            <tr v-for="booking in pagedServiceFeeBookings" :key="booking.bookingNo">
              <td data-label="预约单号"><strong>{{ booking.bookingNo }}</strong></td>
              <td v-if="isPlatform" data-label="场馆">{{ booking.venueName }}</td>
              <td data-label="场地与场次"><strong>{{ booking.courtName }}</strong><br><span class="muted">{{ localDateTime(booking.startAt) }} 至 {{ localDateTime(booking.endAt) }}</span></td>
              <td data-label="状态"><span class="status" :data-tone="statusTone(booking.status)">{{ statusLabel(booking.status) }}</span></td>
              <td data-label="预约金额">{{ money(booking.amountFen) }}</td>
              <td data-label="费率快照">{{ percent(booking.serviceFeeRateBasisPoints) }}</td>
              <td data-label="平台服务费" class="service-fee-value">{{ preciseYuan(booking.serviceFeeYuan) }}</td>
            </tr>
            </tbody>
          </table>
        </div>
        <div v-if="!loading && !settlement.bookings.length" class="empty-state"><strong>本月没有可计费预约</strong><span>仅统计已完成或未到场的线上预约。</span></div>
        <PaginationBar v-if="settlement.bookings.length" v-model="serviceFeePage" :total="serviceFeeTotal" :page-size="serviceFeePageSize" />
      </section>
    </template>
  </section>
</template>

<style scoped>
.report-filters {
  padding-block-end: var(--space-md);
}

.report-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: min(100%, 18rem);
  margin-block-end: var(--space-md);
}

.report-tabs .button,
.report-filters .button {
  min-height: 2.75rem;
}

.service-fee-value {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.report-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--color-rule-2);
  border-radius: var(--radius-panel);
  background: var(--color-surface);
}

.report-summary > div {
  min-width: 0;
  min-height: 4.5rem;
  padding: var(--space-sm) var(--space-md);
  border-block-end: 1px solid var(--color-rule-2);
  border-inline-end: 1px solid var(--color-rule-2);
}

.report-summary > div:nth-child(3n) { border-inline-end: 0; }

.report-summary dt {
  color: var(--color-muted);
  font-size: var(--text-xs);
  font-weight: 600;
}

.report-summary dd {
  margin: var(--space-xs) 0 0;
  overflow-wrap: anywhere;
  font-family: var(--font-numeric);
  font-size: var(--text-lg);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

@media (max-width: 59.999rem) {
  .report-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .report-summary > div:nth-child(3n) { border-inline-end: 1px solid var(--color-rule-2); }
  .report-summary > div:nth-child(2n) { border-inline-end: 0; }
}
</style>
