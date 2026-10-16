const { request, money, date, time } = require("../../utils/api");
const { validateContact } = require("../../utils/validation");

Page({
  data: { slot: null, contactName: "", contactPhone: "", idempotencyKey: "", loading: true, error: "" },
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
    finally { this.setData({ loading: false }); }
  },
  onName(event) { this.setData({ contactName: event.detail.value }); },
  onPhone(event) { this.setData({ contactPhone: event.detail.value }); },
  async submit() {
    this.setData({ loading: true, error: "" });
    try {
      validateContact(this.data.contactName, this.data.contactPhone);
      if (!this.data.slot?.id) throw new Error("场次信息已失效，请返回重新选择");
      const booking = await request("/mini/bookings/create", { method: "POST", data: { sessionSlotId: this.data.slot.id, contactName: this.data.contactName, contactPhone: this.data.contactPhone, idempotencyKey: this.data.idempotencyKey, expectedPriceFen: this.data.slot.priceFen, entryType: this.data.slot.entryType || null } });
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
