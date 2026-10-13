const { assetUrl, request, dateInZhengzhou, time } = require("../../utils/api");

Page({
  data: { id: 0, date: "", sportTypeId: null, startTime: "", endTime: "", minPriceFen: "", maxPriceFen: "", entryType: null, venue: null, sports: [], courts: [], groups: [], matrixRows: [], matrixCourts: [], matrixWidth: 190, viewMode: "list", preferenceNotice: "", courtId: null, preferredStartTime: "", bookAgainId: null, district: "", reviewSummary: { reviewCount: 0 }, reviews: [], ugcEnabled: false, error: "", loading: true },

  async onLoad(options) {
    this.setData({ id: Number(options.id), date: options.date || dateInZhengzhou(),
      sportTypeId: Number(options.sportTypeId) || null, startTime: options.startTime || "", endTime: options.endTime || "",
      minPriceFen: options.minPriceFen || "", maxPriceFen: options.maxPriceFen || "", entryType: options.entryType || null, district: options.district || "",
      courtId: Number(options.courtId) || null, preferredStartTime: options.preferredStartTime || "",
      bookAgainId: Number(options.bookAgainId) || null });
    await this.load();
  },

  async load() {
    const version = this.loadVersion = (this.loadVersion || 0) + 1;
    this.setData({ loading: true, error: "" });
    try {
      const params = [`date=${this.data.date}`];
      if (this.data.sportTypeId) params.push(`sportTypeId=${this.data.sportTypeId}`);
      for (const field of ["startTime", "endTime", "minPriceFen", "maxPriceFen"]) {
        if (this.data[field] !== "") params.push(`${field}=${encodeURIComponent(this.data[field])}`);
      }
      const [detail, publicSlots, ugcEnabled] = await Promise.all([
        request(`/mini/venues/${this.data.id}?${params.join("&")}`),
        request(`/public/venues/${this.data.id}/slot-comparison?${params.join("&")}`),
        request("/public/ugc-config").catch(() => false),
      ]);
      if (version !== this.loadVersion) return;
      const statusLabels = { OPEN: "可预约", HELD: "暂时占位", BOOKED: "已预约", CLOSED: "不开放", UNAVAILABLE: "当前不可预约" };
      const slots = publicSlots.map(slot => ({ ...slot,
        time: `${time(slot.startAt)} - ${time(slot.endAt)}`, price: `¥${(slot.priceFen / 100).toFixed(2)}`,
        statusLabel: statusLabels[slot.status] || "当前不可预约",
        cancelText: slot.cancelDeadline ? `${slot.cancelDeadline.slice(5, 10)} ${time(slot.cancelDeadline)}` : "请查看预约规则",
        preferred: this.data.courtId === slot.courtId && (!this.data.preferredStartTime || time(slot.startAt) === this.data.preferredStartTime),
      }));
      const groups = detail.courts.map(court => ({ ...court, imagePath: assetUrl(court.imagePath),
        slots: slots.filter(slot => slot.courtId === court.id) })).filter(court => court.slots.length);
      const matrixCourts = groups.map(court => ({ id: court.id, name: court.name }));
      // 每行是一个真实起止区间；长短场次保留原长度，不能组合小时格购买。
      const intervals = [...new Map(slots.map(slot => [`${slot.startAt}/${slot.endAt}`, slot])).values()];
      const matrixRows = intervals.map(interval => ({ key: `${interval.startAt}/${interval.endAt}`, time: interval.time,
        cells: matrixCourts.map(court => ({ courtId: court.id, slots: slots.filter(slot => slot.courtId === court.id
          && slot.startAt === interval.startAt && slot.endAt === interval.endAt) })) }));
      const preferenceNotice = this.data.courtId && !slots.some(slot => slot.preferred && slot.status === "OPEN")
        ? "原场地或时段当前不可预约。可查看其他完整场次并主动选择，系统不会自动替您预约。" : "";
      this.setData({ venue: { ...detail.venue, coverPath: assetUrl(detail.venue.coverPath),
          districtLabel: detail.venue.district || "区县未设置" },
        sports: detail.sports, courts: detail.courts, groups, matrixCourts, matrixWidth: 190 + matrixCourts.length * 250, matrixRows, preferenceNotice, ugcEnabled });
      if (ugcEnabled) {
        const [reviewSummary, reviews] = await Promise.all([
          request(`/public/venues/${this.data.id}/review-summary`),
          request(`/public/venues/${this.data.id}/reviews`),
        ]);
        if (version !== this.loadVersion) return;
        this.setData({ reviewSummary, reviews: reviews.slice(0, 3) });
      }
      wx.setNavigationBarTitle({ title: detail.venue.name });
    } catch (error) { if (version === this.loadVersion) this.setData({ error: error.message }); }
    finally { if (version === this.loadVersion) this.setData({ loading: false }); }
  },

  async selectSport(event) { this.setData({ sportTypeId: Number(event.currentTarget.dataset.id) || null }); await this.load(); },
  async selectDate(event) { this.setData({ date: event.detail.value }); await this.load(); },
  async clearFilters() {
    this.setData({ startTime: "", endTime: "", minPriceFen: "", maxPriceFen: "" });
    await this.load();
  },
  selectView(event) { this.setData({ viewMode: event.currentTarget.dataset.mode }); },
  openFavourites() { wx.navigateTo({ url: `/pages/favourites/index?venueId=${this.data.id}` }); },
  openMap() {
    const venue = this.data.venue;
    if (!venue?.latitude || !venue?.longitude) return;
    wx.openLocation({ latitude: Number(venue.latitude), longitude: Number(venue.longitude), name: venue.name, address: venue.address });
  },
  book(event) {
    if (this.data.loading || this.data.error) return;
    const slot = this.data.groups.flatMap(group => group.slots).find(item => item.id === Number(event.currentTarget.dataset.id));
    if (!slot || slot.status !== "OPEN") return;
    wx.setStorageSync("yuechangPendingSlot", { ...slot, venueName: this.data.venue.name, entryNotice: this.data.venue.entryNotice, entryType: this.data.entryType, bookAgainId: this.data.bookAgainId });
    wx.navigateTo({ url: "/pages/confirm/index" });
  },
  openReviews() { wx.navigateTo({ url: `/pages/venue-reviews/index?venueId=${this.data.id}` }); },
});
