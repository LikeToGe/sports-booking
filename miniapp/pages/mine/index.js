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
    ugcEnabled: false,closing: false,showProfileForm: false,
  },
  async onShow() {
    const app = getApp();
    this.setData({ showLocalDebug: isDevelopment(), apiBase: app.globalData.apiBase });
    await this.load();
  },
  async load() {
    try {
      const [profile, staffVenues, ugcEnabled] = await Promise.all([
        request("/auth/mini/profile/detail"),
        request("/staff/venues"),
        request("/public/ugc-config"),
      ]);
      this.setData({
        profile, staffVenues, ugcEnabled,contactName: profile.contactName, phone: profile.phone,
        staffActive: staffVenues.length > 0,
        canVerify: staffVenues.some(item => item.permissions.includes("BOOKING_VERIFY")),
        canIdentifyMember: staffVenues.some(item => item.permissions.includes("CUSTOMER_VIEW")),
        error: "",
      });
    } catch (error) { this.setData({ error: error.message }); }
  },
  toggleProfileForm() { this.setData({ showProfileForm: !this.data.showProfileForm }); },
  openBookings() { wx.switchTab({ url: "/pages/bookings/index" }); },
  openFavourites() { wx.navigateTo({ url: "/pages/favourites/index" }); },
  openReminders() { wx.navigateTo({ url: "/pages/reminders/index" }); },
  openMembership() { wx.switchTab({ url: "/pages/membership/index" }); },
  onName(event) { this.setData({ contactName: event.detail.value, saved: false }); },
  onPhone(event) { this.setData({ phone: event.detail.value, saved: false }); },
  async save() {
    try { await request("/auth/mini/profile/update", { method: "PUT", data: { contactName: this.data.contactName, phone: this.data.phone } }); this.setData({ saved: true, error: "", showProfileForm: false, profile: { ...this.data.profile, contactName: this.data.contactName, phone: this.data.phone } }); }
    catch (error) { this.setData({ error: error.message }); }
  },
  bindStaff() { wx.navigateTo({ url: "/pages/staff-bind/index" }); },
  verify() { wx.navigateTo({ url: "/pages/staff-verify/index" }); },
  records() { wx.navigateTo({ url: "/pages/staff-records/index" }); },
  webLogin() { wx.navigateTo({ url: "/pages/staff-web-login/index" }); },
  onApiBase(event) { this.setData({ apiBase: event.detail.value, connectionMessage: "" }); },
  community(event) { wx.navigateTo({ url: `/pages/community/index?mode=${event.currentTarget.dataset.mode}` }); },
  agreement() { wx.navigateTo({ url: "/pages/content-agreement/index" }); },
  async closeAccount() {
    if (this.data.closing) return;
    const result = await new Promise(resolve => wx.showModal({ title: "注销账号",
      content: "注销不可恢复。余额须已结清且没有未结束预约；注销后同一身份无法重新登录。",confirmText: "注销",success: resolve }));
    if (!result.confirm) return;
    this.setData({ closing: true,error: "" });
    try {
      await request("/auth/mini/close-account",{ method: "POST" });
      const app = getApp();
      app.globalData.token = ""; app.globalData.profile = null;
      wx.removeStorageSync("yuechangMiniToken");
      this.setData({ profile: null,contactName: "",phone: "",staffVenues: [],staffActive: false,error: "账号已注销" });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ closing: false }); }
  },
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
