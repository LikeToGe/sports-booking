const { request, time } = require('../../utils/api');

Page({
  data: { bookingId: 0, date: '', booking: null, loading: true, error: '' },
  async onLoad(options) {
    this.setData({ bookingId: Number(options.bookingId) });
    await this.load();
  },
  async load() {
    this.setData({ loading: true, error: '', booking: null });
    try {
      // 原详情接口校验本人归属；只使用场馆、运动、场地和开始时间偏好。
      const detail = await request(`/mini/bookings/${this.data.bookingId}`);
      this.setData({ booking: detail.booking });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  selectDate(event) { this.setData({ date: event.detail.value }); },
  viewSlots() {
    if (!this.data.date || !this.data.booking || this.data.loading || this.data.error) return;
    const booking = this.data.booking;
    const params = [`id=${booking.venueId}`, `sportTypeId=${booking.sportTypeId}`,
      `courtId=${booking.courtId}`, `date=${this.data.date}`,
      `preferredStartTime=${encodeURIComponent(time(booking.startAt))}`, 'entryType=BOOK_AGAIN'];
    // 复用当前场次页面，原偏好不可订时同页展示可主动选择的替代场次。
    wx.navigateTo({ url: `/pages/venue/index?${params.join('&')}` });
  },
});
