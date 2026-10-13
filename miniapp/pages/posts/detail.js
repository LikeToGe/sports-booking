const { request, downloadMedia, dateTime } = require("../../utils/api");
const { reportContent } = require("../../utils/content");

const recruitmentLevels = { BEGINNER_FRIENDLY: "新手友好", CASUAL: "休闲", ADVANCED: "进阶" };
const recruitmentLabels = { RECRUITING: "招募中", FULL: "已满员", CLOSED: "已关闭", BOOKING_CANCELLED: "预约已取消", ENDED: "已结束" };
const memberLabels = { APPLIED: "申请中", APPROVED: "已加入", REJECTED: "未通过", LEFT: "已退出", REMOVED: "已移除" };

Page({
  data: { id: null, post: null, comments: [], body: "", parentId: null, editingCommentId: null,
    draftLabel: "", levelOptions: ["新手友好", "休闲", "进阶"], levelValues: ["BEGINNER_FRIENDLY", "CASUAL", "ADVANCED"], levelIndex: 0, recruitment: null, level: "", maxMembers: 1, feeMode: "FREE", aaAmount: "",
    recruitmentDescription: "", loading: true, saving: false, error: "", message: "" },
  onLoad(options) {
    this.setData({ id: Number(options.id) });
    if (options.shared) request("/mini/content/events?eventType=SHARE_OPEN&targetType=POST&targetId=" + options.id,
      { method: "POST" }).catch(() => {});
    this.load();
  },
  async load() {
    this.setData({ loading: true });
    try {
      const post = await request(`/mini/posts/${this.data.id}`);
      const media = await Promise.all((post.media || []).map(async item => ({
        ...item, localPath: await downloadMedia(item.url),
      })));
      const [comments, recruitment] = await Promise.all([
        request(`/mini/posts/${this.data.id}/comments`), request(`/mini/posts/${this.data.id}/recruitment`),
      ]);
      this.setData({ post: { ...post, media }, comments,
        recruitment: recruitment ? { ...recruitment, levelLabel: recruitment.levelTag
          ? recruitmentLevels[recruitment.levelTag] : `其他说明：${recruitment.level || "历史水平"}`,
          statusLabel: recruitmentLabels[recruitment.status], myStatusLabel: memberLabels[recruitment.myStatus] || "",
          timeLabel: dateTime(recruitment.startAt), feeLabel: recruitment.feeMode === "FREE" ? "免费（平台不代收）"
            : `预计 AA ¥${(recruitment.aaAmountFen / 100).toFixed(2)}（平台不代收）`,
          active: ["RECRUITING", "FULL"].includes(recruitment.status),
          members: recruitment.members.map(member => ({ ...member, statusLabel: memberLabels[member.status] })),
        } : null, error: "" });
    } catch (error) {
      this.setData({ post: null,comments: [],recruitment: null,
        error: error.message === "数据不存在" ? "动态已失效或当前不可见" : error.message });
    } finally { this.setData({ loading: false }); }
  },
  editBody(event) { this.setData({ body: event.detail.value }); },
  reply(event) {
    this.setData({ parentId: Number(event.currentTarget.dataset.id),editingCommentId: null,body: "",draftLabel: "回复评论" });
  },
  editComment(event) {
    const comment = this.data.comments.find(item => item.id === Number(event.currentTarget.dataset.id));
    if (comment) this.setData({ editingCommentId: comment.id,parentId: null,body: comment.body,draftLabel: "编辑评论" });
  },
  cancelDraft() { this.setData({ body: "",parentId: null,editingCommentId: null,draftLabel: "" }); },
  async submitComment() {
    if (this.data.saving || !this.data.body.trim()) return;
    this.setData({ saving: true, error: "" });
    try {
      const editingId = this.data.editingCommentId;
      const comment = await request(editingId ? `/mini/posts/comments/${editingId}` : `/mini/posts/${this.data.id}/comments`,
        { method: editingId ? "PUT" : "POST",data: {
          body: this.data.body.trim(),parentCommentId: editingId ? undefined : this.data.parentId,
        } });
      this.cancelDraft();
      this.setData({ message: comment.moderationStatus === "PENDING" ? "评论已提交审核" : "评论已发布" });
      await this.load();
    } catch (error) {
      this.setData({ error: error.message });
      if (error.message === "请先接受当前社区规则与隐私说明") wx.navigateTo({ url: "/pages/content-agreement/index" });
    } finally { this.setData({ saving: false }); }
  },
  async removeComment(event) {
    const id = Number(event.currentTarget.dataset.id);
    const result = await new Promise(resolve => wx.showModal({ title: "删除评论",content: "确认删除这条评论？",success: resolve }));
    if (!result.confirm) return;
    try { await request(`/mini/posts/comments/${id}`,{ method: "DELETE" }); await this.load(); }
    catch (error) { this.setData({ error: error.message }); }
  },
  async toggleLike() {
    if (this.data.saving || !this.data.post) return;
    this.setData({ saving: true });
    try {
      const liked = !this.data.post.liked;
      const likeCount = await request(`/mini/posts/${this.data.id}/likes?liked=${liked}`,{ method: "POST" });
      this.setData({ post: { ...this.data.post,liked,likeCount } });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ saving: false }); }
  },
  async toggleComments(event) {
    try {
      await request(`/mini/posts/${this.data.id}/comments-enabled?enabled=${event.detail.value}`,{ method: "PUT" });
      await this.load();
    } catch (error) { this.setData({ error: error.message }); }
  },
  async report(event) {
    const targetId = Number(event.currentTarget.dataset.id || this.data.id);
    const targetType = event.currentTarget.dataset.type || "POST";
    try {
      if (await reportContent(targetType,targetId)) this.setData({ message: "举报已提交" });
    } catch (error) {
      if (error.message) this.setData({ error: error.message });
    }
  },
  async blockAuthor() {
    if (!this.data.post || this.data.post.authorType !== "USER" || this.data.post.isMine) return;
    const result = await new Promise(resolve => wx.showModal({ title: "拉黑用户",content: "双方动态和互动将不再互相可见。",success: resolve }));
    if (!result.confirm) return;
    try {
      await request(`/mini/posts/blocks/${this.data.post.authorId}`,{ method: "POST" });
      this.setData({ post: null,comments: [],recruitment: null,message: "已拉黑" });
    } catch (error) { this.setData({ error: error.message }); }
  },
  openUser(event) {
    const id = event.currentTarget.dataset.id || this.data.post?.authorId;
    if (id) wx.navigateTo({ url: `/pages/community/user?id=${id}` });
  },
  async openVenue() {
    if (!this.data.post?.venueId) return;
    await request(`/mini/content/events?eventType=POST_TO_VENUE&targetType=VENUE&targetId=${this.data.post.venueId}`,
      { method: "POST" }).catch(() => {});
    wx.navigateTo({ url: `/pages/venue/index?id=${this.data.post.venueId}` });
  },
  selectLevel(event) { const index = Number(event.detail.value) || 0; this.setData({ level: this.data.levelValues[index], levelIndex: index }); },
  onRecruitmentField(event) { this.setData({ [event.currentTarget.dataset.field]: event.detail.value }); },
  selectFee(event) { this.setData({ feeMode: event.detail.value }); },
  async openRecruitment() {
    if (!this.data.post?.bookingId || this.data.saving) return;
    this.setData({ saving: true,error: "" });
    try {
      let amount = 0;
      if (this.data.feeMode === "AA") {
        if (!/^\d+(\.\d{1,2})?$/.test(this.data.aaAmount)) throw new Error("金额最多两位小数");
        const [whole,fraction = ""] = this.data.aaAmount.split(".");
        amount = Number(whole) * 100 + Number(fraction.padEnd(2,"0"));
        if (!Number.isSafeInteger(amount) || amount > 2147483647) throw new Error("金额超出范围");
      }
      await request(`/mini/posts/${this.data.id}/recruitment`,{ method: "POST",data: {
        bookingId: this.data.post.bookingId,level: this.data.level,maxMembers: Number(this.data.maxMembers),
        feeMode: this.data.feeMode,aaAmountFen: amount,description: this.data.recruitmentDescription,
      } });
      await this.load();
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ saving: false }); }
  },
  async recruitmentAction(event) {
    if (this.data.saving || !this.data.recruitment) return;
    const action = event.currentTarget.dataset.action;
    const userId = event.currentTarget.dataset.userId;
    const base = `/mini/posts/recruitments/${this.data.recruitment.id}`;
    let path = action === "approve" || action === "reject"
      ? `${base}/members/${userId}/decide?approve=${action === "approve"}` : `${base}/${action}`;
    if (action === "remove") {
      const result = await new Promise(resolve => wx.showModal({ title: "移除成员",editable: true,
        placeholderText: "移除原因",success: resolve }));
      if (!result.confirm || !result.content.trim()) return;
      path = `${base}/members/${userId}/remove?reason=${encodeURIComponent(result.content.trim())}`;
    }
    this.setData({ saving: true,error: "" });
    try { await request(path,{ method: "POST" }); await this.load(); }
    catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ saving: false }); }
  },
  previewImage(event) {
    const urls = this.data.post.media.filter(item => item.mediaType === "IMAGE").map(item => item.localPath);
    wx.previewImage({ current: event.currentTarget.dataset.path, urls });
  },
  onShareAppMessage() {
    return { title: this.data.post?.body.slice(0,40) || "运动动态",path: `/pages/posts/detail?id=${this.data.id}&shared=1` };
  },
});
