Component({
  properties: {
    busy: Boolean,
    done: Boolean,
    label: String,
    busyLabel: { type: String, value: "处理中" },
    doneLabel: { type: String, value: "已完成" },
  },
  observers: {
    "done, busy"() { this.notifyComplete(); },
  },
  methods: {
    notifyComplete() {
      clearTimeout(this.timer);
      if (this.data.done && !this.data.busy && !this.hidden) {
        // 先让用户看清成功反馈，再通知页面执行跳转或收起表单。
        this.timer = setTimeout(() => this.triggerEvent("complete"), 420);
      }
    },
  },
  lifetimes: { detached() { this.hidden = true; clearTimeout(this.timer); } },
  pageLifetimes: { hide() { this.hidden = true; clearTimeout(this.timer); }, show() { this.hidden = false; this.notifyComplete(); } },
});
