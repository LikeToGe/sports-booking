const { request, money, time } = require("../../utils/api");

const labels = { PENDING_PAYMENT: "待支付", CONFIRMED: "待核销", COMPLETED: "已完成", CANCELLED: "已取消", NO_SHOW: "未到场" };

Page({
  data: { bookings: [], loading: true, error: "" },
  async onShow() { await this.load(); },
  async onPullDownRefresh() { await this.load(); wx.stopPullDownRefresh(); },
  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const bookings = await request("/mini/bookings/list");
      this.setData({ bookings: bookings.map(item => ({
        ...item,
        date: item.startAt.slice(0, 10),
        time: `${time(item.startAt)}–${time(item.endAt)}`,
        price: money(item.amountFen),
        statusLabel: labels[item.status] || item.status,
        statusTone: ["CONFIRMED", "PENDING_PAYMENT"].includes(item.status) ? "warning" : item.status === "COMPLETED" ? "active" : "danger",
      })) });
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ loading: false }); }
  },
  open(event) { wx.navigateTo({ url: `/pages/booking/index?id=${event.currentTarget.dataset.id}` }); },
});
