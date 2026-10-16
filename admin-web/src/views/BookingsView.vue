<script setup lang="ts">
import { requireText, requireSelection } from "../validation";
import { computed, nextTick, onMounted, ref } from "vue";
import { CalendarPlus, Plus, X } from "@lucide/vue";
import { ApiError, api, localDateTime, money, today } from "../api";
import { auth, hasPermission } from "../auth";
import { statusLabel, statusTone } from "../labels";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { BookingView, SessionView, VenueCustomer } from "../types";

const selectedDate = ref(today());
const bookings = ref<BookingView[]>([]);
const sessions = ref<SessionView[]>([]);
const customers = ref<VenueCustomer[]>([]);
const loading = ref(false);
const error = ref("");
const formError = ref("");
const conflictDates = ref<string[]>([]);
const createDialog = ref<HTMLDialogElement>();
const cancelDialog = ref<HTMLDialogElement>();
const paymentDialog = ref<HTMLDialogElement>();
const rescheduleDialog = ref<HTMLDialogElement>();
const editingBooking = ref<BookingView>();
const cancelling = ref<BookingView>();
const selectedPaymentBooking = ref<BookingView>();
const rescheduling = ref<BookingView>();
const cancelReason = ref("");
const offlineSlotId = ref(0);
const offlineName = ref("");
const offlinePhone = ref("");
const offlinePaymentStatus = ref("UNPAID");
const venueCustomerId = ref<number>();
const createMode = ref<"single" | "batch">("single");
const batchDate = ref(today());
const batchDates = ref<string[]>([]);
const paymentStatus = ref("PAID");
const paymentReason = ref("");
const canEdit = hasPermission("BOOKING_EDIT");
const canCancelOnline = hasPermission("ONLINE_BOOKING_CANCEL");
const canEditOfflinePayment = hasPermission("OFFLINE_PAYMENT_STATUS_EDIT");
const rescheduleDate = ref(today());
const rescheduleSlotId = ref(0);
const rescheduleReason = ref("");
const keyword = ref("");
const status = ref("");
const source = ref("");

const openSessions = computed(() => sessions.value.filter(item => item.status === "OPEN"));
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
  loading.value = true;
  error.value = "";
  try {
    if (!selectedDate.value) throw new Error("请选择预约日期");
    [bookings.value, sessions.value, customers.value] = await Promise.all([
      api<BookingView[]>(`/venue-admin/bookings/list?date=${selectedDate.value}`),
      api<SessionView[]>(`/venue-admin/sessions/list?date=${selectedDate.value}`),
      api<VenueCustomer[]>("/venue-admin/customers/list?keyword="),
    ]);
    offlineSlotId.value ||= openSessions.value[0]?.id || 0;
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "预约加载失败";
  } finally {
    loading.value = false;
  }
}

function resetCreate(mode: "single" | "batch") {
  createMode.value = mode;
  editingBooking.value = undefined;
  offlineName.value = "";
  offlinePhone.value = "";
  offlinePaymentStatus.value = "UNPAID";
  venueCustomerId.value = undefined;
  offlineSlotId.value = openSessions.value[0]?.id || 0;
  batchDates.value = [];
  batchDate.value = selectedDate.value;
  formError.value = "";
  conflictDates.value = [];
  createDialog.value?.showModal();
}

function openEdit(booking: BookingView) {
  createMode.value = "single";
  editingBooking.value = booking;
  offlineName.value = booking.contactName;
  offlinePhone.value = booking.contactPhone;
  formError.value = "";
  createDialog.value?.showModal();
}

function addBatchDate() {
  if (batchDate.value && !batchDates.value.includes(batchDate.value)) {
    batchDates.value = [...batchDates.value, batchDate.value].sort();
  }
}

function removeBatchDate(date: string) {
  batchDates.value = batchDates.value.filter(item => item !== date);
}

async function saveOffline() {
  formError.value = "";
  conflictDates.value = [];
  try {
    requireText(offlineName.value, "联系人姓名");
    requireText(offlinePhone.value, "联系电话");
    if (!editingBooking.value && !offlineSlotId.value) throw new Error("请选择可预约场次");
    if (!editingBooking.value && createMode.value === "batch") requireSelection(batchDates.value, "预约日期");
    if (editingBooking.value) {
      await api(`/venue-admin/bookings/${editingBooking.value.id}/contact`, {
        method: "PUT", body: JSON.stringify({ contactName: offlineName.value, contactPhone: offlinePhone.value }),
      });
    } else {
      const path = createMode.value === "batch"
        ? "/venue-admin/bookings/offline/batch" : "/venue-admin/bookings/offline";
      const body = { sessionSlotId: offlineSlotId.value, contactName: offlineName.value,
        contactPhone: offlinePhone.value, paymentStatus: offlinePaymentStatus.value,
        venueCustomerId: venueCustomerId.value || null,
        ...(createMode.value === "batch" ? { dates: batchDates.value } : {}) };
      await api(path, { method: "POST", body: JSON.stringify(body) });
    }
    createDialog.value?.close();
    await load();
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "线下预约保存失败";
    if (reason instanceof ApiError && Array.isArray(reason.data)) conflictDates.value = reason.data;
  }
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
  await submit(cancelDialog, () => api(`/venue-admin/bookings/${cancelling.value?.id}/cancel`, {
    method: "POST", body: JSON.stringify({ reason: cancelReason.value }),
  }));
}

function openPayment(booking: BookingView) {
  selectedPaymentBooking.value = booking;
  paymentStatus.value = booking.paymentStatus === "UNRECORDED" ? "UNPAID" : "PAID";
  paymentReason.value = "";
  formError.value = "";
  paymentDialog.value?.showModal();
}

async function savePayment() {
  await submit(paymentDialog, () => api(`/venue-admin/bookings/${selectedPaymentBooking.value?.id}/payment-status`, {
    method: "PUT", body: JSON.stringify({ paymentStatus: paymentStatus.value, reason: paymentReason.value }),
  }));
}

async function openReschedule(booking: BookingView) {
  rescheduling.value = booking;
  rescheduleDate.value = selectedDate.value;
  rescheduleReason.value = "";
  formError.value = "";
  await loadRescheduleSessions();
  rescheduleDialog.value?.showModal();
}

async function loadRescheduleSessions() {
  try {
    sessions.value = await api<SessionView[]>(`/venue-admin/sessions/list?date=${rescheduleDate.value}`);
    rescheduleSlotId.value = openSessions.value.find(item => item.priceFen === rescheduling.value?.amountFen)?.id || 0;
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "可改期场次加载失败";
  }
}

async function saveReschedule() {
  await submit(rescheduleDialog, () => api(`/venue-admin/bookings/${rescheduling.value?.id}/reschedule`, {
    method: "POST", body: JSON.stringify({ sessionSlotId: rescheduleSlotId.value, reason: rescheduleReason.value }),
  }));
}

async function submit(dialog: typeof cancelDialog, action: () => Promise<unknown>) {
  formError.value = "";
  try {
    if (dialog === cancelDialog) requireText(cancelReason.value, "取消原因", 255);
    if (dialog === paymentDialog) requireText(paymentReason.value, "收款状态修改原因", 255);
    if (dialog === rescheduleDialog) {
      if (!rescheduleSlotId.value) throw new Error("请选择同价可预约场次");
      requireText(rescheduleReason.value, "改期原因", 255);
    }
    await action();
    dialog.value?.close();
    await load();
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "操作失败";
  }
}

function paymentOptions(booking?: BookingView) {
  if (!booking) return [];
  if (booking.paymentStatus === "UNRECORDED") return auth.principal?.role === "VENUE_ADMIN" ? ["UNPAID", "PAID"] : [];
  if (booking.paymentStatus === "UNPAID") return ["PAID"];
  if (booking.paymentStatus === "PAID" && booking.status === "CANCELLED") return ["REFUNDED"];
  return [];
}

onMounted(load);
</script>

<template>
  <section class="page bookings-page">
    <header class="page-header">
      <div><h1>预约管理</h1><p>线上收款与线下登记分开处理，批量创建保持整批原子。</p></div>
      <div v-if="canEdit" class="toolbar">
        <button class="button secondary" type="button" @click="resetCreate('batch')"><CalendarPlus :size="18" aria-hidden="true" />批量预约</button>
        <button class="button" type="button" @click="resetCreate('single')"><Plus :size="18" aria-hidden="true" />线下预约</button>
      </div>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }} <button class="inline-command" type="button" @click="load">重新加载</button></p>
    <div class="filter-bar" aria-label="预约筛选">
      <label class="field"><span>日期</span><input v-model="selectedDate" type="date" @change="load"></label>
      <label class="field filter-search"><span>搜索</span><input v-model="keyword" type="search" placeholder="预约号、场地或联系人"></label>
      <label class="field"><span>来源</span><select v-model="source"><option value="">全部来源</option><option value="ONLINE">小程序</option><option value="OFFLINE">线下</option></select></label>
      <label class="field"><span>履约状态</span><select v-model="status"><option value="">全部状态</option><option value="PENDING_PAYMENT">待支付</option><option value="CONFIRMED">待履约</option><option value="COMPLETED">已完成</option><option value="CANCELLED">已取消</option><option value="NO_SHOW">未到场</option><option value="ENDED_UNTRACKED">履约未跟踪</option></select></label>
    </div>
    <section class="data-panel" aria-label="预约记录" :aria-busy="loading">
      <header class="data-panel-head"><div><h2>预约记录</h2><p>{{ selectedDate }} 的线上与线下预约</p></div><span class="record-count">{{ total }} 条</span></header>
      <div v-if="!loading || bookings.length" class="table-wrap">
        <table>
          <thead><tr><th>时间</th><th>场地</th><th>联系人</th><th class="numeric-column">金额</th><th>来源</th><th>履约</th><th>收款</th><th>操作</th></tr></thead>
          <tbody><tr v-for="booking in pagedItems" :key="booking.id">
            <td data-label="时间"><strong>{{ localDateTime(booking.startAt) }}</strong><span class="subline">{{ booking.bookingNo }}</span></td>
            <td data-label="场地"><strong>{{ booking.courtName }}</strong><br><span class="muted">{{ booking.sportName }}</span></td>
            <td data-label="联系人">{{ booking.contactName }}<br><span class="muted contact-phone">{{ booking.contactPhone }}</span></td>
            <td data-label="金额" class="numeric-column">{{ money(booking.amountFen) }}</td>
            <td data-label="来源"><span class="status" :data-tone="booking.source === 'ONLINE' ? 'active' : 'info'">{{ booking.source === "ONLINE" ? "小程序" : "线下" }}</span></td>
            <td data-label="履约"><span class="status" :data-tone="statusTone(booking.status)">{{ statusLabel(booking.status) }}</span></td>
            <td data-label="收款"><span class="status" :data-tone="statusTone(booking.paymentStatus || '')">{{ statusLabel(booking.paymentStatus || "") }}</span></td>
            <td data-label="操作"><div class="toolbar">
              <button v-if="canEdit && booking.source === 'OFFLINE'" class="button secondary" type="button" @click="openEdit(booking)">联系人</button>
              <button v-if="canEditOfflinePayment && booking.source === 'OFFLINE' && paymentOptions(booking).length" class="button secondary" type="button" @click="openPayment(booking)">收款状态</button>
              <button v-if="canEdit && booking.source === 'OFFLINE' && booking.status === 'CONFIRMED'" class="button secondary" type="button" @click="openReschedule(booking)">改期</button>
              <button v-if="booking.status === 'CONFIRMED' && (booking.source === 'OFFLINE' ? canEdit : canCancelOnline)" class="button danger" type="button" @click="openCancel(booking)">取消</button>
            </div></td>
          </tr></tbody>
        </table>
      </div>
      <div v-if="loading && !bookings.length" class="empty-state" role="status"><strong>正在加载预约记录</strong><span>日期与筛选条件保持不变。</span></div>
      <div v-else-if="!error && !filteredBookings.length" class="empty-state"><strong>{{ bookings.length ? "没有匹配的预约" : "这一天没有预约" }}</strong><span>{{ bookings.length ? "调整筛选后重试。" : "用户或场馆创建预约后会显示在这里。" }}</span></div>
      <PaginationBar v-if="filteredBookings.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>

    <dialog ref="createDialog" class="modal" aria-labelledby="bookingsview-createDialog-title">
      <form method="dialog" class="modal-head"><h2 id="bookingsview-createDialog-title">{{ editingBooking ? "编辑线下预约联系人" : createMode === "batch" ? "批量线下预约" : "创建线下预约" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="saveOffline">
        <p v-if="formError" class="error-banner" role="alert">{{ formError }}<span v-if="conflictDates.length" class="block">冲突日期：{{ conflictDates.join("、") }}</span></p>
        <label v-if="!editingBooking" class="field"><span>模板场次</span><select v-model.number="offlineSlotId" required><option v-for="session in openSessions" :key="session.id" :value="session.id">{{ localDateTime(session.startAt) }} · {{ session.courtName }} · {{ money(session.priceFen) }}</option></select></label>
        <template v-if="!editingBooking && createMode === 'batch'">
          <div class="inline-date"><label class="field"><span>预约日期</span><input v-model="batchDate" type="date"></label><button class="button secondary" type="button" @click="addBatchDate"><Plus :size="18" aria-hidden="true" />添加日期</button></div>
          <div class="date-list" aria-label="已选日期"><button v-for="date in batchDates" :key="date" class="date-item" type="button" :aria-label="`移除 ${date}`" @click="removeBatchDate(date)">{{ date }}<X :size="15" aria-hidden="true" /></button></div>
        </template>
        <label class="field"><span>联系人</span><input v-model.trim="offlineName" required></label>
        <label class="field"><span>手机号</span><input v-model.trim="offlinePhone" inputmode="tel" required></label>
        <template v-if="!editingBooking">
          <label class="field"><span>关联顾客（可选）</span><select v-model="venueCustomerId"><option :value="undefined">不关联</option><option v-for="customer in customers" :key="customer.customerId" :value="customer.customerId">{{ customer.contactName }} · {{ customer.phone }}</option></select></label>
          <label class="field"><span>登记收款状态</span><select v-model="offlinePaymentStatus"><option value="UNPAID">未支付</option><option value="PAID">已支付</option></select></label>
        </template>
        <div class="modal-actions"><button class="button secondary" type="button" @click="createDialog?.close()">取消</button><button class="button" type="submit" >{{ editingBooking ? "保存修改" : createMode === "batch" ? "整批创建" : "创建预约" }}</button></div>
      </form>
    </dialog>

    <dialog ref="paymentDialog" class="modal" aria-labelledby="bookingsview-paymentDialog-title">
      <form method="dialog" class="modal-head"><h2 id="bookingsview-paymentDialog-title">保存收款状态</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="savePayment"><p v-if="formError" class="error-banner" role="alert">{{ formError }}</p><label class="field"><span>新状态</span><select v-model="paymentStatus"><option v-for="option in paymentOptions(selectedPaymentBooking)" :key="option" :value="option">{{ statusLabel(option) }}</option></select></label><label class="field"><span>修改原因</span><textarea v-model.trim="paymentReason" required></textarea></label><button class="button" type="submit">保存收款状态</button></form>
    </dialog>

    <dialog ref="rescheduleDialog" class="modal" aria-labelledby="bookingsview-rescheduleDialog-title">
      <form method="dialog" class="modal-head"><h2 id="bookingsview-rescheduleDialog-title">同价改期</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="saveReschedule"><p v-if="formError" class="error-banner" role="alert">{{ formError }}</p><label class="field"><span>目标日期</span><input v-model="rescheduleDate" type="date" @change="loadRescheduleSessions"></label><label class="field"><span>同价场次</span><select v-model.number="rescheduleSlotId" required><option v-for="session in openSessions.filter(item => item.priceFen === rescheduling?.amountFen)" :key="session.id" :value="session.id">{{ localDateTime(session.startAt) }} · {{ session.courtName }}</option></select></label><label class="field"><span>改期原因</span><textarea v-model.trim="rescheduleReason" required></textarea></label><button class="button" type="submit" >保存改期</button></form>
    </dialog>

    <dialog ref="cancelDialog" class="modal" aria-labelledby="bookingsview-cancelDialog-title">
      <form method="dialog" class="modal-head"><h2 id="bookingsview-cancelDialog-title">取消预约</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="cancelBooking"><p v-if="formError" class="error-banner" role="alert">{{ formError }}</p><p>线下预约只释放场次，登记收款状态需按实际情况另行修改。</p><label class="field"><span>取消原因</span><textarea v-model.trim="cancelReason" required></textarea></label><button class="button danger" type="submit">确认取消</button></form>
    </dialog>
  </section>
</template>
