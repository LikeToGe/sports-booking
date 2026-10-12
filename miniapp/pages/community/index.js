const { request, dateTime } = require("../../utils/api");
const statuses = { PENDING: "处理中",RESOLVED: "已处理",REJECTED: "未通过",UPHELD: "维持处置",
  REVERSED: "已撤销",PUBLISHED: "已公开",HIDDEN: "已隐藏",APPROVED: "已通过" };
const targets = { POST: "动态",COMMENT: "评论",REVIEW: "评价",REVIEW_REPLY: "官方回复",USER: "用户" };
const actions = { REJECT: "拒绝",HIDE: "隐藏",DELETE: "删除",RESTRICT: "限制内容权限" };
const paths = { messages: "/mini/content/notifications",posts: "/mini/posts/mine",reports: "/mini/content/reports",
  appeals: "/mini/content/appeals",decisions: "/mini/content/decisions",blocks: "/mini/posts/blocks" };
Page({
  data: { mode: "messages",rows: [],loading: true,error: "",message: "",ugcEnabled: false },
  onLoad(options) { if (paths[options.mode]) this.setData({ mode: options.mode }); },
  onShow() { this.load(); },
  async load() {
    this.setData({ loading: true,error: "" });
    try {
      const ugcEnabled = await request("/public/ugc-config");
      this.setData({ ugcEnabled,mode: ugcEnabled ? this.data.mode : "messages" });
      const rows = await request(paths[this.data.mode]);
      this.setData({ rows: rows.map(row => ({ ...row,
        id: row.id || row.userId,
        statusLabel: statuses[row.status || row.contentStatus] || "",timeLabel: dateTime(row.createdAt),
        targetLabel: targets[row.targetType] || "内容",actionLabel: actions[row.action] || row.action,
      })) });
    } catch (error) { this.setData({ error: error.message,rows: [] }); }
    finally { this.setData({ loading: false }); }
  },
  selectMode(event) { this.setData({ mode: event.currentTarget.dataset.mode }); this.load(); },
  async openMessage(event) {
    const row = this.data.rows.find(item => item.id === Number(event.currentTarget.dataset.id));
    if (!row) return;
    try {
      await request(`/mini/content/notifications/${row.id}/read`,{ method: "POST" });
      if (row.businessType === "POST") wx.navigateTo({ url: `/pages/posts/detail?id=${row.businessId}` });
      else await this.load();
    } catch (error) { this.setData({ error: error.message }); }
  },
  editPost(event) {
    getApp().globalData.editPostId = Number(event.currentTarget.dataset.id);
    wx.switchTab({ url: "/pages/posts/index" });
  },
  openPost(event) { wx.navigateTo({ url: `/pages/posts/detail?id=${event.currentTarget.dataset.id}` }); },
  async removePost(event) {
    const result = await new Promise(resolve => wx.showModal({ title: "删除动态",content: "删除后立即退出公开展示。",success: resolve }));
    if (!result.confirm) return;
    try { await request(`/mini/posts/${event.currentTarget.dataset.id}`,{ method: "DELETE" }); await this.load(); }
    catch (error) { this.setData({ error: error.message }); }
  },
  async appeal(event) {
    const result = await new Promise(resolve => wx.showModal({ title: "提交申诉",editable: true,
      placeholderText: "申诉说明",success: resolve }));
    if (!result.confirm || !result.content.trim()) return;
    try {
      await request(`/mini/content/appeals?moderationLogId=${event.currentTarget.dataset.id}&body=${encodeURIComponent(result.content.trim())}`,
        { method: "POST" });
      this.setData({ message: "申诉已提交" }); await this.load();
    } catch (error) { this.setData({ error: error.message }); }
  },
  async unblock(event) {
    try {
      await request(`/mini/posts/blocks/${event.currentTarget.dataset.id}`,{ method: "DELETE" });
      await this.load();
    } catch (error) { this.setData({ error: error.message }); }
  },
});
