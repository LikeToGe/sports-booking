const { request, money } = require("../../utils/api");

Page({
  data: {
    venueId: 0, customerId: 0, customerName: "会员", plans: [], selectedPlanId: 0,
    channels: [{ value: "CASH", label: "现金" }, { value: "WECHAT", label: "微信" }, { value: "ALIPAY", label: "支付宝" }],
    channelIndex: 0, loading: true, submitting: false, error: "", result: null, clientIdempotencyKey: "",
  },
  async onLoad(options) {
    this.setData({ venueId: Number(options.venueId), customerId: Number(options.customerId), customerName: decodeURIComponent(options.name || "会员") });
    try {
      const plans = await request(`/mini/recharges/plans?venueId=${this.data.venueId}`);
      this.setData({ plans: plans.map(item => ({ ...item, amountLabel: `${money(item.paidAmountFen)} + 赠送 ${money(item.giftAmountFen)}` })) });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  selectPlan(event) { this.setData({ selectedPlanId: Number(event.currentTarget.dataset.id), result: null, error: "" }); },
  selectChannel(event) { this.setData({ channelIndex: Number(event.detail.value), result: null, error: "" }); },
  async submit() {
    if (!this.data.selectedPlanId) return;
    const clientIdempotencyKey = this.data.clientIdempotencyKey || `staff-recharge-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    this.setData({ submitting: true, error: "", clientIdempotencyKey });
    try {
      const result = await request("/staff/recharges", {
        method: "POST",
        data: {
          venueId: this.data.venueId,
          customerId: this.data.customerId,
          planId: this.data.selectedPlanId,
          channel: this.data.channels[this.data.channelIndex].value,
          clientIdempotencyKey,
        },
      });
      this.setData({ result, clientIdempotencyKey: "" });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ submitting: false }); }
  },
});
