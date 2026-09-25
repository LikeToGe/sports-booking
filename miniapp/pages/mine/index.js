const { request } = require("../../utils/api");

function isDevelopment() {
  try {
    return wx.getAccountInfoSync().miniProgram.envVersion !== "release";
  } catch (_) {
    return true;
  }
}

Page({
  data: {
    profile: null, contactName: "", phone: "", staffVenues: [], staffActive: false,
    canVerify: false, canIdentifyMember: false, error: "", saved: false,
    showLocalDebug: false, apiBase: "", reconnecting: false, connectionMessage: "",
  },
  async onShow() {
    const app = getApp();
    this.setData({ showLocalDebug: isDevelopment(), apiBase: app.globalData.apiBase });
    await this.load();
  },
  async load() {
    try {
      const [profile, staffVenues] = await Promise.all([
        request("/auth/mini/profile/detail"),
        request("/staff/venues"),
      ]);
      this.setData({
        profile, staffVenues, contactName: profile.contactName, phone: profile.phone,
        staffActive: staffVenues.length > 0,
        canVerify: staffVenues.some(item => item.permissions.includes("BOOKING_VERIFY")),
        canIdentifyMember: staffVenues.some(item => item.permissions.includes("CUSTOMER_VIEW")),
        error: "",
      });
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
  webLogin() { wx.navigateTo({ url: "/pages/staff-web-login/index" }); },
  onApiBase(event) { this.setData({ apiBase: event.detail.value, connectionMessage: "" }); },
  async reconnect() {
    const app = getApp();
    this.setData({ reconnecting: true, error: "", connectionMessage: "" });
    try {
      const apiBase = app.setApiBase(this.data.apiBase);
      await app.ensureLogin();
      this.setData({ apiBase, connectionMessage: "连接成功" });
      wx.reLaunch({ url: "/pages/home/index" });
    } catch (error) {
      this.setData({ error: `连接失败：${error.message || error.errMsg || "请检查地址和后端服务"}` });
    } finally {
      this.setData({ reconnecting: false });
    }
  },
});
