const { request, downloadMedia } = require("../../utils/api");
const { reportContent } = require("../../utils/content");
Page({
  data: { profile: null,message: "",error: "",loading: true },
  async onLoad(options) {
    try {
      const profile = await request(`/mini/posts/users/${Number(options.id)}`);
      this.setData({ profile });
      const posts = await Promise.all(profile.posts.map(async post => {
        const image = (post.media || []).find(item => item.mediaType === "IMAGE");
        if (!image) return post;
        try { return { ...post, previewPath: await downloadMedia(image.url) }; }
        catch (_) { return { ...post, previewError: true }; }
      }));
      this.setData({ profile: { ...profile, posts } });
    }
    catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  async reportUser() {
    if (!this.data.profile) return;
    try {
      if (await reportContent("USER", this.data.profile.id)) this.setData({ message: "账号举报已提交", error: "" });
    } catch (error) { if (error.message) this.setData({ error: error.message }); }
  },
  open(event) { wx.navigateTo({ url: `/pages/posts/detail?id=${event.currentTarget.dataset.id}` }); },
});
