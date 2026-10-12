const { request } = require("../../utils/api");

Page({
  data: { agreement: null, checked: false, loading: true, saving: false, error: "" },
  async onLoad() {
    try { this.setData({ agreement: await request("/mini/content/agreement") }); }
    catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  toggle(event) { this.setData({ checked: event.detail.value.length > 0 }); },
  async accept() {
    if (!this.data.checked || this.data.saving || !this.data.agreement) return;
    this.setData({ saving: true, error: "" });
    try {
      await request("/mini/content/agreement", { method: "POST", data: {
        rulesVersion: this.data.agreement.rulesVersion,
        privacyVersion: this.data.agreement.privacyVersion,
      } });
      wx.navigateBack();
    } catch (error) {
      this.setData({ error: error.message, saving: false });
    }
  },
});
