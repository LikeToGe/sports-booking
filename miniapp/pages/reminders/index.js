const { request, dateInZhengzhou, time } = require("../../utils/api");

const typeOptions = [{ value: "OPENING", label: "开售提醒" }, { value: "RELEASED", label: "空位提醒" }];
const statusLabels = { OPEN: "可预约", HELD: "支付占位", BOOKED: "已预约", CLOSED: "不开放", UNAVAILABLE: "当前不可预约" };
// 仅省略全零秒；遗留的非零秒保持原条件，由检索拒绝，不能静默扩大范围。
function minuteTime(value) { return value.replace(/^(\d{2}:\d{2}):00(?:\.0+)?$/, "$1"); }
function addDays(day, days) {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
function saleRule(day, days, minutes, targetDate) {
  if (!days || minutes == null) return "以场馆当前预约规则和实时供给为准。";
  const lastOpen = addDays(day, days - 1);
  const opening = targetDate > lastOpen ? `${targetDate} 预计 ${addDays(targetDate, 1 - days)} 进入预约窗口` : "目标日期已在预约窗口内";
  return `${opening}；开场前 ${minutes} 分钟截止，实际以查询时可售状态为准。`;
}

Page({
  data: {
    rows: [], sports: [], venues: [], typeOptions, typeIndex: 0, venueIndex: 0, sportIndex: 0,
    today: dateInZhengzhou(), dateMax: "", ruleText: "", targetDate: dateInZhengzhou(),
    startTime: "19:00", endTime: "21:00", focusId: 0, focusSlotId: 0,
    focus: null, supply: [], supplyNotice: "", loading: true, checking: false, saving: false,
    error: "", message: "",
  },
  onLoad(options) {
    this.setData({ focusId: Number(options.subscriptionId) || 0, focusSlotId: Number(options.slotId) || 0 });
  },
  onShow() { return this.load(); },
  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const [sports, venues, rows] = await Promise.all([
        request("/mini/sports"), request("/mini/venues"), request("/mini/reminder-subscriptions"),
      ]);
      const focus = rows.find(row => row.id === this.data.focusId) || null;
      const today = dateInZhengzhou();
      this.setData({ sports, venues, today, rows: rows.map(row => ({ ...row,
        typeLabel: row.type === "OPENING" ? "开售提醒" : "空位提醒",
        venueName: venues.find(venue => venue.id === row.venueId)?.name || `场馆 ${row.venueId}`,
        sportName: sports.find(sport => sport.id === row.sportTypeId)?.name || `运动 ${row.sportTypeId}`,
        ruleText: saleRule(today, row.bookingDaysAhead, row.minBookingMinutes, row.targetDate),
      })), focus });
      if (focus) {
        this.setData({
          venueIndex: venues.findIndex(venue => venue.id === focus.venueId),
          sportIndex: sports.findIndex(sport => sport.id === focus.sportTypeId),
          typeIndex: focus.type === "RELEASED" ? 1 : 0,
          targetDate: focus.targetDate, startTime: minuteTime(focus.startTime), endTime: minuteTime(focus.endTime),
        });
        await this.checkSupply();
      }
      await this.loadVenueRule();
    } catch (error) { this.setData({ error: error.message, rows: [], supply: [] }); }
    finally { this.setData({ loading: false }); }
  },
  async loadVenueRule() {
    const venue = this.data.venues[this.data.venueIndex];
    if (!venue) {
      this.setData({ dateMax: "", ruleText: "该场馆当前不可预约；历史提醒仍可查看。" });
      return;
    }
    try {
      const detail = await request(`/mini/venues/${venue.id}?date=${this.data.targetDate}`);
      const days = detail.venue.bookingDaysAhead;
      this.setData({ dateMax: addDays(this.data.today, days),
        ruleText: `当前可订至 ${addDays(this.data.today, days - 1)}；下一开放日 ${addDays(this.data.today, days)}。`
          + `开场前 ${detail.venue.minBookingMinutes} 分钟截止，实际以查询为准。` });
    } catch (error) { this.setData({ ruleText: "当前规则加载失败，请重试；登记仍由服务端校验。" }); }
  },
  async selectVenue(event) { this.setData({ venueIndex: Number(event.detail.value) }); await this.loadVenueRule(); },
  selectSport(event) { this.setData({ sportIndex: Number(event.detail.value) }); },
  selectType(event) { this.setData({ typeIndex: Number(event.detail.value) }); },
  selectDate(event) { this.setData({ targetDate: event.detail.value }); },
  selectStart(event) { this.setData({ startTime: event.detail.value }); },
  selectEnd(event) { this.setData({ endTime: event.detail.value }); },
  async save() {
    if (this.data.saving) return;
    const venue = this.data.venues[this.data.venueIndex];
    const sport = this.data.sports[this.data.sportIndex];
    if (!venue || !sport || !this.data.targetDate || this.data.startTime >= this.data.endTime) {
      this.setData({ error: "请选择场馆、运动、日期和递增的完整时段" }); return;
    }
    this.setData({ saving: true, error: "", message: "" });
    try {
      const row = await request("/mini/reminder-subscriptions", { method: "POST", data: {
        venueId: venue.id, sportTypeId: sport.id, targetDate: this.data.targetDate,
        startTime: this.data.startTime, endTime: this.data.endTime, type: typeOptions[this.data.typeIndex].value,
      } });
      this.setData({ focusId: row.id, focusSlotId: 0,
        message: row.availableNow ? "当前已有可预约场次，请直接查看；不会补发旧开放事件。" : "提醒登记已保存" });
      await this.load();
    } catch (error) { this.setData({ error: error.message }); }
    finally { this.setData({ saving: false }); }
  },
  async cancel(event) {
    this.setData({ error: "", message: "" });
    try {
      await request(`/mini/reminder-subscriptions/${event.currentTarget.dataset.id}`, { method: "DELETE" });
      this.setData({ message: "已撤回，后续不再投递新提醒" });
      await this.load();
    } catch (error) { this.setData({ error: error.message }); }
  },
  view(event) {
    this.setData({ focusId: Number(event.currentTarget.dataset.id), focusSlotId: 0 });
    this.load();
  },
  async checkSupply() {
    const focus = this.data.focus;
    if (!focus) return;
    this.setData({ checking: true, error: "" });
    try {
      const params = `date=${focus.targetDate}&sportTypeId=${focus.sportTypeId}`
        + `&startTime=${encodeURIComponent(minuteTime(focus.startTime))}`
        + `&endTime=${encodeURIComponent(minuteTime(focus.endTime))}`;
      const rows = await request(`/public/venues/${focus.venueId}/slot-comparison?${params}`);
      const supply = rows.map(row => ({ ...row, statusLabel: statusLabels[row.status] || "当前不可预约",
        timeLabel: `${time(row.startAt)}–${time(row.endAt)}` }));
      const original = supply.find(row => row.id === this.data.focusSlotId);
      const alternatives = supply.filter(row => row.status === "OPEN");
      this.setData({ supply, supplyNotice: this.data.focusSlotId && (!original || original.status !== "OPEN")
        ? `原空位${original ? original.statusLabel : "当前不可预约"}，可主动选择其他完整场次。`
        : alternatives.length ? "当前有可预约场次，名额未为你保留。" : "当前没有可预约场次，可查看该馆其他时段。" });
    } catch (error) { this.setData({ error: error.message, supply: [],
      supplyNotice: "场馆或场次当前不可预约，可返回登记列表查看其他有效场馆。" }); }
    finally { this.setData({ checking: false }); }
  },
  openVenue(event) {
    const focus = this.data.focus;
    if (!focus) return;
    const relaxed = Boolean(event.currentTarget.dataset.relaxed);
    const params = `id=${focus.venueId}&date=${focus.targetDate}&sportTypeId=${focus.sportTypeId}`
      + (relaxed ? "" : `&startTime=${minuteTime(focus.startTime)}&endTime=${minuteTime(focus.endTime)}`)
      + "&entryType=REMINDER_ENTRY";
    wx.navigateTo({ url: `/pages/venue/index?${params}` });
  },
});
