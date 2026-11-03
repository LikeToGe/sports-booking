const entries = [
  { route: "pages/home/index",label: "订场",icon: "courts" },
  { route: "pages/posts/index",label: "动态",icon: "community",ugc: true },
  { route: "pages/bookings/index",label: "预约",icon: "calendar" },
  { route: "pages/membership/index",label: "会员",icon: "membership" },
  { route: "pages/mine/index",label: "我的",icon: "user" },
];
Component({
  data: { entries: entries.filter(item => !item.ugc),route: "", transitionVisible: false, expanded: false, fading: false, circleStyle: "" },
  lifetimes: {
    attached() { this.refresh(); },
    detached() { this.clearTimers(); },
  },
  pageLifetimes: {
    show() { this.refresh(); },
    hide() {
      clearTimeout(this.expandTimer);
      clearTimeout(this.revealTimer);
      const transition = getApp().globalData.tabTransition;
      if (transition && (transition.phase === "expanding" || transition.phase === "revealing")) {
        clearTimeout(transition.watchdog);
        getApp().globalData.tabTransition = null;
      }
      this.setData({ transitionVisible: false });
    },
  },
  methods: {
    async refresh() {
      const pages = getCurrentPages();
      this.setData({ route: pages[pages.length - 1]?.route || "" });
      this.reveal();
      try {
        const enabled = await require("../utils/api").request("/public/ugc-config");
        getApp().globalData.ugcEnabled = Boolean(enabled);
        this.setData({ entries: entries.filter(item => !item.ugc || enabled) });
      } catch (_) { this.setData({ entries: entries.filter(item => !item.ugc) }); }
    },
    clearTimers() {
      clearTimeout(this.expandTimer);
      clearTimeout(this.revealTimer);
      clearTimeout(this.watchdog);
    },
    change(event) {
      const app = getApp();
      const target = event.currentTarget.dataset.route;
      if (app.globalData.tabTransition || target === this.data.route || !this.data.entries.some(item => item.route === target)) return;
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      const touch = event.changedTouches?.[0] || event.touches?.[0];
      const index = this.data.entries.findIndex(item => item.route === target);
      const x = Math.max(0, Math.min(info.windowWidth, touch?.clientX ?? event.detail?.x ?? info.windowWidth * (index + .5) / this.data.entries.length));
      const y = Math.max(0, Math.min(info.windowHeight, touch?.clientY ?? event.detail?.y ?? info.windowHeight - 32));
      const radius = Math.ceil(Math.hypot(Math.max(x, info.windowWidth - x), Math.max(y, info.windowHeight - y)));
      const transition = { target, circleStyle: `left:${x - radius}px;top:${y - radius}px;width:${radius * 2}px;height:${radius * 2}px;`, phase: "expanding" };
      app.globalData.tabTransition = transition;
      const fail = () => {
        if (app.globalData.tabTransition !== transition) return;
        app.globalData.tabTransition = null;
        this.clearTimers();
        this.setData({ transitionVisible: false, expanded: false, fading: false });
        app.showOperationFeedback(false, "页面切换未完成，请重试");
      };
      this.watchdog = transition.watchdog = setTimeout(fail, 2500);
      this.setData({ transitionVisible: true, expanded: false, fading: false, circleStyle: transition.circleStyle }, () => {
        // 先绘制触点圆，再启动扩散，切页时由目标 Tab 接住同一层遮罩。
        wx.nextTick(() => {
          if (app.globalData.tabTransition !== transition) return;
          this.setData({ expanded: true });
          this.expandTimer = setTimeout(() => {
            transition.phase = "switching";
            wx.switchTab({ url: "/" + target, fail, success: () => {
              const pages = getCurrentPages();
              const tabBar = pages[pages.length - 1]?.getTabBar?.();
              if (tabBar) {
                tabBar.setData({ route: pages[pages.length - 1].route });
                tabBar.reveal();
              } else fail();
            } });
          }, 280);
        });
      });
    },
    reveal() {
      const app = getApp();
      const transition = app.globalData.tabTransition;
      if (!transition || transition.target !== this.data.route || transition.phase !== "switching") return;
      transition.phase = "revealing";
      this.setData({ transitionVisible: true, expanded: true, fading: false, circleStyle: transition.circleStyle }, () => {
        wx.nextTick(() => {
          if (app.globalData.tabTransition !== transition) return;
          this.setData({ fading: true });
          this.revealTimer = setTimeout(() => {
            this.setData({ transitionVisible: false });
            if (app.globalData.tabTransition === transition) {
              clearTimeout(transition.watchdog);
              app.globalData.tabTransition = null;
            }
          }, 160);
        });
      });
    },
    stopTouch() {},
  },
});
