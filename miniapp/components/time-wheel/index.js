Component({
  properties: {
    value: String,
    label: { type: String, value: "选择时间" },
    placeholder: { type: String, value: "不限" },
    disabled: { type: Boolean, observer(disabled) { if (disabled) this.cancel(); } },
  },
  data: {
    opened: false, spinning: false, draft: [0, 0],
    hours: Array.from({ length: 24 }, (_, index) => String(index).padStart(2, "0")),
    minutes: Array.from({ length: 60 }, (_, index) => String(index).padStart(2, "0")),
  },
  pageLifetimes: { hide() { this.cancel(); } },
  methods: {
    open() {
      if (this.data.disabled) return;
      const matched = /^(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/.exec(this.data.value);
      const draft = matched && Number(matched[1]) < 24 && Number(matched[2]) < 60
        ? [Number(matched[1]), Number(matched[2])] : [19, 0];
      this.setData({ opened: true, spinning: false, draft });
    },
    start() { this.setData({ spinning: true }); },
    end() { wx.nextTick(() => this.setData({ spinning: false })); },
    change(event) {
      const [hour, minute] = event.detail.value;
      if (!Number.isInteger(hour) || hour < 0 || hour > 23 || !Number.isInteger(minute) || minute < 0 || minute > 59) return;
      this.setData({ draft: [hour, minute], spinning: false });
    },
    confirm() {
      if (!this.data.opened || this.data.spinning || this.data.disabled) return;
      const value = this.data.draft.map(part => String(part).padStart(2, "0")).join(":");
      this.setData({ opened: false });
      this.triggerEvent("change", { value });
    },
    cancel() { this.setData({ opened: false, spinning: false }); },
    stopScroll() {},
  },
});
