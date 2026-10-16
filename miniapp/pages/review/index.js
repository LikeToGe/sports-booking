const { request, uploadFile, downloadMedia } = require("../../utils/api");
const { requireText } = require("../../utils/validation");

Page({
  data: {
    bookingId: 0, review: null, body: "", overallScore: 5, courtScore: 5,
    environmentScore: 5, serviceScore: 5, anonymous: false,
    tags: [], selectedImages: [], existingImages: [], imagesCleared: false,
    loading: true, saving: false, error: "", message: "",
  },
  async onLoad(options) {
    this.setData({ bookingId: Number(options.bookingId) });
    await this.load();
  },
  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const [detail, review] = await Promise.all([
        request(`/mini/bookings/${this.data.bookingId}`),
        request(`/mini/bookings/${this.data.bookingId}/review`),
      ]);
      const tags = await request(`/public/review-tags?sportTypeId=${detail.booking.sportTypeId}`);
      const existingImages = await Promise.all((review?.media || []).map(async item => ({
        ...item, path: await downloadMedia(`/mini/content/media/reviews/${item.id}`),
      })));
      this.setData({
        review,
        selectedImages: [], existingImages, imagesCleared: false,
        tags: tags.map(tag => ({ ...tag, selected: (review?.tagIds || []).includes(tag.id) })),
        body: review?.body || "",
        overallScore: review?.overallScore || 5,
        courtScore: review?.courtScore || 5,
        environmentScore: review?.environmentScore || 5,
        serviceScore: review?.serviceScore || 5,
        anonymous: Boolean(review?.anonymous),
      });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  editBody(event) { this.setData({ body: event.detail.value }); },
  async chooseImages() {
    try {
      const result = await new Promise((resolve, reject) => wx.chooseMedia({
        count: 3, mediaType: ["image"], sourceType: ["album", "camera"],
        success: resolve, fail: reject,
      }));
      const files = result.tempFiles || [];
      if (files.length > 3 || files.some(file => file.fileType !== "image" || file.size > 5 * 1024 * 1024)) {
        this.setData({ error: "最多选择 3 张图片，每张不超过 5 MB" });
        return;
      }
      this.setData({ selectedImages: files.map(file => ({ path: file.tempFilePath })),
        existingImages: [], imagesCleared: true, error: "" });
    } catch (_) { }
  },
  clearImages() { this.setData({ selectedImages: [], existingImages: [], imagesCleared: true }); },
  toggleAnonymous(event) { this.setData({ anonymous: event.detail.value }); },
  toggleTag(event) {
    const id = Number(event.currentTarget.dataset.id);
    const tags = this.data.tags.map(tag => tag.id === id ? { ...tag, selected: !tag.selected } : tag);
    if (tags.filter(tag => tag.selected).length > 3) return;
    this.setData({ tags });
  },
  adjustScore(event) {
    const field = event.currentTarget.dataset.field;
    const delta = Number(event.currentTarget.dataset.delta);
    this.setData({ [field]: Math.min(5, Math.max(1, this.data[field] + delta)) });
  },
  async save() {
    if (this.data.saving) return;
    if (!this.data.body.trim()) {
      this.setData({ error: "请填写评价内容" });
      return;
    }
    this.setData({ saving: true, error: "", message: "" });
    try {
      requireText(this.data.body, "评价内容", 2000);
      const selectedImages = await Promise.all(this.data.selectedImages.map(async item => ({
        ...item, uploadId: item.uploadId || (await uploadFile(item.path)).id,
      })));
      this.setData({ selectedImages });
      const review = await request(this.data.review ? `/mini/reviews/${this.data.review.id}`
        : `/mini/bookings/${this.data.bookingId}/review`, {
        method: this.data.review ? "PUT" : "POST",
        data: {
          body: this.data.body.trim(), overallScore: this.data.overallScore,
          courtScore: this.data.courtScore, environmentScore: this.data.environmentScore,
          serviceScore: this.data.serviceScore, anonymous: this.data.anonymous,
          tagIds: this.data.tags.filter(tag => tag.selected).map(tag => tag.id),
          uploadIds: selectedImages.length ? selectedImages.map(item => item.uploadId)
            : this.data.imagesCleared ? [] : undefined,
        },
      });
      this.setData({ review, selectedImages: [], imagesCleared: false,
        message: review.moderationStatus === "PENDING" ? "评价已提交，审核通过后公开" : "评价已发布" });
    } catch (error) {
      this.setData({ error: error.message });
      if (error.message === "请先接受当前社区规则与隐私说明") {
        wx.navigateTo({ url: "/pages/content-agreement/index" });
      }
    } finally { this.setData({ saving: false }); }
  },
  async remove() {
    if (!this.data.review || this.data.saving) return;
    const confirmed = await new Promise(resolve => wx.showModal({
      title: "删除评价", content: "删除后不能对这笔预约重新评价。", confirmText: "删除",
      success: result => resolve(result.confirm),
    }));
    if (!confirmed) return;
    this.setData({ saving: true, error: "" });
    try {
      await request(`/mini/reviews/${this.data.review.id}`, { method: "DELETE" });
      wx.navigateBack();
    } catch (error) { this.setData({ error: error.message, saving: false }); }
  },
});
