const { request } = require("../../utils/api");
const { requireText } = require("../../utils/validation");

Page({
  data: {
    venues: [], selectedIndex: 0, current: null, mode: "booking", code: "", memberIdentifier: "",
    result: null, memberResult: null, error: "", loading: true, canVerify: false, canIdentify: false, canRecharge: false,
  },
  async onShow() {
    this.setData({ loading: true, error: "" });
    try {
      const venues = await request("/staff/venues");
      const remembered = wx.getStorageSync("yuechangStaffVenueId");
      const selectedIndex = Math.max(0, venues.findIndex(item => item.venueId === remembered));
      this.applyVenue(venues, selectedIndex);
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  applyVenue(venues, selectedIndex) {
    const current = venues[selectedIndex] || null;
    const permissions = current?.permissions || [];
    const canVerify = permissions.includes("BOOKING_VERIFY");
    const canIdentify = permissions.includes("CUSTOMER_VIEW");
    wx.setStorageSync("yuechangStaffVenueId", current?.venueId || "");
    this.setData({ venues, selectedIndex, current, canVerify, canIdentify, canRecharge: permissions.includes("MEMBER_RECHARGE"), mode: canVerify ? "booking" : "member", result: null, memberResult: null, error: "" });
  },
  selectVenue(event) { this.applyVenue(this.data.venues, Number(event.detail.value)); },
  selectMode(event) { this.setData({ mode: event.currentTarget.dataset.mode, result: null, memberResult: null, error: "" }); },
  onCode(event) { this.setData({ code: event.detail.value }); },
  onMemberIdentifier(event) { this.setData({ memberIdentifier: event.detail.value }); },
  scan() {
    wx.scanCode({
      onlyFromCamera: true, scanType: ["qrCode"],
      success: result => this.data.mode === "booking" ? this.verify(result.result, "QR") : this.identifyMember(result.result),
      fail: reason => { if (!String(reason.errMsg).includes("cancel")) this.setData({ error: "无法读取二维码，请手动输入" }); },
    });
  },
  submitCode() { this.verify(this.data.code, "SHORT_CODE"); },
  submitMember() { this.identifyMember(this.data.memberIdentifier); },
  async verify(credential, method) {
    if (!this.data.current) { this.setData({ error: "请选择工作场馆" }); return; }
    this.setData({ loading: true, error: "", result: null });
    try {
      requireText(credential, method === "SHORT_CODE" ? "6 位核销码" : "核销二维码");
      if (method === "SHORT_CODE" && !/^\d{6}$/.test(credential.trim())) throw new Error("核销码必须是 6 位数字");
      const result = await request(`/staff/verifications/verify?venueId=${this.data.current.venueId}`, {
        method: "POST", data: { credential, method },
      });
      this.setData({ result });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  async identifyMember(identifier) {
    if (!this.data.current) { this.setData({ error: "请选择工作场馆" }); return; }
    this.setData({ loading: true, error: "", memberResult: null });
    try {
      requireText(identifier, "会员码或完整手机号");
      const key = /^\d{11}$/.test(identifier) ? "phone" : "memberCode";
      const memberResult = await request(`/staff/members?venueId=${this.data.current.venueId}&${key}=${encodeURIComponent(identifier)}`);
      this.setData({ memberResult });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  recharge() {
    const { current, memberResult } = this.data;
    wx.navigateTo({ url: `/pages/staff-recharge/index?venueId=${current.venueId}&customerId=${memberResult.customerId}&name=${encodeURIComponent(memberResult.contactName || "会员")}` });
  },
  records() { wx.navigateTo({ url: `/pages/staff-records/index?venueId=${this.data.current?.venueId || ""}` }); },
});
