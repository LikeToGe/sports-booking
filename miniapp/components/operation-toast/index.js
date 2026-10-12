Component({
  data: { visible: false, tone: "success", message: "", version: 0 },
  lifetimes: {
    attached() {
      this.timer = setInterval(() => {
        const feedback = getApp().globalData.operationFeedback;
        if (!feedback || (feedback.version === this.data.version && feedback.visible === this.data.visible)) return;
        this.setData({ visible: feedback.visible, tone: feedback.tone, message: feedback.message, version: feedback.version });
      }, 100);
    },
    detached() {
      if (this.timer) clearInterval(this.timer);
    },
  },
});
