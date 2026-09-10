const { request, money } = require("../../utils/api");

Page({
  data: { id: 0, detail: null, amount: "", seconds: 600, countdown: "10:00", loading: false, error: "" },
  async onLoad(options) {
    this.setData({ id: Number(options.id) });
    await this.load();
    this.timer = setInterval(() => {
      const seconds = Math.max(0, this.data.seconds - 1);
      this.setData({ seconds, countdown: this.countdown(seconds) });
      if (!seconds) clearInterval(this.timer);
    }, 1000);
  },
  onUnload() { clearInterval(this.timer); },
  async load() {
    try {
      const detail = await request(`/mini/bookings/${this.data.id}`);
      const expires = new Date(detail.booking.paymentExpiresAt).getTime();
      const seconds = Math.max(0, Math.floor((expires - Date.now()) / 1000));
      this.setData({ detail, amount: money(detail.booking.amountFen), seconds, countdown: this.countdown(seconds) });
    } catch (error) { this.setData({ error: error.message }); }
  },
  async pay() {
    this.setData({ loading: true, error: "" });
    try { await request(`/mini/bookings/${this.data.id}/mock-pay`, { method: "POST" }); wx.redirectTo({ url: `/pages/booking/index?id=${this.data.id}` }); }
    catch (error) { this.setData({ error: error.message, loading: false }); }
  },
  countdown(seconds) { return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`; },
});
