const { request } = require("../../utils/api");

Page({
  data: { venues: [], selectedIndex: 0, current: null, records: [], error: "", loading: true, requestedVenueId: 0 },
  onLoad(options) { this.setData({ requestedVenueId: Number(options.venueId || 0) }); },
  async onShow() {
    this.setData({ loading: true, error: "" });
    try {
      const venues = (await request("/staff/venues")).filter(item => item.permissions.includes("BOOKING_VERIFY"));
      const venueId = this.data.requestedVenueId || wx.getStorageSync("yuechangStaffVenueId");
      const selectedIndex = Math.max(0, venues.findIndex(item => item.venueId === venueId));
      this.setData({ venues, selectedIndex, current: venues[selectedIndex] || null });
      await this.loadRecords();
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  async selectVenue(event) {
    const selectedIndex = Number(event.detail.value);
    const current = this.data.venues[selectedIndex];
    wx.setStorageSync("yuechangStaffVenueId", current.venueId);
    this.setData({ selectedIndex, current, loading: true, error: "" });
    try { await this.loadRecords(); }
    catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  async loadRecords() {
    if (!this.data.current) { this.setData({ records: [] }); return; }
    const records = await request(`/staff/verifications/records?venueId=${this.data.current.venueId}`);
    this.setData({ records: records.map(item => ({ ...item, time: new Date(item.verifiedAt).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai", hour12: false }) })) });
  },
});
