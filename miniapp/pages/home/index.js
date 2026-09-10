const { request, dateInZhengzhou, money, time, assetUrl } = require("../../utils/api");

Page({
  data: {
    sports: [],
    venues: [],
    selectedSportId: null,
    date: dateInZhengzhou(),
    keyword: "",
    dateLabel: dateInZhengzhou().slice(5),
    loading: true,
    error: "",
  },

  async onLoad() {
    await this.load();
  },

  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },

  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const [sports, venues] = await Promise.all([request("/mini/sports"), this.fetchVenues()]);
      this.setData({ sports, venues });
    } catch (error) {
      this.setData({ error: error.message });
    } finally {
      this.setData({ loading: false });
    }
  },

  async fetchVenues() {
    const params = [`date=${this.data.date}`];
    if (this.data.selectedSportId) params.push(`sportTypeId=${this.data.selectedSportId}`);
    if (this.data.keyword) params.push(`keyword=${encodeURIComponent(this.data.keyword)}`);
    const venues = await request(`/mini/venues?${params.join("&")}`);
    return venues.map((venue, index) => ({
      ...venue,
      cover: assetUrl(venue.coverPath) || `/assets/venues/venue-${(index % 3) + 1}.jpg`,
      price: venue.startingPriceFen == null ? "暂无场次" : `${money(venue.startingPriceFen)} 起`,
      nextTime: venue.nextAvailableAt ? `${time(venue.nextAvailableAt)} 可约` : "当天暂无空位",
    }));
  },

  onKeyword(event) { this.setData({ keyword: event.detail.value }); },

  async search() {
    try { this.setData({ venues: await this.fetchVenues(), error: "" }); }
    catch (error) { this.setData({ error: error.message }); }
  },

  async selectSport(event) {
    const id = Number(event.currentTarget.dataset.id) || null;
    this.setData({ selectedSportId: id });
    await this.search();
  },

  async selectDate(event) {
    this.setData({ date: event.detail.value, dateLabel: event.detail.value.slice(5) });
    await this.search();
  },

  openVenue(event) {
    wx.navigateTo({ url: `/pages/venue/index?id=${event.currentTarget.dataset.id}&date=${this.data.date}` });
  },
});
