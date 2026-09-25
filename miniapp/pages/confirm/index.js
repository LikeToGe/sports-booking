const { request, money, date, time } = require("../../utils/api");

Page({
  data: { slot: null, contactName: "", contactPhone: "", idempotencyKey: "", loading: false, error: "" },
  async onLoad() {
    const slot = wx.getStorageSync("yuechangPendingSlot");
    if (!slot) return wx.navigateBack();
    this.setData({
      slot: { ...slot, dateLabel: date(slot.startAt) },
      idempotencyKey: `mini-booking-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    });
    try {
      const profile = await request("/auth/mini/profile/detail");
      this.setData({ contactName: profile.contactName || "", contactPhone: profile.phone || "" });
    } catch (error) { this.setData({ error: error.message }); }
  },
  onName(event) { this.setData({ contactName: event.detail.value }); },
  onPhone(event) { this.setData({ contactPhone: event.detail.value }); },
  async submit() {
    this.setData({ loading: true, error: "" });
    try {
      const booking = await request("/mini/bookings/create", { method: "POST", data: { sessionSlotId: this.data.slot.id, contactName: this.data.contactName, contactPhone: this.data.contactPhone, idempotencyKey: this.data.idempotencyKey } });
      wx.removeStorageSync("yuechangPendingSlot");
      let status = booking.status;
      try {
        const detail = await request(`/mini/bookings/${booking.id}`);
        status = detail.booking.status;
      } catch (_) {
        // 创建已成功时仍以创建结果继续，详情页会再次读取服务端终态。
      }
      wx.redirectTo({ url: status === "CONFIRMED" ? `/pages/booking/index?id=${booking.id}` : `/pages/payment/index?id=${booking.id}` });
    } catch (error) { this.setData({ error: error.message, loading: false }); }
  },
});
