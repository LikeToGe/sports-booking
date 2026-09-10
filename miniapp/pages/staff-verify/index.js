const { request } = require("../../utils/api");
Page({
  data: { code: "", result: null, error: "", loading: false },
  onCode(event) { this.setData({ code: event.detail.value }); },
  scan() { wx.scanCode({ onlyFromCamera: true, scanType: ["qrCode"], success: result => this.verify(result.result, "QR"), fail: reason => { if (!String(reason.errMsg).includes("cancel")) this.setData({ error: "无法读取二维码，请使用短码" }); } }); },
  submitCode() { this.verify(this.data.code, "SHORT_CODE"); },
  async verify(credential, method) { this.setData({ loading: true, error: "", result: null }); try { const booking = await request("/staff/verifications/verify", { method: "POST", data: { credential, method } }); this.setData({ result: booking }); } catch (error) { this.setData({ error: error.message }); } finally { this.setData({ loading: false }); } },
  records() { wx.navigateTo({ url: "/pages/staff-records/index" }); },
});
