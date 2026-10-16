const { request, dateInZhengzhou, money, time, assetUrl } = require("../../utils/api");

// 按郑州日历日期生成快捷入口，UTC 计算只用于避免设备时区改变日期。
function upcomingDates(today) {
  const start = new Date(`${today}T00:00:00Z`).getTime();
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(start + index * 86400000);
    return { date: day.toISOString().slice(0, 10), day: day.getUTCDate(), label: index === 0 ? "今天" : `周${"日一二三四五六"[day.getUTCDay()]}` };
  });
}

Page({
  onReady() { this.getTabBar?.()?.refresh(); },
  data: {
    sports: [],
    dateOptions: upcomingDates(dateInZhengzhou()),
    districtOptions: ["全部区县", "金水区", "二七区", "中原区", "管城回族区", "郑东新区", "高新区"], districtIndex: 0,
    venues: [],
    slots: [],
    startTime: "", endTime: "", district: "", districtIndex: 0, minPrice: "", maxPrice: "",
    searchMode: false, filtersDirty: false, page: 1, hasMore: false,
    selectedSportId: null,
    date: dateInZhengzhou(),
    keyword: "",
    dateLabel: dateInZhengzhou().slice(5),
    loading: true,
    showFilters: false,
    error: "",
  },

  onShow() { this.getTabBar?.()?.refresh(); },

  async onLoad() {
    await this.load();
  },

  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },

  async load() {
    this.setData({ dateOptions: upcomingDates(dateInZhengzhou()) });
    try { this.setData({ sports: await request("/mini/sports") }); }
    catch (error) { this.setData({ error: error.message, loading: false }); return; }
    await this.search();
  },

  filterParams() {
    const params = [`date=${this.data.date}`];
    if (this.data.selectedSportId) params.push(`sportTypeId=${this.data.selectedSportId}`);
    if (this.data.keyword) params.push(`keyword=${encodeURIComponent(this.data.keyword)}`);
    if (this.data.district) params.push(`district=${encodeURIComponent(this.data.district)}`);
    if (this.data.startTime || this.data.endTime) {
      if (!this.data.startTime || !this.data.endTime || this.data.startTime >= this.data.endTime) throw new Error("请选择同一天递增的起止时间");
      params.push(`startTime=${encodeURIComponent(this.data.startTime)}`, `endTime=${encodeURIComponent(this.data.endTime)}`);
    }
    const prices = [this.data.minPrice, this.data.maxPrice].map(value => {
      if (value === "") return null;
      if (!/^\d+(\.\d{1,2})?$/.test(value)) throw new Error("价格须为非负金额，最多两位小数");
      const [yuan, fraction = ""] = value.split(".");
      const fen = Number(yuan) * 100 + Number(fraction.padEnd(2, "0"));
      if (!Number.isSafeInteger(fen) || fen > 2147483647) throw new Error("价格超出允许范围");
      return fen;
    });
    if (prices[0] != null && prices[1] != null && prices[0] > prices[1]) throw new Error("最低价格不能高于最高价格");
    if (prices[0] != null) params.push(`minPriceFen=${prices[0]}`);
    if (prices[1] != null) params.push(`maxPriceFen=${prices[1]}`);
    return params;
  },

  toggleFilters() { this.setData({ showFilters: !this.data.showFilters }); },

  onFilter(event) {
    this.setData({ [event.currentTarget.dataset.field]: event.detail.value, venues: [], slots: [], hasMore: false, loading: false, filtersDirty: true });
    // 条件编辑使旧请求失效，结果只能来自一次明确提交的检索。
    this.searchVersion = (this.searchVersion || 0) + 1;
  },

  async selectDistrict(event) {
    const index = Number(event.detail.value) || 0;
    this.setData({ districtIndex: index, district: index ? this.data.districtOptions[index] : "" });
    await this.search();
  },

  async clearFilters() {
    this.setData({ startTime: "", endTime: "", district: "", districtIndex: 0, minPrice: "", maxPrice: "" });
    await this.search();
  },

  onKeyword(event) { this.onFilter({ ...event, currentTarget: { dataset: { field: "keyword" } } }); },

  async search(event, append = false) {
    const version = this.searchVersion = (this.searchVersion || 0) + 1;
    this.setData({ loading: true, error: "" });
    try {
      const params = this.filterParams();
      const searchMode = !!(this.data.startTime || this.data.endTime || this.data.minPrice || this.data.maxPrice);
      const page = append ? this.data.page + 1 : 1;
      if (searchMode) {
        const result = await request(`/mini/slots/search?${params.join("&")}&page=${page}`);
        if (version !== this.searchVersion) return;
        const slots = result.map(slot => ({ ...slot, price: `¥${(slot.priceFen / 100).toFixed(2)}`, time: `${time(slot.startAt)} - ${time(slot.endAt)}` }));
        this.setData({ searchMode, filtersDirty: false, venues: [], slots: append ? this.data.slots.concat(slots) : slots, page, hasMore: result.length === 20 });
      } else {
        const result = await request(`/mini/venues?${params.join("&")}`);
        if (version !== this.searchVersion) return;
        const venues = result.map((venue, index) => ({ ...venue,
          cover: assetUrl(venue.coverPath) || `/assets/venues/venue-${(index % 3) + 1}.jpg`,
          price: venue.startingPriceFen == null ? "暂无场次" : `${money(venue.startingPriceFen)} 起`,
          nextTime: venue.nextAvailableAt ? `${time(venue.nextAvailableAt)} 可约` : "当天暂无空位",
        }));
        this.setData({ searchMode, filtersDirty: false, venues, slots: [], page: 1, hasMore: false });
      }
    } catch (error) {
      if (version === this.searchVersion) this.setData({ error: error.message, venues: append ? this.data.venues : [], slots: append ? this.data.slots : [], hasMore: false });
    } finally {
      if (version === this.searchVersion) this.setData({ loading: false });
    }
  },

  async loadMore() { if (!this.data.loading && this.data.hasMore) await this.search(null, true); },

  async selectSport(event) {
    const id = Number(event.currentTarget.dataset.id) || null;
    this.setData({ selectedSportId: id });
    await this.search();
  },

  async selectDate(event) {
    const date = event.detail.value || event.currentTarget.dataset.date;
    this.setData({ date, dateLabel: date.slice(5) });
    await this.search();
  },

  openVenue(event) {
    if (this.data.loading) return;
    const params = this.filterParams();
    wx.navigateTo({ url: `/pages/venue/index?id=${event.currentTarget.dataset.id}&${params.join("&")}${this.data.searchMode ? "&entryType=TIME_SEARCH" : ""}` });
  },
});
