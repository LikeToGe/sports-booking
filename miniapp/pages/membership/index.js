const { request, money, dateTime } = require("../../utils/api");

const flowLabels = {
  RECHARGE: "充值入账",
  HOLD: "预约冻结",
  CONSUME: "预约消费",
  RELEASE: "冻结释放",
  REFUND: "取消返还",
  EXPIRE: "赠送到期",
  REVERSE: "充值撤销",
};
const rechargeLabels = { PENDING: "待支付", SUCCESS: "充值成功", FAILED: "充值失败", CLOSED: "已关闭", REVERSED: "已撤销" };

function amount(value) {
  return Number.isFinite(value) ? money(value) : "--";
}

Page({
  data: {
    memberships: [], selectedIndex: 0, current: null, plans: [], flows: [], selectedPlanId: null, rechargeIdempotencyKey: "",
    recharge: null, loading: true, switching: false, creatingRecharge: false, payingRecharge: false,
    error: "", actionError: "", actionMessage: "", actionMessageTone: "info", showMemberCode: false,
  },

  async onShow() {
    await this.load();
  },

  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },

  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const memberships = await request("/mini/memberships/list");
      const selectedIndex = Math.min(this.data.selectedIndex, Math.max(0, memberships.length - 1));
      this.setData({ memberships, selectedIndex });
      if (memberships[selectedIndex]) await this.loadVenue(memberships[selectedIndex].venueId);
      else this.setData({ current: null, plans: [], flows: [] });
    } catch (error) {
      this.setData({ error: error.message });
    } finally {
      this.setData({ loading: false });
    }
  },

  toggleMemberCode() { this.setData({ showMemberCode: !this.data.showMemberCode }); },

  async selectMembership(event) {
    const selectedIndex = Number(event.detail.value);
    this.setData({ selectedIndex, switching: true, showMemberCode: false, error: "", actionError: "", actionMessage: "", recharge: null });
    try {
      await this.loadVenue(this.data.memberships[selectedIndex].venueId);
    } catch (error) {
      this.setData({ error: error.message });
    } finally {
      this.setData({ switching: false });
    }
  },

  async loadVenue(venueId) {
    const [membership, account, plans] = await Promise.all([
      request(`/mini/memberships/${venueId}`),
      request(`/mini/memberships/${venueId}/balance-flows`),
      request(`/mini/recharges/plans?venueId=${venueId}`),
    ]);
    this.setData({
      current: {
        ...membership,
        statusLabel: membership.status === "MEMBER" ? "储值会员" : "普通顾客",
        paidBalance: amount(account.paidBalanceFen),
        giftBalance: amount(account.giftBalanceFen),
        expiringGiftBalance: amount(account.nextGiftExpiresAmountFen),
        nextGiftExpiresAtLabel: dateTime(account.nextGiftExpiresAt),
        hasExpiry: Number.isFinite(account.nextGiftExpiresAmountFen) && Boolean(account.nextGiftExpiresAt),
      },
      plans: plans.map(plan => ({
        ...plan,
        paidAmount: amount(plan.paidAmountFen),
        giftAmount: amount(plan.giftAmountFen),
        giftLabel: plan.giftAmountFen > 0 ? `赠 ${amount(plan.giftAmountFen)}` : "无赠送",
        validityLabel: plan.giftAmountFen > 0 && plan.giftValidDays ? `${plan.giftValidDays} 天有效` : "长期有效",
      })),
      flows: account.flows.map(flow => ({
        ...flow,
        businessLabel: flowLabels[flow.businessType] || flow.businessType,
        balanceLabel: flow.balanceType === "GIFT" ? "赠送余额" : "实付余额",
        signedAmount: `${flow.direction === "CREDIT" ? "+" : "-"}${amount(flow.amountFen)}`,
        tone: flow.direction === "CREDIT" ? "credit" : "debit",
        createdAtLabel: dateTime(flow.createdAt),
      })),
      selectedPlanId: plans.some(plan => plan.id === this.data.selectedPlanId) ? this.data.selectedPlanId : null,
    });
  },

  selectPlan(event) {
    if (this.data.creatingRecharge || this.data.payingRecharge) return;
    this.setData({ selectedPlanId: Number(event.currentTarget.dataset.id), rechargeIdempotencyKey: "", actionError: "", actionMessage: "" });
  },

  async createRecharge() {
    if (!this.data.selectedPlanId || !this.data.current) return;
    const idempotencyKey = this.data.rechargeIdempotencyKey
      || `mini-recharge-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    this.setData({ creatingRecharge: true, rechargeIdempotencyKey: idempotencyKey, actionError: "", actionMessage: "" });
    try {
      const recharge = await request("/mini/recharges/create", {
        method: "POST",
        data: {
          venueId: this.data.current.venueId,
          planId: this.data.selectedPlanId,
          idempotencyKey,
        },
      });
      this.setData({ recharge: this.presentRecharge(recharge), rechargeIdempotencyKey: "", actionMessage: "充值单已创建，请继续完成 Mock 支付。", actionMessageTone: "info" });
    } catch (error) {
      this.setData({ actionError: error.message });
    } finally {
      this.setData({ creatingRecharge: false });
    }
  },

  async payRecharge() {
    if (!this.data.recharge || this.data.recharge.status !== "PENDING") return;
    this.setData({ payingRecharge: true, actionError: "", actionMessage: "" });
    try {
      await request(`/mini/recharges/${this.data.recharge.id}/mock-pay`, { method: "POST" });
      await this.refreshRecharge();
    } catch (error) {
      this.setData({ actionError: error.message });
    } finally {
      this.setData({ payingRecharge: false });
    }
  },

  async refreshRecharge() {
    if (!this.data.recharge) return;
    try {
      const recharge = await request(`/mini/recharges/${this.data.recharge.id}`);
      const presented = this.presentRecharge(recharge);
      this.setData({
        recharge: presented,
        actionError: "",
        actionMessage: presented.status === "SUCCESS" ? "充值成功，余额已更新。" : "充值状态已刷新。",
        actionMessageTone: presented.status === "SUCCESS" ? "success" : "info",
      });
      if (presented.status === "SUCCESS") await this.loadVenue(this.data.current.venueId);
    } catch (error) {
      this.setData({ actionError: error.message });
    }
  },

  resetRecharge() {
    this.setData({ recharge: null, selectedPlanId: null, actionError: "", actionMessage: "" });
  },

  presentRecharge(recharge) {
    return {
      ...recharge,
      statusLabel: rechargeLabels[recharge.status] || recharge.status,
      statusTone: recharge.status === "SUCCESS" ? "active" : recharge.status === "PENDING" ? "warning" : "danger",
      paidAmount: amount(recharge.paidAmountFen),
      giftAmount: amount(recharge.giftAmountFen),
      createdAtLabel: dateTime(recharge.createdAt),
    };
  },
});
