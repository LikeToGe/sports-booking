const { request } = require("../../utils/api");
Page({
  data: { profile: null,error: "",loading: true },
  async onLoad(options) {
    try { this.setData({ profile: await request(`/mini/posts/users/${Number(options.id)}`) }); }
    catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  open(event) { wx.navigateTo({ url: `/pages/posts/detail?id=${event.currentTarget.dataset.id}` }); },
});
