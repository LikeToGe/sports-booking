const { request, money } = require("../../utils/api");

const paymentLabels = {
  PAYING: "待支付",
  PAID: "支付成功",
  PAYMENT_FAILED: "支付失败",
  REFUNDING: "退款处理中",
  REFUNDED: "已退款",
  REFUND_FAILED: "退款异常",
};
const externalPaymentLabels = { PENDING: "支付结果待确认", SUCCESS: "支付成功", FAILED: "支付失败", CLOSED: "支付已关闭" };

function amount(value) {
  return Number.isFinite(value) ? money(value) : "--";
}

Page({
  data: {
    id: 0, detail: null, booking: null, composition: null, amount: "", seconds: null,
    countdown: "--:--", paymentLabel: "", paymentTone: "warning", canPay: false,
    canQuery: false, canCancel: false, loading: true, paying: false, querying: false, cancelling: false, error: "", message: "",
  },

  async onLoad(options) {
    this.setData({ id: Number(options.id) });
    await this.load();
    this.startCountdown();
  },

  onUnload() { clearInterval(this.timer); },

  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const detail = await request(`/mini/bookings/${this.data.id}`);
      if (detail.booking.status === "CONFIRMED") {
        return wx.redirectTo({ url: `/pages/booking/index?id=${this.data.id}` });
      }
      const composition = detail.composition;
      const hasComposition = composition
        && Number.isFinite(composition.giftBalanceAmountFen)
        && Number.isFinite(composition.paidBalanceAmountFen)
        && Number.isFinite(composition.externalAmountFen);
      const externalAmountFen = hasComposition ? composition.externalAmountFen : null;
      const paymentPending = detail.payment?.status === "PENDING";
      const pendingBooking = detail.booking.status === "PENDING_PAYMENT";
      const expires = detail.booking.paymentExpiresAt ? new Date(detail.booking.paymentExpiresAt).getTime() : NaN;
      const seconds = Number.isFinite(expires) ? Math.max(0, Math.floor((expires - Date.now()) / 1000)) : null;
      const paymentStatus = detail.booking.paymentStatus;
      this.setData({
        detail,
        booking: detail.booking,
        composition: hasComposition ? {
          ...composition,
          giftBalanceAmount: amount(composition.giftBalanceAmountFen),
          paidBalanceAmount: amount(composition.paidBalanceAmountFen),
          externalAmount: amount(composition.externalAmountFen),
        } : null,
        amount: amount(detail.booking.amountFen),
        seconds,
        countdown: seconds === null ? "--:--" : this.countdown(seconds),
        paymentLabel: detail.payment ? (externalPaymentLabels[detail.payment.status] || detail.payment.status) : (paymentLabels[paymentStatus] || "支付状态不可用"),
        paymentTone: detail.payment?.status === "SUCCESS" ? "active" : ["FAILED", "CLOSED"].includes(detail.payment?.status) || ["PAYMENT_FAILED", "REFUND_FAILED"].includes(paymentStatus) ? "danger" : "warning",
        canPay: pendingBooking && externalAmountFen > 0 && paymentPending && (seconds === null || seconds > 0),
        canQuery: pendingBooking && externalAmountFen > 0 && paymentPending,
        canCancel: pendingBooking,
      });
    } catch (error) {
      this.setData({ error: error.message });
    } finally {
      this.setData({ loading: false });
    }
  },

  startCountdown() {
    clearInterval(this.timer);
    this.timer = setInterval(() => {
      if (this.data.seconds === null || this.data.seconds <= 0) return;
      const seconds = this.data.seconds - 1;
      this.setData({ seconds, countdown: this.countdown(seconds), canPay: seconds > 0 && this.data.canPay });
    }, 1000);
  },

  async pay() {
    if (!this.data.canPay || this.data.paying || this.data.querying || this.data.cancelling) return;
    this.setData({ paying: true, error: "", message: "" });
    try {
      await request(`/mini/bookings/${this.data.id}/mock-pay`, { method: "POST" });
      await this.load();
      if (this.data.booking?.status === "PENDING_PAYMENT") this.setData({ message: "支付结果尚未确认，请查询支付结果。" });
    } catch (error) {
      this.setData({ error: error.message });
    } finally {
      this.setData({ paying: false });
    }
  },

  async queryPayment() {
    if (!this.data.canQuery || this.data.paying || this.data.querying || this.data.cancelling) return;
    this.setData({ querying: true, error: "", message: "" });
    try {
      await request(`/mini/bookings/${this.data.id}/payment-query`, { method: "POST" });
      await this.load();
      if (this.data.booking?.status === "PENDING_PAYMENT") this.setData({ message: "支付结果仍待确认，可稍后再次查询。" });
    } catch (error) {
      this.setData({ error: error.message });
    } finally {
      this.setData({ querying: false });
    }
  },

  async cancel() {
    if (!this.data.canCancel || this.data.paying || this.data.querying || this.data.cancelling) return;
    const confirmed = await new Promise(resolve => wx.showModal({ title: "取消待支付预约",
      content: "系统会先请求关闭未支付订单。结果未确认时保留占位，请稍后重试。",
      confirmText: "确认取消", success: result => resolve(result.confirm) }));
    if (!confirmed || this.data.paying || this.data.querying || this.data.cancelling) return;
    this.setData({ cancelling: true, error: "", message: "" });
    try {
      const booking = await request(`/mini/bookings/${this.data.id}/cancel`, { method: "POST" });
      if (booking.status === "CANCELLED") wx.redirectTo({ url: `/pages/booking/index?id=${this.data.id}` });
      else await this.load();
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ cancelling: false }); }
  },

  openBooking() { wx.redirectTo({ url: `/pages/booking/index?id=${this.data.id}` }); },
  countdown(seconds) { return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`; },
});
