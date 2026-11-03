const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");

function clock() {
  let now = 0, sequence = 0;
  const timers = new Map();
  return {
    setTimeout(callback, delay = 0) { const id = ++sequence; timers.set(id, { at: now + delay, callback }); return id; },
    clearTimeout(id) { timers.delete(id); },
    tick(duration) {
      const end = now + duration;
      while (true) {
        const entry = [...timers].filter(([, value]) => value.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
        if (!entry) break;
        now = entry[1].at; timers.delete(entry[0]); entry[1].callback();
      }
      now = end;
    },
  };
}

function load(file, environment = {}) {
  let definition;
  const timer = environment.timer || clock();
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), {
    Page: value => { definition = value; }, Component: value => { definition = value; },
    setTimeout: timer.setTimeout, clearTimeout: timer.clearTimeout,
    getApp: () => environment.app || { globalData: {} },
    getCurrentPages: () => environment.pages || [],
    wx: { nextTick: callback => timer.setTimeout(callback), ...environment.wx },
    require: name => name.endsWith("/validation") ? require("../utils/validation")
      : name.endsWith("/content") ? {} : environment.api || {},
  });
  const properties = Object.fromEntries(Object.entries(definition.properties || {}).map(([name, spec]) =>
    [name, spec.value ?? (spec === Boolean ? false : spec === String ? "" : undefined)]));
  const instance = {
    ...definition, ...definition.methods,
    data: { ...properties, ...structuredClone(definition.data || {}) }, events: [],
    triggerEvent(name, detail) { this.events.push({ name, detail }); },
    setData(values, callback) {
      for (const [key, value] of Object.entries(values)) {
        const parts = key.split(".");
        let target = this.data;
        for (const part of parts.slice(0, -1)) target = target[part];
        target[parts.at(-1)] = value;
      }
      for (const [keys, observer] of Object.entries(definition.observers || {})) {
        if (keys.split(",").some(key => Object.hasOwn(values, key.trim()))) observer.call(this);
      }
      callback?.();
    },
  };
  return { instance, timer };
}

test("滚轮惯性未停止不能提交，取消和页面隐藏不改变原条件", () => {
  const { instance: wheel, timer } = load("components/time-wheel/index.js");
  wheel.data.value = "19:00:30";
  wheel.open(); wheel.start(); wheel.confirm();
  assert.equal(wheel.events.length, 0);
  wheel.change({ detail: { value: [23, 59] } });
  wheel.confirm();
  assert.equal(wheel.events[0].detail.value, "23:59");
  assert.equal(wheel.data.value, "19:00:30");
  wheel.open(); wheel.change({ detail: { value: [0, 0] } }); wheel.cancel();
  assert.equal(wheel.events.length, 1);
  assert.equal(wheel.data.value, "19:00:30");
  wheel.open(); wheel.start(); wheel.end(); timer.tick(0);
  wheel.pageLifetimes.hide.call(wheel); wheel.confirm();
  assert.equal(wheel.events.length, 1);
  assert.equal(wheel.data.opened, false);
  wheel.data.disabled = true; wheel.open();
  assert.equal(wheel.data.opened, false);
});

test("只有完成态通知跳转；离开页面停止计时，返回后恢复反馈", () => {
  const { instance: feedback, timer } = load("components/action-feedback/index.js");
  feedback.setData({ busy: true }); timer.tick(1000);
  assert.equal(feedback.events.length, 0);
  feedback.setData({ done: true }); timer.tick(1000);
  assert.equal(feedback.events.length, 0);
  feedback.setData({ busy: false }); timer.tick(419);
  assert.equal(feedback.events.length, 0);
  feedback.pageLifetimes.hide.call(feedback); timer.tick(1000);
  assert.equal(feedback.events.length, 0);
  feedback.pageLifetimes.show.call(feedback); timer.tick(420);
  assert.deepEqual(feedback.events.map(event => event.name), ["complete"]);
  feedback.setData({ done: false }); timer.tick(1000);
  assert.equal(feedback.events.length, 1);
  feedback.setData({ done: true }); feedback.lifetimes.detached.call(feedback); timer.tick(1000);
  assert.equal(feedback.events.length, 1);
});

function tabs(width = 375) {
  const timer = clock();
  const app = { globalData: {}, errors: [], showOperationFeedback(success, message) { this.errors.push({ success, message }); } };
  const pages = [{ route: "pages/home/index" }];
  const calls = [];
  const environment = { timer, app, pages, api: { request: async () => false }, wx: {
    getWindowInfo: () => ({ windowWidth: width, windowHeight: 812 }),
    switchTab: options => calls.push(options),
  } };
  const source = load("custom-tab-bar/index.js", environment).instance;
  const target = load("custom-tab-bar/index.js", environment).instance;
  source.data.route = "pages/home/index";
  target.data.route = "pages/mine/index";
  const tap = (route = "pages/mine/index") => source.change({ currentTarget: { dataset: { route } }, changedTouches: [{ clientX: width - 20, clientY: 790 }] });
  return { source, target, timer, app, pages, calls, tap };
}

test("触点圆覆盖四角，扩散结束再切页；目标页接罩并解除连续点击锁", () => {
  for (const width of [320, 375, 390, 414]) {
    const { source, target, timer, app, pages, calls, tap } = tabs(width);
    tap("pages/home/index"); tap("pages/posts/index");
    assert.equal(source.data.transitionVisible, false, "同一 Tab 和关闭的社区不产生转场");
    tap(); tap("pages/bookings/index");
    const [, left, top, diameter] = /left:([\d.-]+)px;top:([\d.-]+)px;width:([\d.-]+)px/.exec(source.data.circleStyle);
    const radius = Number(diameter) / 2;
    for (const x of [0, width]) for (const y of [0, 812]) {
      assert.ok(Math.hypot(x - (Number(left) + radius), y - (Number(top) + radius)) <= radius);
    }
    timer.tick(279); assert.equal(calls.length, 0);
    timer.tick(1); assert.equal(calls.length, 1);
    source.pageLifetimes.hide.call(source);
    pages[0] = { route: "pages/mine/index", getTabBar: () => target };
    calls[0].success();
    assert.equal(target.data.transitionVisible, true);
    assert.equal(target.data.expanded, true);
    timer.tick(160);
    assert.equal(target.data.transitionVisible, false);
    assert.equal(app.globalData.tabTransition, null);
    timer.tick(3000); assert.equal(app.errors.length, 0);
  }
});

test("切页失败、超时或扩散中离开都可清除遮罩并重试", () => {
  const { source, timer, app, calls, tap } = tabs();
  tap(); timer.tick(280); calls[0].fail();
  assert.equal(source.data.transitionVisible, false);
  assert.equal(app.globalData.tabTransition, null);
  assert.equal(app.errors.length, 1);
  tap(); source.pageLifetimes.hide.call(source); timer.tick(3000);
  assert.equal(calls.length, 1, "页面离开后不能迟到切页");
  assert.equal(app.globalData.tabTransition, null);
  tap(); timer.tick(2500);
  assert.equal(source.data.transitionVisible, false);
  assert.equal(app.globalData.tabTransition, null);
});

test("预约创建只提交一次，成功反馈完成前保持原页；失败不显示完成", async () => {
  let resolve, writes = 0;
  const navigation = [];
  const { instance: page } = load("pages/confirm/index.js", { api: { request: async url => {
    if (url.endsWith("/create")) { writes++; return new Promise(done => { resolve = done; }); }
    return { booking: { status: "CONFIRMED" } };
  } }, wx: { removeStorageSync() {}, redirectTo: value => navigation.push(value.url) } });
  Object.assign(page.data, { loading: false, slot: { id: 9 }, contactName: "测试", contactPhone: "13800000000" });
  const request = page.submit(); await page.submit();
  assert.equal(writes, 1);
  resolve({ id: 4, status: "CONFIRMED" }); await request;
  assert.equal(page.data.created, true);
  assert.equal(navigation.length, 0);
  page.openCreatedBooking();
  assert.equal(navigation[0], "/pages/booking/index?id=4");
  const { instance: failed } = load("pages/confirm/index.js", { api: { request: async () => { throw new Error("库存已变化"); } } });
  Object.assign(failed.data, { loading: false, slot: { id: 9 }, contactName: "测试", contactPhone: "13800000000" });
  await failed.submit();
  assert.equal(failed.data.created, false);
  assert.match(failed.data.error, /库存/);
});

test("支付请求成功但仍待确认时不显示勾号；服务端确认后才完成", async () => {
  let confirmed = false;
  const navigation = [];
  const { instance: page } = load("pages/payment/index.js", { api: { money: value => String(value), request: async url => {
    if (url.endsWith("/mock-pay")) return {};
    return { booking: { id: 1, status: confirmed ? "CONFIRMED" : "PENDING_PAYMENT" },
      composition: { giftBalanceAmountFen: 0, paidBalanceAmountFen: 0, externalAmountFen: 100 }, payment: { status: "PENDING" } };
  } }, wx: { redirectTo: value => navigation.push(value.url) } });
  Object.assign(page.data, { id: 1, loading: false, canPay: true });
  await page.pay();
  assert.equal(page.data.paymentDone, false);
  assert.match(page.data.message, /尚未确认/);
  confirmed = true; await page.pay();
  assert.equal(page.data.paymentDone, true);
  assert.equal(page.data.canPay, false);
  assert.equal(page.data.message, "", "支付已确认后不能保留待确认提示");
  assert.equal(navigation.length, 0);
  page.openBooking(); assert.equal(navigation.length, 1);
});

test("协议取消勾选阻止提交；评论开关失败回到原状态且可重试", async () => {
  let writes = 0;
  const { instance: agreement } = load("pages/content-agreement/index.js", { api: { request: async () => { writes++; } } });
  agreement.data.agreement = { rulesVersion: "1", privacyVersion: "1" };
  agreement.toggle({ detail: { value: ["accept"] } });
  agreement.toggle({ detail: { value: [] } }); await agreement.accept();
  assert.equal(writes, 0);
  agreement.toggle({ detail: { value: ["accept"] } }); await agreement.accept();
  assert.equal(agreement.data.accepted, true);
  await agreement.accept(); assert.equal(writes, 1);
  const { instance: detail } = load("pages/posts/detail.js", { api: { request: async () => { throw new Error("保存失败"); } } });
  detail.data.post = { commentsEnabled: true };
  await detail.toggleComments({ detail: { value: false } });
  assert.equal(detail.data.post.commentsEnabled, true);
  assert.equal(detail.data.commentsSaving, false);
  assert.match(detail.data.error, /保存失败/);
});
