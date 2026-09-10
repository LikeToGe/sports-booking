const { assetUrl, request, dateInZhengzhou, money, time } = require("../../utils/api");

Page({
  data: { id: 0, date: "", sportTypeId: null, venue: null, sports: [], courts: [], groups: [], error: "", loading: true },

  async onLoad(options) {
    this.setData({ id: Number(options.id), date: options.date || dateInZhengzhou() });
    await this.load();
  },

  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const params = [`date=${this.data.date}`];
      if (this.data.sportTypeId) params.push(`sportTypeId=${this.data.sportTypeId}`);
      const detail = await request(`/mini/venues/${this.data.id}?${params.join("&")}`);
      const groups = detail.courts.map(court => ({
        ...court,
        imagePath: assetUrl(court.imagePath),
        slots: detail.slots.filter(slot => slot.courtId === court.id).map(slot => ({ ...slot, time: `${time(slot.startAt)}–${time(slot.endAt)}`, price: money(slot.priceFen) })),
      })).filter(court => court.slots.length);
      this.setData({ venue: { ...detail.venue, coverPath: assetUrl(detail.venue.coverPath) }, sports: detail.sports, courts: detail.courts, groups });
      wx.setNavigationBarTitle({ title: detail.venue.name });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },

  async selectSport(event) { this.setData({ sportTypeId: Number(event.currentTarget.dataset.id) || null }); await this.load(); },
  async selectDate(event) { this.setData({ date: event.detail.value }); await this.load(); },
  openMap() {
    const venue = this.data.venue;
    if (!venue?.latitude || !venue?.longitude) return;
    wx.openLocation({ latitude: Number(venue.latitude), longitude: Number(venue.longitude), name: venue.name, address: venue.address });
  },
  book(event) {
    const slot = this.data.groups.flatMap(group => group.slots).find(item => item.id === Number(event.currentTarget.dataset.id));
    wx.setStorageSync("yuechangPendingSlot", { ...slot, venueName: this.data.venue.name });
    wx.navigateTo({ url: "/pages/confirm/index" });
  },
});
