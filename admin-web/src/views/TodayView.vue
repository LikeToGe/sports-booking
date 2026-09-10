<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { CalendarDays, RefreshCw } from "@lucide/vue";
import { api, localDateTime, today } from "../api";
import { statusLabel, statusTone } from "../labels";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { BookingView } from "../types";

const selectedDate = ref(today());
const bookings = ref<BookingView[]>([]);
const error = ref("");
const loading = ref(false);
const keyword = ref("");
const status = ref("");
const pendingCount = computed(() => bookings.value.filter(item => item.status === "CONFIRMED").length);
const filteredBookings = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return bookings.value.filter(booking =>
    (!query || [booking.courtName, booking.sportName, booking.contactName, booking.contactPhone]
      .some(value => value.toLowerCase().includes(query)))
    && (!status.value || booking.status === status.value));
});
const { page, pageSize, total, pagedItems } = usePagination(filteredBookings);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    bookings.value = await api<BookingView[]>(`/venue-admin/bookings/list?date=${selectedDate.value}`);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "今日场次加载失败";
  } finally {
    loading.value = false;
  }
}

function time(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "Asia/Shanghai",
  }).format(new Date(value));
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1>今日场次</h1>
        <p>{{ pendingCount }} 笔待核销，按开场时间依次处理。</p>
      </div>
    </header>

    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <div class="filter-bar" aria-label="到场队列筛选">
      <label class="field"><span>日期</span><input v-model="selectedDate" type="date" @change="load"></label>
      <label class="field filter-search"><span>搜索</span><input v-model="keyword" type="search" placeholder="场地、运动或联系人"></label>
      <label class="field"><span>状态</span><select v-model="status"><option value="">全部状态</option><option value="CONFIRMED">待核销</option><option value="COMPLETED">已完成</option><option value="CANCELLED">已取消</option><option value="NO_SHOW">未到场</option></select></label>
      <button class="icon-button filter-action" type="button" aria-label="刷新场次" title="刷新场次" :disabled="loading" @click="load">
        <RefreshCw :size="19" aria-hidden="true" />
      </button>
    </div>
    <section class="data-panel" aria-label="今日预约队列">
      <header class="data-panel-head"><div><h2>到场队列</h2><p>按开场时间排列</p></div><span class="record-count">{{ total }} 条</span></header>
      <div v-if="filteredBookings.length" class="timeline" aria-live="polite">
      <article v-for="booking in pagedItems" :key="booking.id" class="timeline-row">
        <time class="timeline-time" :datetime="booking.startAt">{{ time(booking.startAt) }}</time>
        <div class="timeline-content">
          <div>
            <strong>{{ booking.courtName }} · {{ booking.sportName }}</strong>
            <span class="muted">{{ booking.contactName }} · {{ booking.contactPhone }}</span>
          </div>
          <span class="status" :data-tone="statusTone(booking.status)">{{ statusLabel(booking.status) }}</span>
        </div>
      </article>
      </div>
      <div v-else-if="!loading" class="empty-state">
        <CalendarDays :size="28" aria-hidden="true" />
        <strong>{{ bookings.length ? "没有匹配的预约" : "这一天没有预约" }}</strong>
        <span>{{ bookings.length ? "调整搜索或状态后重试。" : "开放场次可在“场次”中查看和调整。" }}</span>
      </div>
      <div v-else class="empty-state" aria-live="polite"><span>正在加载场次…</span></div>
      <PaginationBar v-if="filteredBookings.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>
  </section>
</template>
