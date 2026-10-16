const { request } = require("../../utils/api");
const { requireText } = require("../../utils/validation");
Page({ data: { code: "", error: "", loading: false }, onCode(event) { this.setData({ code: event.detail.value }); }, async bind() { this.setData({ loading: true, error: "" }); try { requireText(this.data.code, "员工绑定码"); await request("/mini/staff/bind", { method: "POST", data: { bindingCode: this.data.code } }); wx.navigateBack(); } catch (error) { this.setData({ error: error.message, loading: false }); } } });
