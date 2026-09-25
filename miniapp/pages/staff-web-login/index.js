const { request } = require("../../utils/api");

Page({
  data: { venues: [], selectedIndex: 0, current: null, ticket: "", error: "", loading: true, confirmed: false },
  async onLoad() {
    try {
      const venues = await request("/staff/venues");
      const remembered = wx.getStorageSync("yuechangStaffVenueId");
      const selectedIndex = Math.max(0, venues.findIndex(item => item.venueId === remembered));
      this.applyVenue(venues, selectedIndex);
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  applyVenue(venues, selectedIndex) {
    const current = venues[selectedIndex] || null;
    if (current) wx.setStorageSync("yuechangStaffVenueId", current.venueId);
    this.setData({ venues, selectedIndex, current, confirmed: false, error: "" });
  },
  selectVenue(event) { this.applyVenue(this.data.venues, Number(event.detail.value)); },
  ticketFromContent(value) {
    const content = String(value || "").trim();
    const matched = content.match(/[?&]ticket=([^&#]+)/);
    return matched ? decodeURIComponent(matched[1]) : content;
  },
  onTicket(event) { this.setData({ ticket: this.ticketFromContent(event.detail.value), confirmed: false, error: "" }); },
  scan() {
    wx.scanCode({
      onlyFromCamera: true,
      scanType: ["qrCode"],
      success: result => this.setData({ ticket: this.ticketFromContent(result.result), confirmed: false, error: "" }),
      fail: reason => { if (!String(reason.errMsg).includes("cancel")) this.setData({ error: "无法读取登录二维码" }); },
    });
  },
  async confirm() {
    if (!this.data.current || !this.data.ticket) return;
    this.setData({ loading: true, error: "" });
    try {
      await request(`/staff/web-login-tickets/${encodeURIComponent(this.data.ticket)}/confirm`, {
        method: "POST", data: { venueId: this.data.current.venueId },
      });
      this.setData({ confirmed: true });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
});
