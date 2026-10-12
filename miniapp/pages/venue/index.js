const { assetUrl, request, dateInZhengzhou, money, time } = require("../../utils/api");

Page({
  data: { id: 0, date: "", sportTypeId: null, startTime: "", endTime: "", minPriceFen: "", maxPriceFen: "", entryType: null, venue: null, sports: [], courts: [], groups: [], reviewSummary: { reviewCount: 0 }, reviews: [], ugcEnabled: false, error: "", loading: true },

  async onLoad(options) {
    this.setData({ id: Number(options.id), date: options.date || dateInZhengzhou(),
      sportTypeId: Number(options.sportTypeId) || null, startTime: options.startTime || "", endTime: options.endTime || "",
      minPriceFen: options.minPriceFen || "", maxPriceFen: options.maxPriceFen || "", entryType: options.entryType || null });
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
      const [detail, ugcEnabled] = await Promise.all([
        request(`/mini/venues/${this.data.id}?${params.join("&")}`),
        request("/public/ugc-config").catch(() => false),
      ]);
      if (version !== this.loadVersion) return;
      const groups = detail.courts.map(court => ({
        ...court,
        imagePath: assetUrl(court.imagePath),
        slots: detail.slots.filter(slot => slot.courtId === court.id).map(slot => ({ ...slot, time: `${time(slot.startAt)} - ${time(slot.endAt)}`, price: `¥${(slot.priceFen / 100).toFixed(2)}` })),
      })).filter(court => court.slots.length);
      this.setData({ venue: { ...detail.venue, coverPath: assetUrl(detail.venue.coverPath) }, sports: detail.sports, courts: detail.courts, groups, ugcEnabled });
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
  openMap() {
    const venue = this.data.venue;
    if (!venue?.latitude || !venue?.longitude) return;
    wx.openLocation({ latitude: Number(venue.latitude), longitude: Number(venue.longitude), name: venue.name, address: venue.address });
  },
  book(event) {
    if (this.data.loading || this.data.error) return;
    const slot = this.data.groups.flatMap(group => group.slots).find(item => item.id === Number(event.currentTarget.dataset.id));
    if (!slot) return;
    wx.setStorageSync("yuechangPendingSlot", { ...slot, venueName: this.data.venue.name, entryType: this.data.entryType });
    wx.navigateTo({ url: "/pages/confirm/index" });
  },
  openReviews() { wx.navigateTo({ url: `/pages/venue-reviews/index?venueId=${this.data.id}` }); },
});
