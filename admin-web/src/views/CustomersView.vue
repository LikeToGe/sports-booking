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
const keyword = ref("");
const error = ref("");
const loading = ref(false);
const selected = ref<VenueCustomer>();
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
  return customers.value.filter(customer => !query || [customer.contactName, customer.phone, customer.memberCode]
    .some(value => value?.toLowerCase().includes(query)));
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    customers.value = await api<VenueCustomer[]>(`/venue-admin/customers/list?keyword=${encodeURIComponent(keyword.value.trim())}`);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "顾客加载失败";
  } finally {
    loading.value = false;
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

onMounted(() => Promise.all([load(), canRecharge ? loadPlans() : Promise.resolve()]));
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>顾客与会员</h1><p>用户首次在本场馆预约后形成独立顾客档案。</p></div>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <form class="filter-bar" aria-label="顾客搜索" @submit.prevent="load">
      <label class="field filter-search"><span>姓名、完整手机号或会员码</span><input v-model.trim="keyword" type="search"></label>
      <button class="button" type="submit" :disabled="loading"><Search :size="18" aria-hidden="true" />搜索顾客</button>
      <button class="icon-button filter-action" type="button" aria-label="刷新顾客" title="刷新顾客" :disabled="loading" @click="load"><RefreshCw :size="19" aria-hidden="true" /></button>
    </form>
    <section class="data-panel" aria-label="顾客列表">
      <header class="data-panel-head"><div><h2>场馆顾客</h2><p>会员将在首次成功充值后激活</p></div><span class="record-count">{{ filtered.length }} 条</span></header>
      <div v-if="filtered.length" class="table-wrap">
        <table>
          <thead><tr><th>顾客</th><th>手机号</th><th>会员码</th><th>身份</th><th>建档时间</th><th>操作</th></tr></thead>
          <tbody><tr v-for="customer in filtered" :key="customer.customerId"><td data-label="顾客"><strong>{{ customer.contactName || "未填写" }}</strong></td><td data-label="手机号">{{ customer.phone || "未填写" }}</td><td data-label="会员码"><span class="numeric">{{ customer.memberCode }}</span></td><td data-label="身份"><span class="status" :data-tone="statusTone(customer.status)">{{ statusLabel(customer.status) }}</span></td><td data-label="建档时间">{{ localDateTime(customer.createdAt) }}</td><td data-label="操作"><button class="button secondary" type="button" @click="open(customer)">查看详情</button></td></tr></tbody>
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
