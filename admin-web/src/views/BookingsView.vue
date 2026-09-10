<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import { Plus, X } from "@lucide/vue";
import { api, localDateTime, money, today } from "../api";
import { statusLabel, statusTone } from "../labels";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { BookingView, SessionView } from "../types";

const selectedDate = ref(today());
const bookings = ref<BookingView[]>([]);
const error = ref("");
const formError = ref("");
const createDialog = ref<HTMLDialogElement>();
const cancelDialog = ref<HTMLDialogElement>();
const cancelling = ref<BookingView | null>(null);
const cancelReason = ref("");
const sessions = ref<SessionView[]>([]);
const offlineSlotId = ref(0);
const offlineName = ref("");
const offlinePhone = ref("");
const keyword = ref("");
const status = ref("");
const source = ref("");
const filteredBookings = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return bookings.value.filter(booking =>
    (!query || [booking.bookingNo, booking.courtName, booking.sportName, booking.contactName, booking.contactPhone]
      .some(value => value.toLowerCase().includes(query)))
    && (!status.value || booking.status === status.value)
    && (!source.value || booking.source === source.value));
});
const { page, pageSize, total, pagedItems } = usePagination(filteredBookings);

async function load() {
  error.value = "";
  try {
    [bookings.value, sessions.value] = await Promise.all([
      api<BookingView[]>(`/venue-admin/bookings/list?date=${selectedDate.value}`),
      api<SessionView[]>(`/venue-admin/sessions/list?date=${selectedDate.value}`),
    ]);
    const firstOpen = sessions.value.find(session => session.status === "OPEN");
    if (firstOpen && !offlineSlotId.value) offlineSlotId.value = firstOpen.id;
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "预约加载失败";
  }
}

async function createOffline() {
  formError.value = "";
  try {
    await api("/venue-admin/bookings/create", {
      method: "POST",
      body: JSON.stringify({ sessionSlotId: offlineSlotId.value, contactName: offlineName.value, contactPhone: offlinePhone.value }),
    });
    offlineName.value = "";
    offlinePhone.value = "";
    offlineSlotId.value = 0;
    createDialog.value?.close();
    await load();
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "线下预约创建失败";
  }
}

function openCreate() {
  formError.value = "";
  createDialog.value?.showModal();
}

async function openCancel(booking: BookingView) {
  cancelling.value = booking;
  cancelReason.value = "";
  formError.value = "";
  await nextTick();
  cancelDialog.value?.showModal();
}

async function cancelBooking() {
  if (!cancelling.value) return;
  error.value = "";
  try {
    await api(`/venue-admin/bookings/${cancelling.value.id}/cancel`, {
      method: "POST", body: JSON.stringify({ reason: cancelReason.value }),
    });
    cancelDialog.value?.close();
    await load();
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "预约取消失败";
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>预约</h1><p>线上与线下预约使用同一场次库存。</p></div>
      <button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增预约</button>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <div class="filter-bar" aria-label="预约筛选">
      <label class="field"><span>日期</span><input v-model="selectedDate" type="date" @change="load"></label>
      <label class="field filter-search"><span>搜索</span><input v-model="keyword" type="search" placeholder="预约号、场地或联系人"></label>
      <label class="field"><span>来源</span><select v-model="source"><option value="">全部来源</option><option value="ONLINE">小程序</option><option value="OFFLINE">线下</option></select></label>
      <label class="field"><span>状态</span><select v-model="status"><option value="">全部状态</option><option value="PENDING_PAYMENT">待支付</option><option value="CONFIRMED">待核销</option><option value="COMPLETED">已完成</option><option value="CANCELLED">已取消</option><option value="NO_SHOW">未到场</option></select></label>
    </div>
    <section class="data-panel" aria-label="预约记录">
      <header class="data-panel-head"><div><h2>预约记录</h2><p>{{ selectedDate }} 的线上与线下预约</p></div><span class="record-count">{{ total }} 条</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>时间</th><th>场地</th><th>联系人</th><th>金额</th><th>来源</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
          <tr v-for="booking in pagedItems" :key="booking.id">
            <td data-label="时间">{{ localDateTime(booking.startAt) }}</td>
            <td data-label="场地"><strong>{{ booking.courtName }}</strong><br><span class="muted">{{ booking.sportName }}</span></td>
            <td data-label="联系人">{{ booking.contactName }}<br><span class="muted">{{ booking.contactPhone }}</span></td>
            <td data-label="金额">{{ money(booking.amountFen) }}</td>
            <td data-label="来源">{{ booking.source === "ONLINE" ? "小程序" : "线下" }}</td>
            <td data-label="状态"><span class="status" :data-tone="statusTone(booking.status)">{{ statusLabel(booking.status) }}</span></td>
            <td data-label="操作">
              <button v-if="booking.status === 'CONFIRMED'" class="button danger" type="button" @click="openCancel(booking)">取消预约</button>
              <span v-else>—</span>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filteredBookings.length" class="empty-state"><strong>{{ bookings.length ? "没有匹配的预约" : "这一天没有预约" }}</strong><span>{{ bookings.length ? "调整搜索、来源或状态后重试。" : "用户或场馆创建预约后会显示在这里。" }}</span></div>
      <PaginationBar v-if="filteredBookings.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>

    <dialog ref="createDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>新增线下预约</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="createOffline">
        <p v-if="formError" class="error-banner" role="alert">{{ formError }}</p>
        <label class="field"><span>开放场次</span><select v-model.number="offlineSlotId" required><option v-for="session in sessions.filter(item => item.status === 'OPEN')" :key="session.id" :value="session.id">{{ localDateTime(session.startAt) }} · {{ session.courtName }}</option></select></label>
        <label class="field"><span>联系人</span><input v-model.trim="offlineName" required></label>
        <label class="field"><span>手机号</span><input v-model.trim="offlinePhone" inputmode="tel" required></label>
        <div class="modal-actions"><button class="button secondary" type="button" @click="createDialog?.close()">取消</button><button class="button" type="submit" :disabled="!offlineSlotId">创建预约</button></div>
      </form>
    </dialog>

    <dialog ref="cancelDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>取消预约</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="cancelBooking">
        <p v-if="formError" class="error-banner" role="alert">{{ formError }}</p>
        <p>取消后，该场次重新开放；已模拟支付的预约会同步模拟退款。</p>
        <label class="field"><span>取消原因</span><textarea v-model.trim="cancelReason" required></textarea></label>
        <button class="button danger" type="submit">确认取消</button>
      </form>
    </dialog>
  </section>
</template>
