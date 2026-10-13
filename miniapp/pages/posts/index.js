const { request, uploadFile, dateTime, time } = require("../../utils/api");

Page({
  data: { posts: [], recruitments: [], mode: "posts", body: "", sportTypeId: null, sportIndex: 0, venueId: null, venueIndex: 0,
    sports: [], venues: [], bookingId: null, bookingIndex: 0, bookings: [], filterSports: [], filterVenues: [],
    filterSportIndex: 0, filterVenueIndex: 0, filterSportId: null, filterVenueId: null,
    loading: true, saving: false, enabled: false, editingId: null, selectedMedia: [], existingMediaCount: 0,
    recruitmentDate: "", recruitmentLevel: "", recruitmentLevelLabel: "不限水平", recruitmentLevelIndex: 0, recruitmentMinRemaining: "",
    mediaCleared: false, error: "", message: "", showEditor: false },

  onLoad() { this.ready = this.loadOptions(); },
  async loadOptions() {
    try {
      const [sports, venues, bookings] = await Promise.all([
        request("/mini/sports"),request("/mini/venues"),request("/mini/bookings/list"),
      ]);
      this.setData({ sports, venues: [{ id: null, name: "不关联场馆" }, ...venues],
        filterSports: [{ id: null, name: "全部运动" }, ...sports],
        filterVenues: [{ id: null, name: "全部场馆" }, ...venues],
        bookings: [{ id: null, label: "不绑定预约" }, ...bookings.filter(item => item.status === "CONFIRMED")
          .map(item => ({ ...item,label: `${item.venueName} ${dateTime(item.startAt)}` }))],
        sportTypeId: sports[0]?.id || null,sportIndex: 0 });
    } catch (error) { this.setData({ error: error.message }); }
  },

  async onShow() {
    if (this.ready) await this.ready;
    const editId = getApp().globalData.editPostId;
    if (editId) {
      getApp().globalData.editPostId = null;
      try {
        const posts = await request("/mini/posts/mine");
        const post = posts.find(item => item.id === editId);
        if (post) this.applyEdit(post);
      } catch (error) { this.setData({ error: error.message }); }
    }
    await this.load();
    if (this.data.enabled && this.data.mode === "recruitments") await this.loadRecruitments();
  },

  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const params = [];
      if (this.data.filterSportId) params.push(`sportTypeId=${this.data.filterSportId}`);
      if (this.data.filterVenueId) params.push(`venueId=${this.data.filterVenueId}`);
      this.setData({ posts: await request(`/mini/posts/list${params.length ? `?${params.join("&")}` : ""}`), enabled: true });
    } catch (error) { this.setData({ error: error.message, posts: [], enabled: !/暂未开放/.test(error.message) }); }
    finally { this.setData({ loading: false }); }
  },
  switchMode(event) {
    const mode = event.currentTarget.dataset.mode;
    if (mode !== "recruitments") this.recruitmentRequest = (this.recruitmentRequest || 0) + 1;
    this.setData({ mode });
    if (mode === "recruitments") this.loadRecruitments();
  },
  async loadRecruitments() {
    const requestId = this.recruitmentRequest = (this.recruitmentRequest || 0) + 1;
    this.setData({ loading: true, error: "" });
    try {
      const params = [];
      if (this.data.recruitmentDate) params.push(`date=${this.data.recruitmentDate}`);
      if (this.data.filterSportId) params.push(`sportTypeId=${this.data.filterSportId}`);
      if (this.data.filterVenueId) params.push(`venueId=${this.data.filterVenueId}`);
      if (this.data.recruitmentLevel) params.push(`levelTag=${encodeURIComponent(this.data.recruitmentLevel)}`);
      if (this.data.recruitmentMinRemaining) params.push(`minRemaining=${this.data.recruitmentMinRemaining}`);
      const rows = await request(`/mini/recruitments${params.length ? `?${params.join("&")}` : ""}`);
      const levels = { BEGINNER_FRIENDLY: "新手友好", CASUAL: "休闲", ADVANCED: "进阶" };
      if (requestId !== this.recruitmentRequest) return;
      this.setData({ recruitments: rows.map(item => ({ ...item,
        startLabel: dateTime(item.startAt), endLabel: time(item.endAt),
        levelLabel: item.levelTag ? levels[item.levelTag] : `其他说明：${item.level || "历史水平"}`,
        feeLabel: item.feeMode === "FREE" ? "免费（平台不代收）" : `预计 AA ¥${(item.aaAmountFen / 100).toFixed(2)}（平台不代收）`,
      })) });
    } catch (error) { if (requestId === this.recruitmentRequest) this.setData({ error: error.message, recruitments: [] }); }
    finally { if (requestId === this.recruitmentRequest) this.setData({ loading: false }); }
  },
  retryLoad() { return this.data.mode === "recruitments" ? this.loadRecruitments() : this.load(); },
  onRecruitmentFilter(event) {
    const field = event.currentTarget.dataset.field;
    const value = field === "recruitmentLevel"
      ? ["", "BEGINNER_FRIENDLY", "CASUAL", "ADVANCED"][Number(event.detail.value)] || ""
      : event.detail.value;
    this.setData({ [field]: value, ...(field === "recruitmentLevel" ? {
      recruitmentLevelIndex: Number(event.detail.value),
      recruitmentLevelLabel: ["不限水平", "新手友好", "休闲", "进阶"][Number(event.detail.value)] || "不限水平",
    } : {}) });
    this.loadRecruitments();
  },
  openRecruitment(event) {
    const postId = Number(event.currentTarget.dataset.postId);
    request(`/mini/content/events?eventType=RECRUITMENT_DISCOVERY_OPEN&targetType=POST&targetId=${postId}`,
      { method: "POST" }).catch(() => {});
    wx.navigateTo({ url: `/pages/posts/detail?id=${postId}` });
  },

  toggleEditor() { this.setData({ showEditor: !this.data.showEditor }); },
  editBody(event) { this.setData({ body: event.detail.value }); },
  async chooseMedia() {
    try {
      const result = await new Promise((resolve, reject) => wx.chooseMedia({
        count: 3, mediaType: ["image", "video"], sourceType: ["album", "camera"], maxDuration: 20,
        success: resolve, fail: reject,
      }));
      const files = result.tempFiles || [];
      const videos = files.filter(file => file.fileType === "video");
      if (videos.length && files.length !== 1 || files.some(file => file.fileType === "video"
        ? file.size > 30 * 1024 * 1024 || file.duration > 20
        : file.size > 5 * 1024 * 1024)) {
        this.setData({ error: "最多 3 张图片或 1 个 20 秒内的视频" });
        return;
      }
      this.setData({ selectedMedia: files.map(file => ({
        path: file.tempFilePath, type: file.fileType, size: file.size,
        duration: file.fileType === "video" ? Math.ceil(file.duration) : null,
      })), existingMediaCount: 0, mediaCleared: true, error: "" });
    } catch (_) { }
  },
  clearMedia() { this.setData({ selectedMedia: [], existingMediaCount: 0, mediaCleared: true }); },
  selectSport(event) {
    const sportIndex = Number(event.detail.value);
    this.setData({ sportIndex, sportTypeId: this.data.sports[sportIndex]?.id || null });
  },
  selectVenue(event) {
    const venueIndex = Number(event.detail.value);
    this.setData({ venueIndex, venueId: this.data.venues[venueIndex]?.id || null });
  },
  async selectBooking(event) {
    const bookingIndex = Number(event.detail.value);
    const bookingId = this.data.bookings[bookingIndex]?.id || null;
    this.setData({ bookingIndex,bookingId });
    if (bookingId) {
      try {
        const { booking } = await request(`/mini/bookings/${bookingId}`);
        this.setData({ sportTypeId: booking.sportTypeId,venueId: booking.venueId,
          sportIndex: Math.max(0,this.data.sports.findIndex(item => item.id === booking.sportTypeId)),
          venueIndex: Math.max(0,this.data.venues.findIndex(item => item.id === booking.venueId)) });
      } catch (error) { this.setData({ error: error.message,bookingId: null,bookingIndex: 0 }); }
    }
  },
  selectFilterSport(event) {
    const filterSportIndex = Number(event.detail.value);
    this.setData({ filterSportIndex,filterSportId: this.data.filterSports[filterSportIndex]?.id || null });
    this.data.mode === "recruitments" ? this.loadRecruitments() : this.load();
  },
  selectFilterVenue(event) {
    const filterVenueIndex = Number(event.detail.value);
    this.setData({ filterVenueIndex,filterVenueId: this.data.filterVenues[filterVenueIndex]?.id || null });
    this.data.mode === "recruitments" ? this.loadRecruitments() : this.load();
  },

  openDetail(event) { wx.navigateTo({ url: `/pages/posts/detail?id=${event.currentTarget.dataset.id}` }); },
  edit(event) {
    const post = this.data.posts.find(item => item.id === Number(event.currentTarget.dataset.id));
    if (post) this.applyEdit(post);
  },
  applyEdit(post) {
    this.setData({ showEditor: true, editingId: post.id, body: post.body, sportTypeId: post.sportTypeId,venueId: post.venueId,
      bookingId: post.bookingId || null,bookingIndex: Math.max(0,this.data.bookings.findIndex(item => item.id === post.bookingId)),
      sportIndex: Math.max(0,this.data.sports.findIndex(item => item.id === post.sportTypeId)),
      venueIndex: Math.max(0,this.data.venues.findIndex(item => item.id === post.venueId)),
      selectedMedia: [], existingMediaCount: (post.media || []).length, mediaCleared: false,
      error: "", message: "" });
  },

  async publish() {
    if (this.data.saving || !this.data.body.trim()) {
      if (!this.data.body.trim()) this.setData({ error: "请填写动态内容" });
      return;
    }
    this.setData({ saving: true, error: "", message: "" });
    try {
      const selectedMedia = await Promise.all(this.data.selectedMedia.map(async item => ({
        ...item, uploadId: item.uploadId || (await uploadFile(item.path, item.duration)).id,
      })));
      this.setData({ selectedMedia });
      const editingId = this.data.editingId;
      const path = editingId ? `/mini/posts/${editingId}` : "/mini/posts/create";
      await request(path, { method: editingId ? "PUT" : "POST", data: {
        body: this.data.body.trim(), sportTypeId: this.data.sportTypeId,
        venueId: this.data.venueId,bookingId: this.data.bookingId,
        uploadIds: selectedMedia.length ? selectedMedia.map(item => item.uploadId)
          : this.data.mediaCleared ? [] : undefined,
      } });
      this.setData({ showEditor: false, body: "", editingId: null, selectedMedia: [], existingMediaCount: 0,
        bookingId: null,bookingIndex: 0,
        mediaCleared: false, message: editingId ? "动态已提交" : "动态已提交审核" });
      await this.load();
    } catch (error) {
      this.setData({ error: error.message });
      if (error.message === "请先接受当前社区规则与隐私说明") {
        wx.navigateTo({ url: "/pages/content-agreement/index" });
      }
    } finally { this.setData({ saving: false }); }
  },

  async remove(event) {
    const id = Number(event.currentTarget.dataset.id);
    const confirmed = await new Promise(resolve => wx.showModal({
      title: "删除动态", content: "删除后将不再公开显示。", confirmText: "删除",
      success: result => resolve(result.confirm),
    }));
    if (!confirmed) return;
    try { await request(`/mini/posts/${id}`, { method: "DELETE" }); await this.load(); }
    catch (error) { this.setData({ error: error.message }); }
  },
});
