<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import {
  ArrowDownLeft,
  ArrowUpRight,
  CircleCheck,
  CircleX,
  Clock3,
  Gift,
  RefreshCw,
  Search,
  TriangleAlert,
  UsersRound,
  WalletCards,
  X,
} from "@lucide/vue";
import { api, localDateTime, money } from "../api";
import { hasPermission } from "../auth";
import { statusLabel, statusTone } from "../labels";
import type { VenueCustomer } from "../types";

interface CustomerOperation {
  customerId: number;
  contactName: string | null;
  phone: string | null;
  validBookingCount: number;
  lastValidBookingAt: string | null;
  lastValidBookingSource: "ONLINE" | "OFFLINE" | null;
  lastCompletedAt: string | null;
  latestVerifiedAt: string | null;
  futureConfirmedCount: number;
  usualHours: string | null;
  tags: string[];
  segment: string | null;
  customerCreatedAt: string;
}

interface VenueCustomerTag {
  id: number;
  name: string;
  createdByName: string;
  createdAt: string;
}

interface CustomerFollowup {
  id: number;
  customerId: number;
  contactName: string | null;
  purpose: string;
  sourceGroup: string;
  status: "PENDING" | "COMPLETED" | "NOT_NEEDED";
  note: string | null;
  createdByName: string;
  createdAt: string;
  handledByName: string | null;
  handledAt: string | null;
  rebookedAfterHandled: boolean | null;
  /** 首个处理后日期有效预约场次的开始时间，不是预约创建时间。 */
  rebookedAfterHandledAt: string | null;
  rebookedAfterHandledSource: "ONLINE" | "OFFLINE" | null;
}

interface RechargePlan {
  id: number;
  name: string;
  paidAmountFen: number;
  giftAmountFen: number;
  giftValidDays: number | null;
  status: "ACTIVE" | "DISABLED";
}

interface BalanceFlow {
  id: number;
  lotId: number;
  balanceType: "PAID" | "GIFT";
  businessType: string;
  direction: "CREDIT" | "DEBIT";
  amountFen: number;
  bookingId: number | null;
  rechargeId: number | null;
  createdAt: string;
}

interface BalanceAccount {
  venueId: number;
  venueCustomerId: number;
  paidBalanceFen: number;
  giftBalanceFen: number;
  nextGiftExpiresAt: string | null;
  nextGiftExpiresAmountFen: number;
  flows: BalanceFlow[];
}

interface MemberRecharge {
  id: number;
  rechargeNo: string;
  planName: string;
  paidAmountFen: number;
  giftAmountFen: number;
  giftValidDays: number | null;
  giftExpiresAt: string | null;
  channel: "MOCK" | "CASH" | "WECHAT" | "ALIPAY";
  status: "PENDING" | "SUCCESS" | "FAILED" | "CLOSED" | "REVERSED";
  reverseReason: string | null;
  succeededAt: string | null;
  reversedAt: string | null;
  createdAt: string;
}

const customers = ref<VenueCustomer[]>([]);
const operations = ref<CustomerOperation[]>([]);
const tags = ref<VenueCustomerTag[]>([]);
const followups = ref<CustomerFollowup[]>([]);
const keyword = ref("");
const segment = ref("");
const followupStatus = ref("");
const newTagName = ref("");
const operationsError = ref("");
const tagsError = ref("");
const followupsError = ref("");
const actionError = ref("");
const actionMessage = ref("");
const creatingTag = ref(false);
const canEditCustomerOperations = hasPermission("CUSTOMER_OPERATIONS_EDIT");
const error = ref("");
const loading = ref(false);
const selected = ref<VenueCustomer>();
const selectedOperation = ref<CustomerOperation>();
const drawer = ref<HTMLDialogElement>();
const balance = ref<BalanceAccount>();
const recharges = ref<MemberRecharge[]>([]);
const plans = ref<RechargePlan[]>([]);
const detailError = ref("");
const detailMessage = ref("");
const detailLoading = ref(false);
const planError = ref("");
const rechargeError = ref("");
const recharging = ref(false);
const rechargePlanId = ref<number | "">("");
const rechargeChannel = ref<"CASH" | "WECHAT" | "ALIPAY">("CASH");
let rechargeIdempotencyKey = "";
const reverseDialog = ref<HTMLDialogElement>();
const followupDialog = ref<HTMLDialogElement>();
const updatingFollowup = ref<CustomerFollowup>();
const followupNextStatus = ref<"COMPLETED" | "NOT_NEEDED">("COMPLETED");
const followupNote = ref("");
const updatingFollowupBusy = ref(false);
const reversingRecharge = ref<MemberRecharge>();
const reverseReason = ref("");
const offlineFundsHandled = ref(false);
const reversing = ref(false);
const reverseError = ref("");
const canViewFunds = hasPermission("MEMBER_FUNDS_VIEW");
const canRecharge = hasPermission("MEMBER_RECHARGE");
const canReverseRecharge = hasPermission("MEMBER_RECHARGE_REVERSE");
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  const groupedIds = segment.value ? new Set(operations.value.map(item => item.customerId)) : null;
  return customers.value.filter(customer => (!query || [customer.contactName, customer.phone, customer.memberCode]
    .some(value => value?.toLowerCase().includes(query)))
    && (!groupedIds || groupedIds.has(customer.customerId)));
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const params = new URLSearchParams({ keyword: keyword.value.trim() });
    const operationParams = new URLSearchParams();
    if (segment.value) operationParams.set("segment", segment.value);
    const [rows, summaries] = await Promise.all([
      api<VenueCustomer[]>(`/venue-admin/customers/list?${params}`),
      api<CustomerOperation[]>(`/venue-admin/customer-operations?${operationParams}`),
    ]);
    customers.value = rows;
    operations.value = summaries;
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "顾客加载失败";
  } finally {
    loading.value = false;
  }
}

async function loadTags() {
  tagsError.value = "";
  try {
    tags.value = await api<VenueCustomerTag[]>("/venue-admin/customer-tags");
  } catch (reason) {
    tagsError.value = reason instanceof Error ? reason.message : "顾客标签加载失败";
  }
}

async function loadFollowups() {
  followupsError.value = "";
  try {
    const params = new URLSearchParams();
    if (followupStatus.value) params.set("status", followupStatus.value);
    followups.value = await api<CustomerFollowup[]>(`/venue-admin/customer-followups?${params}`);
  } catch (reason) {
    followupsError.value = reason instanceof Error ? reason.message : "回访待办加载失败";
  }
}

function operationFor(customerId: number) {
  return operations.value.find(item => item.customerId === customerId);
}

function bookingSourceText(value: "ONLINE" | "OFFLINE" | null | undefined) {
  return value === "ONLINE" ? "线上" : value === "OFFLINE" ? "线下登记" : "来源未知";
}

function usualHoursText(value: string | null) {
  if (!value) return "暂无";
  return value.split(",").map(row => {
    const [hour, count] = row.split(":");
    return `${hour}时 · ${count} 次`;
  }).join("，");
}

async function createTag() {
  const name = newTagName.value.trim();
  if (!name || creatingTag.value) return;
  creatingTag.value = true;
  tagsError.value = "";
  actionMessage.value = "";
  try {
    await api("/venue-admin/customer-tags", { method: "POST", body: JSON.stringify({ name }) });
    newTagName.value = "";
    actionMessage.value = "标签已创建";
    await loadTags();
  } catch (reason) {
    tagsError.value = reason instanceof Error ? reason.message : "标签创建失败";
  } finally {
    creatingTag.value = false;
  }
}

async function deleteTag(tag: VenueCustomerTag) {
  if (!window.confirm(`删除“${tag.name}”会同时移除本馆全部关联，确定继续吗？`)) return;
  actionError.value = "";
  actionMessage.value = "";
  try {
    await api(`/venue-admin/customer-tags/${tag.id}`, { method: "DELETE" });
    actionMessage.value = `标签“${tag.name}”及其关联已删除`;
    await Promise.all([loadTags(), load()]);
    if (selected.value) await loadOperation(selected.value.customerId);
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : "标签删除失败";
  }
}

async function loadOperation(customerId: number) {
  operationsError.value = "";
  try {
    selectedOperation.value = await api<CustomerOperation>(`/venue-admin/customer-operations/${customerId}`);
  } catch (reason) {
    operationsError.value = reason instanceof Error ? reason.message : "顾客经营信息加载失败";
  }
}

async function changeCustomerTag(tag: VenueCustomerTag) {
  if (!selected.value || !canEditCustomerOperations) return;
  const isAssigned = selectedOperation.value?.tags.includes(tag.name);
  actionError.value = "";
  actionMessage.value = "";
  try {
    await api(`/venue-admin/customers/${selected.value.customerId}/tags/${tag.id}`, {
      method: isAssigned ? "DELETE" : "POST",
    });
    actionMessage.value = isAssigned ? `已移除标签“${tag.name}”` : `已添加标签“${tag.name}”`;
    await Promise.all([loadOperation(selected.value.customerId), load()]);
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : "顾客标签更新失败";
  }
}

async function createFollowup(customerId: number) {
  actionError.value = "";
  actionMessage.value = "";
  try {
    await api("/venue-admin/customer-followups", { method: "POST", body: JSON.stringify({
      customerId, purpose: "30_DAY_NO_REBOOK", sourceGroup: "30_DAY_NO_REBOOK",
    }) });
    actionMessage.value = "回访待办已创建";
    await loadFollowups();
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : "回访待办创建失败";
  }
}

function openFollowupUpdate(item: CustomerFollowup, status: "COMPLETED" | "NOT_NEEDED") {
  updatingFollowup.value = item;
  followupNextStatus.value = status;
  followupNote.value = "";
  followupDialog.value?.showModal();
}

async function updateFollowup() {
  if (!updatingFollowup.value || updatingFollowupBusy.value) return;
  updatingFollowupBusy.value = true;
  actionError.value = "";
  actionMessage.value = "";
  try {
    await api(`/venue-admin/customer-followups/${updatingFollowup.value.id}`, {
      method: "PATCH", body: JSON.stringify({ status: followupNextStatus.value, note: followupNote.value.trim() || null }),
    });
    actionMessage.value = followupNextStatus.value === "COMPLETED" ? "回访已标记完成" : "回访已标记无需处理";
    followupDialog.value?.close();
    await loadFollowups();
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : "回访待办更新失败";
  } finally {
    updatingFollowupBusy.value = false;
  }
}

async function loadPlans() {
  planError.value = "";
  try {
    plans.value = await api<RechargePlan[]>("/venue-admin/recharge-plans");
  } catch (reason) {
    planError.value = reason instanceof Error ? reason.message : "充值档位加载失败";
  }
}

async function loadMemberData(customerId: number) {
  const [account, rechargeList] = await Promise.all([
    api<BalanceAccount>(`/venue-admin/members/${customerId}/balance-flows`),
    api<MemberRecharge[]>(`/venue-admin/members/${customerId}/recharges`),
  ]);
  balance.value = account;
  recharges.value = rechargeList;
}

async function open(customer: VenueCustomer) {
  selected.value = customer;
  selectedOperation.value = operationFor(customer.customerId);
  balance.value = undefined;
  recharges.value = [];
  detailError.value = "";
  detailMessage.value = "";
  rechargeError.value = "";
  rechargePlanId.value = "";
  rechargeIdempotencyKey = "";
  drawer.value?.showModal();
  await loadDetail();
}

async function loadDetail() {
  if (!selected.value) return;
  detailLoading.value = true;
  detailError.value = "";
  try {
    const customerId = selected.value.customerId;
    const [detail] = await Promise.all([
      api<VenueCustomer>(`/venue-admin/customers/${customerId}`),
      loadOperation(customerId),
      canViewFunds ? loadMemberData(customerId) : Promise.resolve(),
    ]);
    selected.value = detail;
  } catch (reason) {
    detailError.value = reason instanceof Error ? reason.message : "顾客详情加载失败";
  } finally {
    detailLoading.value = false;
  }
}

async function recharge() {
  if (!selected.value || !rechargePlanId.value) return;
  recharging.value = true;
  rechargeError.value = "";
  detailMessage.value = "";
  rechargeIdempotencyKey ||= crypto.randomUUID();
  try {
    await api(`/venue-admin/members/${selected.value.customerId}/recharges`, {
      method: "POST",
      body: JSON.stringify({
        planId: rechargePlanId.value,
        channel: rechargeChannel.value,
        idempotencyKey: rechargeIdempotencyKey,
      }),
    });
    detailMessage.value = "会员充值已完成";
    rechargePlanId.value = "";
    rechargeIdempotencyKey = "";
    await Promise.all([canViewFunds ? loadMemberData(selected.value.customerId) : Promise.resolve(), load()]);
  } catch (reason) {
    rechargeError.value = reason instanceof Error ? reason.message : "会员充值失败";
  } finally {
    recharging.value = false;
  }
}

function openReverse(rechargeRecord: MemberRecharge) {
  reversingRecharge.value = rechargeRecord;
  reverseReason.value = "";
  offlineFundsHandled.value = false;
  reverseError.value = "";
  reverseDialog.value?.showModal();
}

async function reverseRecharge() {
  if (!selected.value || !reversingRecharge.value) return;
  reversing.value = true;
  reverseError.value = "";
  detailMessage.value = "";
  try {
    await api(`/venue-admin/recharges/${reversingRecharge.value.id}/reverse`, {
      method: "POST",
      body: JSON.stringify({ reason: reverseReason.value, offlineFundsHandled: offlineFundsHandled.value }),
    });
    reverseDialog.value?.close();
    detailMessage.value = `充值 ${reversingRecharge.value.rechargeNo} 已整笔撤销`;
    await Promise.all([canViewFunds ? loadMemberData(selected.value.customerId) : Promise.resolve(), load()]);
  } catch (reason) {
    reverseError.value = reason instanceof Error ? reason.message : "充值撤销失败";
  } finally {
    reversing.value = false;
  }
}

function followupStatusText(value: CustomerFollowup["status"]) {
  return { PENDING: "待处理", COMPLETED: "已完成", NOT_NEEDED: "无需处理" }[value];
}

function flowBusinessLabel(value: string) {
  return {
    RECHARGE: "充值入账",
    HOLD: "预约冻结",
    CONSUME: "预约消费",
    RELEASE: "冻结释放",
    REFUND: "取消返还",
    EXPIRE: "赠送到期",
    REVERSE: "充值撤销",
  }[value] || value;
}

function rechargeStatusText(value: MemberRecharge["status"]) {
  return { PENDING: "处理中", SUCCESS: "充值成功", FAILED: "充值失败", CLOSED: "已关闭", REVERSED: "已撤销" }[value];
}

function rechargeStatusTone(value: MemberRecharge["status"]) {
  if (value === "SUCCESS") return "active";
  if (value === "PENDING") return "warning";
  return "danger";
}

function channelText(value: MemberRecharge["channel"]) {
  return { MOCK: "线上 Mock", CASH: "现金", WECHAT: "微信", ALIPAY: "支付宝" }[value];
}

watch([rechargePlanId, rechargeChannel], () => {
  if (!recharging.value) rechargeIdempotencyKey = "";
});

onMounted(() => Promise.all([load(), loadTags(), loadFollowups(), canRecharge ? loadPlans() : Promise.resolve()]));
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>顾客与会员</h1><p>顾客档案、场馆内标签、分组和回访待办仅限当前场馆查看。</p></div>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="actionMessage" class="saved-message" role="status">{{ actionMessage }}</p>
    <p v-if="actionError" class="error-banner" role="alert">{{ actionError }}</p>
    <form class="filter-bar" aria-label="顾客搜索" @submit.prevent="load">
      <label class="field filter-search"><span>姓名、完整手机号或会员码</span><input v-model.trim="keyword" type="search"></label>
      <label class="field"><span>顾客分组</span><select v-model="segment"><option value="">全部顾客</option><option value="30_DAY_NO_REBOOK">30 天未再预约</option></select></label>
      <button class="button" type="submit" :disabled="loading"><Search :size="18" aria-hidden="true" />搜索顾客</button>
      <button class="icon-button filter-action" type="button" aria-label="刷新顾客" title="刷新顾客" :disabled="loading" @click="load"><RefreshCw :size="19" aria-hidden="true" /></button>
    </form>
    <section class="customer-ops-grid">
      <section class="data-panel" aria-label="场馆顾客标签"><header class="data-panel-head"><div><h2>顾客标签</h2><p>标签仅在当前场馆内使用，不改变会员身份或计价。</p></div><span class="record-count">{{ tags.length }} 个</span></header>
        <p v-if="tagsError" class="error-banner" role="alert">{{ tagsError }} <button class="inline-command" type="button" @click="loadTags">重试</button></p>
        <form v-if="canEditCustomerOperations" class="inline-form" @submit.prevent="createTag"><label class="field"><span>新增标签</span><input v-model.trim="newTagName" maxlength="40" required></label><button class="button" type="submit" :disabled="creatingTag || !newTagName.trim()">{{ creatingTag ? "正在创建" : "创建标签" }}</button></form>
        <ul v-if="tags.length" class="tag-list"><li v-for="tag in tags" :key="tag.id"><span><strong>{{ tag.name }}</strong><small>{{ tag.createdByName }} · {{ localDateTime(tag.createdAt) }}</small></span><button v-if="canEditCustomerOperations" class="button danger" type="button" @click="deleteTag(tag)">删除</button></li></ul>
        <div v-else-if="!tagsError" class="empty-state"><strong>暂无标签</strong></div>
      </section>
      <section class="data-panel" aria-label="回访待办"><header class="data-panel-head"><div><h2>回访待办</h2><p>来源为“30 天未再预约”分组；备注仅当前场馆授权人员可见。</p></div><span class="record-count">{{ followups.length }} 条</span></header>
        <form class="inline-form" @submit.prevent="loadFollowups"><label class="field"><span>待办状态</span><select v-model="followupStatus"><option value="">全部</option><option value="PENDING">待处理</option><option value="COMPLETED">已完成</option><option value="NOT_NEEDED">无需处理</option></select></label><button class="button secondary" type="submit">刷新待办</button></form>
        <p v-if="followupsError" class="error-banner" role="alert">{{ followupsError }} <button class="inline-command" type="button" @click="loadFollowups">重试</button></p>
        <ul v-if="followups.length" class="followup-list"><li v-for="item in followups" :key="item.id"><div><strong>{{ item.contactName || "未填写姓名" }} · {{ followupStatusText(item.status) }}</strong><small>{{ item.sourceGroup === "30_DAY_NO_REBOOK" ? "30 天未再预约" : item.sourceGroup }} · {{ item.createdByName }} 创建于 {{ localDateTime(item.createdAt) }}</small><small v-if="item.handledByName">{{ item.handledByName }} · {{ item.handledAt ? localDateTime(item.handledAt) : "" }} · {{ item.note || "无备注" }}</small><small v-if="item.handledAt">处理后日期的{{ item.rebookedAfterHandled ? `${bookingSourceText(item.rebookedAfterHandledSource)}有效预约场次：${localDateTime(item.rebookedAfterHandledAt)}` : "暂无有效预约场次" }}（按场次开始时间；不代表预约创建于处理后或由回访导致）</small></div><div v-if="canEditCustomerOperations && item.status === 'PENDING'" class="row-actions"><button class="button secondary" type="button" @click="openFollowupUpdate(item, 'COMPLETED')">完成</button><button class="button secondary" type="button" @click="openFollowupUpdate(item, 'NOT_NEEDED')">无需处理</button></div></li></ul>
        <div v-else-if="!followupsError" class="empty-state"><strong>暂无回访待办</strong></div>
      </section>
    </section>

    <section class="data-panel" aria-label="顾客列表">
      <header class="data-panel-head"><div><h2>{{ segment ? "30 天未再预约" : "场馆顾客" }}</h2><p>预约与核销来自本场馆线上预约事实；线下履约未跟踪不视为到场。</p></div><span class="record-count">{{ filtered.length }} 条</span></header>
      <div v-if="filtered.length" class="table-wrap">
        <table>
          <thead><tr><th>顾客</th><th>手机号</th><th>有效预约</th><th>最近有效预约</th><th>最近线上完成</th><th>最近线上核销</th><th>常约时段</th><th>会员码</th><th>身份</th><th>操作</th></tr></thead>
          <tbody><tr v-for="customer in filtered" :key="customer.customerId"><td data-label="顾客"><strong>{{ customer.contactName || "未填写" }}</strong><span v-if="operationFor(customer.customerId)?.tags.length" class="subline">{{ operationFor(customer.customerId)?.tags.join("、") }}</span></td><td data-label="手机号">{{ customer.phone || "未填写" }}</td><td data-label="有效预约">{{ operationFor(customer.customerId)?.validBookingCount ?? 0 }}</td><td data-label="最近有效预约">{{ operationFor(customer.customerId)?.lastValidBookingAt ? `${localDateTime(operationFor(customer.customerId)!.lastValidBookingAt!)} · ${bookingSourceText(operationFor(customer.customerId)?.lastValidBookingSource)}` : "暂无" }}</td><td data-label="最近线上完成">{{ operationFor(customer.customerId)?.lastCompletedAt ? localDateTime(operationFor(customer.customerId)!.lastCompletedAt!) : "暂无" }}</td><td data-label="最近线上核销">{{ operationFor(customer.customerId)?.latestVerifiedAt ? localDateTime(operationFor(customer.customerId)!.latestVerifiedAt!) : "暂无" }}</td><td data-label="常约时段">{{ usualHoursText(operationFor(customer.customerId)?.usualHours || null) }}</td><td data-label="会员码"><span class="numeric">{{ customer.memberCode }}</span></td><td data-label="身份"><span class="status" :data-tone="statusTone(customer.status)">{{ statusLabel(customer.status) }}</span></td><td data-label="操作"><div class="row-actions"><button class="button secondary" type="button" @click="open(customer)">查看与标签</button><button v-if="canEditCustomerOperations && operationFor(customer.customerId)?.segment === '30_DAY_NO_REBOOK'" class="button secondary" type="button" @click="createFollowup(customer.customerId)">创建回访</button></div></td></tr></tbody>
        </table>
      </div>
      <div v-else-if="!loading" class="empty-state"><UsersRound :size="28" aria-hidden="true" /><strong>暂无匹配顾客</strong><span>线上用户完成首次预约后会显示在这里。</span></div>
      <div v-else class="empty-state" aria-live="polite"><span>正在加载顾客...</span></div>
    </section>

    <dialog ref="drawer" class="drawer">
      <form method="dialog" class="modal-head"><h2>顾客详情</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <div v-if="selected" class="drawer-body customer-detail">
        <p v-if="detailError" class="error-banner" role="alert">{{ detailError }} <button class="inline-command" type="button" @click="loadDetail">重新加载</button></p>
        <p v-if="detailMessage" class="saved-message" aria-live="polite"><CircleCheck :size="17" aria-hidden="true" />{{ detailMessage }}</p>
        <div v-if="detailLoading" class="empty-state" aria-live="polite"><RefreshCw class="spin" :size="26" aria-hidden="true" /><strong>正在加载顾客详情</strong></div>
        <template v-else>
          <section class="customer-section" aria-labelledby="customer-summary-heading">
            <h3 id="customer-summary-heading">顾客档案</h3>
            <div class="detail-list customer-facts">
              <div><span>顾客</span><strong>{{ selected.contactName || "未填写" }}</strong></div>
              <div><span>手机号</span><strong>{{ selected.phone || "未填写" }}</strong></div>
              <div><span>会员码</span><strong class="numeric">{{ selected.memberCode }}</strong></div>
              <div><span>身份</span><strong>{{ statusLabel(selected.status) }}</strong></div>
              <div><span>建档时间</span><strong>{{ localDateTime(selected.createdAt) }}</strong></div>
              <div><span>会员激活</span><strong>{{ localDateTime(selected.activatedAt) }}</strong></div>
            </div>
          </section>

          <section class="customer-section" aria-labelledby="customer-operations-heading">
            <div class="section-heading"><div><h3 id="customer-operations-heading">预约习惯与标签</h3><p>线上核销仅记录已完成预约；线下履约未跟踪不推断到场。</p></div></div>
            <div v-if="selectedOperation" class="detail-list customer-facts">
              <div><span>有效预约次数</span><strong>{{ selectedOperation.validBookingCount }}</strong></div>
              <div><span>最近有效预约</span><strong>{{ selectedOperation.lastValidBookingAt ? `${localDateTime(selectedOperation.lastValidBookingAt)} · ${bookingSourceText(selectedOperation.lastValidBookingSource)}` : "暂无" }}</strong></div>
              <div><span>最近线上完成</span><strong>{{ selectedOperation.lastCompletedAt ? localDateTime(selectedOperation.lastCompletedAt) : "暂无" }}</strong></div>
              <div><span>最近线上核销</span><strong>{{ selectedOperation.latestVerifiedAt ? localDateTime(selectedOperation.latestVerifiedAt) : "暂无" }}</strong></div>
              <div><span>未来已确认预约</span><strong>{{ selectedOperation.futureConfirmedCount }}</strong></div>
              <div class="span-2"><span>常约时段</span><strong>{{ usualHoursText(selectedOperation.usualHours) }}</strong></div>
            </div>
            <p v-if="operationsError" class="error-banner" role="alert">{{ operationsError }}</p>
            <div v-if="tags.length" class="tag-list compact"><div v-for="tag in tags" :key="tag.id" class="tag-row"><span>{{ tag.name }}</span><button v-if="canEditCustomerOperations" class="button secondary" type="button" @click="changeCustomerTag(tag)">{{ selectedOperation?.tags.includes(tag.name) ? "移除" : "添加" }}</button><span v-else class="muted">{{ selectedOperation?.tags.includes(tag.name) ? "已添加" : "未添加" }}</span></div></div>
            <p v-else class="muted">暂无场馆标签。</p>
          </section>

          <section v-if="canViewFunds" class="customer-section" aria-labelledby="balance-heading">
            <div class="section-heading"><div><h3 id="balance-heading">会员余额</h3><p>赠送余额优先使用，实付余额长期有效。</p></div></div>
            <div v-if="balance" class="balance-strip">
              <div><WalletCards :size="19" aria-hidden="true" /><span>实付余额</span><strong>{{ money(balance.paidBalanceFen) }}</strong></div>
              <div><Gift :size="19" aria-hidden="true" /><span>赠送余额</span><strong>{{ money(balance.giftBalanceFen) }}</strong></div>
              <div :data-tone="balance.nextGiftExpiresAt ? 'warning' : undefined"><Clock3 :size="19" aria-hidden="true" /><span>最近到期</span><strong>{{ balance.nextGiftExpiresAt ? `${money(balance.nextGiftExpiresAmountFen)} · ${localDateTime(balance.nextGiftExpiresAt)}` : "暂无到期赠送额" }}</strong></div>
            </div>
          </section>

          <section v-if="canRecharge" class="customer-section" aria-labelledby="recharge-heading">
            <div class="section-heading"><div><h3 id="recharge-heading">登记充值</h3><p>选择已启用档位和实际收款渠道。</p></div></div>
            <p v-if="planError" class="error-banner" role="alert">{{ planError }} <button class="inline-command" type="button" @click="loadPlans">重新加载</button></p>
            <p v-if="rechargeError" class="error-banner" role="alert">{{ rechargeError }}</p>
            <form class="recharge-form" @submit.prevent="recharge">
              <label class="field"><span>充值档位</span><select v-model.number="rechargePlanId" required :disabled="recharging"><option disabled value="">请选择档位</option><option v-for="plan in plans.filter(item => item.status === 'ACTIVE')" :key="plan.id" :value="plan.id">{{ plan.name }} · {{ money(plan.paidAmountFen) }} + 赠送 {{ money(plan.giftAmountFen) }}</option></select></label>
              <label class="field"><span>收款渠道</span><select v-model="rechargeChannel" :disabled="recharging"><option value="CASH">现金</option><option value="WECHAT">微信</option><option value="ALIPAY">支付宝</option></select></label>
              <button class="button recharge-action" type="submit" :disabled="recharging || !rechargePlanId || !plans.some(item => item.status === 'ACTIVE')" :aria-busy="recharging"><WalletCards :size="17" aria-hidden="true" />{{ recharging ? "充值处理中" : "确认充值" }}</button>
            </form>
            <p v-if="!planError && !plans.some(item => item.status === 'ACTIVE')" class="muted">当前没有启用的充值档位，请先到“充值档位”页面创建或启用。</p>
          </section>

          <section v-if="canViewFunds" class="customer-section" aria-labelledby="recharge-history-heading">
            <div class="section-heading"><div><h3 id="recharge-history-heading">充值记录</h3><p>撤销仅适用于从未发生消费的整笔充值。</p></div><span class="record-count">{{ recharges.length }} 条</span></div>
            <div v-if="recharges.length" class="table-wrap">
              <table>
                <thead><tr><th>充值</th><th>金额</th><th>渠道</th><th>状态</th><th>时间</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="record in recharges" :key="record.id">
                    <td data-label="充值"><strong>{{ record.planName }}</strong><span class="subline numeric">{{ record.rechargeNo }}</span></td>
                    <td data-label="金额"><strong class="numeric">{{ money(record.paidAmountFen) }}</strong><span class="subline">赠送 {{ money(record.giftAmountFen) }}</span></td>
                    <td data-label="渠道">{{ channelText(record.channel) }}</td>
                    <td data-label="状态"><span class="status status-with-icon" :data-tone="rechargeStatusTone(record.status)"><CircleCheck v-if="record.status === 'SUCCESS'" :size="15" aria-hidden="true" /><Clock3 v-else-if="record.status === 'PENDING'" :size="15" aria-hidden="true" /><CircleX v-else :size="15" aria-hidden="true" />{{ rechargeStatusText(record.status) }}</span></td>
                    <td data-label="时间">{{ localDateTime(record.succeededAt || record.createdAt) }}</td>
                    <td data-label="操作"><button v-if="canReverseRecharge" class="button danger reverse-action" type="button" :disabled="record.status !== 'SUCCESS'" @click="openReverse(record)"><TriangleAlert :size="16" aria-hidden="true" />整笔撤销</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="empty-state"><WalletCards :size="26" aria-hidden="true" /><strong>暂无充值记录</strong><span>充值成功后会保留档位、渠道和金额快照。</span></div>
          </section>

          <section v-if="canViewFunds" class="customer-section" aria-labelledby="flow-heading">
            <div class="section-heading"><div><h3 id="flow-heading">余额流水</h3><p>充值、冻结、消费、释放、退款、到期和撤销均保留记录。</p></div><span class="record-count">{{ balance?.flows.length || 0 }} 条</span></div>
            <div v-if="balance?.flows.length" class="table-wrap">
              <table>
                <thead><tr><th>业务</th><th>余额类型</th><th>变动</th><th>关联</th><th>时间</th></tr></thead>
                <tbody>
                  <tr v-for="flow in balance.flows" :key="flow.id">
                    <td data-label="业务"><strong>{{ flowBusinessLabel(flow.businessType) }}</strong><span class="subline">批次 {{ flow.lotId }}</span></td>
                    <td data-label="余额类型">{{ flow.balanceType === "GIFT" ? "赠送余额" : "实付余额" }}</td>
                    <td data-label="变动"><span class="flow-amount" :data-tone="flow.direction === 'CREDIT' ? 'active' : 'danger'"><ArrowDownLeft v-if="flow.direction === 'CREDIT'" :size="16" aria-hidden="true" /><ArrowUpRight v-else :size="16" aria-hidden="true" />{{ flow.direction === "CREDIT" ? "+" : "-" }}{{ money(flow.amountFen) }}</span></td>
                    <td data-label="关联">{{ flow.bookingId ? `预约 ${flow.bookingId}` : flow.rechargeId ? `充值 ${flow.rechargeId}` : "—" }}</td>
                    <td data-label="时间">{{ localDateTime(flow.createdAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="empty-state"><WalletCards :size="26" aria-hidden="true" /><strong>暂无余额流水</strong><span>余额发生变化后会显示在这里。</span></div>
          </section>
        </template>
      </div>
    </dialog>

    <dialog ref="followupDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>{{ followupNextStatus === "COMPLETED" ? "完成回访" : "无需处理" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="updateFollowup">
        <p>{{ updatingFollowup?.contactName || "当前顾客" }} · {{ followupNextStatus === "COMPLETED" ? "确认已完成回访" : "确认无需处理" }}</p>
        <label class="field"><span>{{ followupNextStatus === "COMPLETED" ? "处理备注" : "无需处理原因" }}</span><textarea v-model="followupNote" maxlength="255" :disabled="updatingFollowupBusy"></textarea></label>
        <div class="modal-actions"><button class="button secondary" type="button" :disabled="updatingFollowupBusy" @click="followupDialog?.close()">取消</button><button class="button" type="submit" :disabled="updatingFollowupBusy">{{ updatingFollowupBusy ? "正在保存" : "确认" }}</button></div>
      </form>
    </dialog>

    <dialog ref="reverseDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>确认整笔撤销</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="reverseRecharge">
        <div v-if="reversingRecharge" class="destructive-summary">
          <TriangleAlert :size="22" aria-hidden="true" />
          <div><strong>{{ reversingRecharge.planName }} · {{ money(reversingRecharge.paidAmountFen) }}</strong><span>撤销会同时冲回实付与赠送余额，且不可部分撤销。</span></div>
        </div>
        <p v-if="reverseError" class="error-banner" role="alert">{{ reverseError }}</p>
        <label class="field"><span>撤销原因</span><textarea v-model.trim="reverseReason" required :disabled="reversing"></textarea></label>
        <label class="confirmation"><input v-model="offlineFundsHandled" type="checkbox" required :disabled="reversing"><span>{{ reversingRecharge?.channel === "MOCK" ? "确认继续撤销整笔充值" : "已在线下完成对应资金处理，并确认继续撤销整笔充值" }}</span></label>
        <div class="modal-actions"><button class="button secondary" type="button" :disabled="reversing" @click="reverseDialog?.close()">返回检查</button><button class="button danger stable-reverse" type="submit" :disabled="reversing || !offlineFundsHandled" :aria-busy="reversing"><TriangleAlert :size="17" aria-hidden="true" />{{ reversing ? "正在撤销" : "确认整笔撤销" }}</button></div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.customer-detail {
  align-content: start;
}

.customer-ops-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-md); }
.inline-form { display: flex; align-items: end; gap: var(--space-sm); padding: var(--space-md); }
.inline-form .field { flex: 1; }
.tag-list, .followup-list { display: grid; gap: var(--space-xs); margin: 0; padding: var(--space-md); list-style: none; }
.tag-list li, .followup-list li, .tag-row { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); padding: var(--space-sm); border: 1px solid var(--color-rule-2); border-radius: var(--radius-md); }
.tag-list li > span, .followup-list li > div:first-child { display: grid; gap: var(--space-2xs); min-width: 0; }
.tag-list small, .followup-list small { color: var(--color-muted); font-size: var(--text-xs); }
.tag-list .button { min-height: 2.25rem; }
.tag-list.compact { padding: 0; }
.tag-row { justify-content: start; }
.tag-row span:first-child { flex: 1; }
@media (max-width: 48rem) { .customer-ops-grid { grid-template-columns: 1fr; } .inline-form { align-items: stretch; flex-direction: column; } }

.customer-section {
  display: grid;
  gap: var(--space-sm);
  padding-block-end: var(--space-md);
  border-block-end: 1px solid var(--color-rule-2);
}

.customer-section:last-child {
  border-block-end: 0;
}

.section-heading {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: var(--space-sm);
}

.section-heading > div {
  display: grid;
  gap: var(--space-2xs);
}

.section-heading p,
.subline,
.destructive-summary span {
  color: var(--color-muted);
  font-size: var(--text-xs);
}

.subline {
  display: block;
}

.balance-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-block: 1px solid var(--color-rule-2);
}

.balance-strip > div {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-xs);
  min-width: 0;
  padding: var(--space-sm);
}

.balance-strip > div + div {
  border-inline-start: 1px solid var(--color-rule-2);
}

.balance-strip span {
  color: var(--color-muted);
  font-size: var(--text-xs);
}

.balance-strip strong {
  grid-column: 1 / -1;
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.balance-strip > div[data-tone="warning"] {
  color: var(--color-warning-strong);
}

.recharge-form {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(8rem, 0.75fr) auto;
  align-items: end;
  gap: var(--space-sm);
}

.recharge-action {
  min-width: 7rem;
}

.reverse-action {
  width: 7rem;
}

.flow-amount {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  white-space: nowrap;
}

.flow-amount[data-tone="active"] {
  color: var(--color-success-strong);
}

.flow-amount[data-tone="danger"] {
  color: var(--color-danger-strong);
}

.destructive-summary,
.confirmation {
  display: flex;
  align-items: start;
  gap: var(--space-sm);
}

.destructive-summary {
  padding: var(--space-md);
  border-inline-start: var(--space-2xs) solid var(--color-danger);
  background: var(--color-danger-soft);
  color: var(--color-danger-strong);
}

.destructive-summary > div {
  display: grid;
  gap: var(--space-2xs);
}

.confirmation {
  min-height: var(--touch-target);
  cursor: pointer;
}

.confirmation input {
  width: 1.25rem;
  height: 1.25rem;
  margin: 0;
}

.stable-reverse {
  min-width: 9rem;
}

@media (max-width: 40rem) {
  .balance-strip,
  .recharge-form {
    grid-template-columns: 1fr;
  }

  .balance-strip > div + div {
    border-inline-start: 0;
    border-block-start: 1px solid var(--color-rule-2);
  }

  .section-heading {
    flex-direction: column;
  }

  .recharge-action {
    width: 100%;
  }
}
</style>
