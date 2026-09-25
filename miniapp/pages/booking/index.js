const { request, money, date, time, dateTime } = require("../../utils/api");

const labels = { PENDING_PAYMENT: "待支付", CONFIRMED: "待核销", COMPLETED: "已完成", CANCELLED: "已取消", NO_SHOW: "未到场" };
const paymentLabels = { PAYING: "待支付", PAID: "支付成功", PAYMENT_FAILED: "支付失败", REFUNDING: "退款处理中", REFUNDED: "已退款", REFUND_FAILED: "退款异常" };
const externalPaymentLabels = { PENDING: "结果待确认", SUCCESS: "支付成功", FAILED: "支付失败", CLOSED: "支付已关闭" };
const refundLabels = { PROCESSING: "退款处理中", SUCCESS: "退款成功", FAILED: "退款失败", CLOSED: "退款已关闭" };

function amount(value) {
  return Number.isFinite(value) ? money(value) : "--";
}

Page({
  data: {
    id: 0, detail: null, booking: null, credential: null, composition: null, payment: null, refund: null,
    statusLabel: "", paymentLabel: "", paymentTone: "warning", date: "", time: "", amount: "",
    canPay: false, error: "", loading: true, cancelling: false,
  },
  async onLoad(options) { this.setData({ id: Number(options.id) }); await this.load(); },
  async onShow() {
    if (this.data.id) await this.load();
    this.stopCredentialRefresh();
    // 动态二维码按分钟签发，页面停留期间定时换新，避免用户到场时凭证已过期。
    this.credentialTimer = setInterval(() => {
      if (this.data.booking && this.data.booking.status === "CONFIRMED") this.load();
    }, 30000);
  },
  onHide() { this.stopCredentialRefresh(); },
  onUnload() { this.stopCredentialRefresh(); },
  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const detail = await request(`/mini/bookings/${this.data.id}`);
      const booking = detail.booking;
      const composition = detail.composition;
      const hasComposition = composition
        && Number.isFinite(composition.giftBalanceAmountFen)
        && Number.isFinite(composition.paidBalanceAmountFen)
        && Number.isFinite(composition.externalAmountFen);
      const paymentStatus = booking.paymentStatus;
      this.setData({
        detail,
        booking,
        credential: detail.credential,
        composition: hasComposition ? {
          ...composition,
          giftBalanceAmount: amount(composition.giftBalanceAmountFen),
          paidBalanceAmount: amount(composition.paidBalanceAmountFen),
          externalAmount: amount(composition.externalAmountFen),
          lots: (composition.lots || []).map(lot => ({
            ...lot,
            typeLabel: lot.type === "GIFT" ? "赠送余额" : "实付余额",
            amount: amount(lot.amountFen),
            expiresAtLabel: lot.expiresAt ? dateTime(lot.expiresAt) : "",
          })),
        } : null,
        payment: detail.payment ? {
          ...detail.payment,
          amount: amount(detail.payment.amountFen),
          statusLabel: externalPaymentLabels[detail.payment.status] || detail.payment.status,
          statusTone: detail.payment.status === "SUCCESS" ? "active" : detail.payment.status === "PENDING" ? "warning" : "danger",
        } : null,
        refund: detail.refund ? {
          ...detail.refund,
          amount: amount(detail.refund.amountFen),
          statusLabel: refundLabels[detail.refund.status] || detail.refund.status,
          statusTone: detail.refund.status === "SUCCESS" ? "active" : ["FAILED", "CLOSED"].includes(detail.refund.status) ? "danger" : "warning",
          nextRetryAtLabel: detail.refund.nextRetryAt ? dateTime(detail.refund.nextRetryAt) : "",
          retryLabel: Number.isFinite(detail.refund.retryCount) ? `已尝试 ${detail.refund.retryCount} 次` : "",
        } : null,
        statusLabel: labels[booking.status] || booking.status,
        paymentLabel: paymentLabels[paymentStatus] || paymentStatus || "收款状态不可用",
        paymentTone: ["PAID", "REFUNDED"].includes(paymentStatus) ? "active" : ["PAYMENT_FAILED", "REFUND_FAILED"].includes(paymentStatus) ? "danger" : "warning",
        date: date(booking.startAt),
        time: `${time(booking.startAt)}–${time(booking.endAt)}`,
        amount: amount(booking.amountFen),
        canPay: booking.status === "PENDING_PAYMENT" && hasComposition && composition.externalAmountFen > 0 && detail.payment?.status === "PENDING",
        error: "",
      });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  pay() { wx.redirectTo({ url: `/pages/payment/index?id=${this.data.id}` }); },
  stopCredentialRefresh() {
    if (this.credentialTimer) clearInterval(this.credentialTimer);
    this.credentialTimer = null;
  },
  async cancel() {
    const confirmed = await new Promise(resolve => wx.showModal({ title: "取消预约", content: "取消后会模拟原路退款，并重新开放场次。", confirmText: "确认取消", success: result => resolve(result.confirm) }));
    if (!confirmed) return;
    this.setData({ cancelling: true, error: "" });
    try { await request(`/mini/bookings/${this.data.id}/cancel`, { method: "POST" }); await this.load(); }
    catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ cancelling: false }); }
  },
});
