const { request, money, time } = require("../../utils/api");

Page({
  data: { slot: null, contactName: "", contactPhone: "", loading: false, error: "" },
  async onLoad() {
    const slot = wx.getStorageSync("yuechangPendingSlot");
    if (!slot) return wx.navigateBack();
    this.setData({ slot: { ...slot, dateLabel: slot.startAt.slice(0, 10) } });
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
      const booking = await request("/mini/bookings/create", { method: "POST", data: { sessionSlotId: this.data.slot.id, contactName: this.data.contactName, contactPhone: this.data.contactPhone } });
      wx.removeStorageSync("yuechangPendingSlot");
      wx.redirectTo({ url: `/pages/payment/index?id=${booking.id}` });
    } catch (error) { this.setData({ error: error.message, loading: false }); }
  },
});
