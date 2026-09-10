const { request, money, time } = require("../../utils/api");

const labels = { PENDING_PAYMENT: "待支付", CONFIRMED: "待核销", COMPLETED: "已完成", CANCELLED: "已取消", NO_SHOW: "未到场" };

Page({
  data: { id: 0, detail: null, booking: null, credential: null, statusLabel: "", date: "", time: "", amount: "", error: "", loading: true },
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
    try {
      const detail = await request(`/mini/bookings/${this.data.id}`);
      const booking = detail.booking;
      this.setData({ detail, booking, credential: detail.credential, statusLabel: labels[booking.status] || booking.status, date: booking.startAt.slice(0, 10), time: `${time(booking.startAt)}–${time(booking.endAt)}`, amount: money(booking.amountFen), error: "" });
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
    try { await request(`/mini/bookings/${this.data.id}/cancel`, { method: "POST" }); await this.load(); }
    catch (error) { this.setData({ error: error.message }); }
  },
});
