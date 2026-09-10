const { request } = require("../../utils/api");

Page({
  data: { profile: null, contactName: "", phone: "", staffActive: false, error: "", saved: false },
  async onShow() { await this.load(); },
  async load() {
    try {
      const profile = await request("/auth/mini/profile/detail");
      this.setData({ profile, contactName: profile.contactName, phone: profile.phone, staffActive: profile.staff?.status === "ACTIVE", error: "" });
    } catch (error) { this.setData({ error: error.message }); }
  },
  onName(event) { this.setData({ contactName: event.detail.value, saved: false }); },
  onPhone(event) { this.setData({ phone: event.detail.value, saved: false }); },
  async save() {
    try { await request("/auth/mini/profile/update", { method: "PUT", data: { contactName: this.data.contactName, phone: this.data.phone } }); this.setData({ saved: true, error: "" }); }
    catch (error) { this.setData({ error: error.message }); }
  },
  bindStaff() { wx.navigateTo({ url: "/pages/staff-bind/index" }); },
  verify() { wx.navigateTo({ url: "/pages/staff-verify/index" }); },
  records() { wx.navigateTo({ url: "/pages/staff-records/index" }); },
});
