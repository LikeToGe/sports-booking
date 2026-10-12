const { request, assetUrl } = require("../../utils/api");
const { reportContent } = require("../../utils/content");

Page({
  data: { venueId: 0, score: 0, tagId: 0, withImages: false, scores: [5, 4, 3, 2, 1], reviews: [], summary: { reviewCount: 0, tagCounts: [] }, loading: true, error: "",message: "" },
  async onLoad(options) {
    this.setData({ venueId: Number(options.venueId) });
    await this.load();
  },
  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const params = [];
      if (this.data.score) params.push(`score=${this.data.score}`);
      if (this.data.tagId) params.push(`tagId=${this.data.tagId}`);
      if (this.data.withImages) params.push("withImages=true");
      const query = params.length ? `?${params.join("&")}` : "";
      const [summary, reviews] = await Promise.all([
        request(`/public/venues/${this.data.venueId}/review-summary`),
        request(`/public/venues/${this.data.venueId}/reviews${query}`),
      ]);
      this.setData({ summary, reviews: reviews.map(review => ({
        ...review, media: (review.media || []).map(item => ({ ...item, fullUrl: assetUrl(item.url) })),
      })) });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  async selectScore(event) {
    this.setData({ score: Number(event.currentTarget.dataset.score) });
    await this.load();
  },
  async selectTag(event) {
    this.setData({ tagId: Number(event.currentTarget.dataset.tagId) });
    await this.load();
  },
  async toggleImages() {
    this.setData({ withImages: !this.data.withImages });
    await this.load();
  },
  previewImage(event) {
    const review = this.data.reviews.find(item => item.id === Number(event.currentTarget.dataset.reviewId));
    if (!review) return;
    wx.previewImage({ current: event.currentTarget.dataset.url,
      urls: review.media.map(item => item.fullUrl) });
  },
  async report(event) {
    try {
      if (await reportContent("REVIEW",event.currentTarget.dataset.id)) this.setData({ message: "举报已提交" });
    } catch (error) { this.setData({ error: error.message }); }
  },
  onShareAppMessage() {
    return { title: "场馆评价",path: `/pages/venue-reviews/index?venueId=${this.data.venueId}&shared=1` };
  },
});
