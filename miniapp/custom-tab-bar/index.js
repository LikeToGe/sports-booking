const entries = [
  { route: "pages/home/index",label: "订场",icon: "courts" },
  { route: "pages/posts/index",label: "动态",icon: "community",ugc: true },
  { route: "pages/bookings/index",label: "预约",icon: "calendar" },
  { route: "pages/membership/index",label: "会员",icon: "membership" },
  { route: "pages/mine/index",label: "我的",icon: "user" },
];
Component({
  data: { entries: entries.filter(item => !item.ugc),route: "" },
  lifetimes: { attached() { this.refresh(); } },
  pageLifetimes: { show() { this.refresh(); } },
  methods: {
    async refresh() {
      const pages = getCurrentPages();
      this.setData({ route: pages[pages.length - 1]?.route || "" });
      try {
        const enabled = await require("../utils/api").request("/public/ugc-config");
        getApp().globalData.ugcEnabled = Boolean(enabled);
        this.setData({ entries: entries.filter(item => !item.ugc || enabled) });
      } catch (_) { this.setData({ entries: entries.filter(item => !item.ugc) }); }
    },
    change(event) { wx.switchTab({ url: "/" + event.currentTarget.dataset.route }); },
  },
});
