import assert from "node:assert/strict";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { createServer } from "vite";
import { createSSRApp, h } from "vue";
import { renderToString } from "vue/server-renderer";

test("表单在提交前指出具体错误，不发送无效写入请求", async () => {
  const storage = globalThis.localStorage;
  const fetch = globalThis.fetch;
  const window = globalThis.window;
  globalThis.window = { setTimeout: () => 0, clearTimeout() {} };
  globalThis.localStorage = { getItem: () => null };
  let requests = 0;
  globalThis.fetch = async () => { requests++; throw new Error("不应发送无效请求"); };
  const server = await createServer({
    root: fileURLToPath(new URL("../", import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: "custom",
  });
  async function setup(name) {
    const { default: component } = await server.ssrLoadModule(`/src/views/${name}View.vue`);
    let state;
    await renderToString(createSSRApp({ setup() {
      state = component.setup({}, { expose() {} });
      return () => h("div");
    } }));
    return state;
  }
  try {
    const { auth } = await server.ssrLoadModule("/src/auth.ts");
    auth.principal = { role: "VENUE_ADMIN", venueId: 1 };
    const fixtures = [
      ["Sessions", state => {
        state.courts.value = [{ id: 1, name: "乒乓球1号场", sportIds: "2", status: "ACTIVE" }];
        Object.assign(state.form, { courtId: 1, sportTypeId: 3 });
      }, "save", "createError", /场地.*不支持.*运动/],
      ["Schedule", state => {
        state.courts.value = [{ id: 1, name: "1号场", sportIds: "2", status: "ACTIVE" }];
        Object.assign(state.form, { courtId: 1, sportTypeId: 2, startTime: "10:00", endTime: "09:00" });
      }, "save", "createError", /结束时间.*开始时间/],
      ["Courts", state => { state.name.value = "1号场"; }, "saveCourt", "createError", /至少选择.*运动/],
      ["Bookings", state => { state.offlineName.value = "测试联系人"; }, "saveOffline", "formError", /联系电话/],
      ["RechargePlans", state => { state.form.value.name = "测试档位"; }, "save", "formError", /实付金额/],
      ["ReviewTags", state => { state.name.value = "测试标签"; }, "save", "formError", /至少选择.*运动/],
      ["ContentRestrictions", () => {}, "submit", "error", /用户 ID/],
      ["ContentSettings", () => {}, "saveAgreement", "error", /社区规则版本/],
      ["Today", state => { state.selected.value = { id: 1 }; }, "createOffline", "actionError", /联系人姓名/],
      ["Reports", state => { state.startDate.value = "2026-10-20"; state.endDate.value = "2026-10-15"; }, "load", "error", /结束日期.*开始日期/],
      ["Profile", () => {}, "save", "error", /场馆名称/],
      ["PlatformVenues", () => {}, "submitVenue", "formError", /场馆名称/],
      ["Employees", () => {}, "save", "createError", /员工姓名/],
      ["PlatformSports", () => {}, "save", "createError", /运动类型名称/],
      ["Roles", () => {}, "save", "formError", /角色名称/],
      ["OfficialPosts", () => {}, "publish", "error", /动态正文/],
    ];
    const validation = await server.ssrLoadModule("/src/validation.ts");
    assert.doesNotThrow(() => validation.validateAmount(0.29, "场次价格"));
    assert.doesNotThrow(() => validation.validateAmount(0, "场次价格"));
    assert.throws(() => validation.validateAmount(1.005, "场次价格"), /最多保留两位小数/);
    assert.throws(() => validation.requireNumber(1.5, "赠送有效天数", 1), /必须是整数/);
    assert.throws(() => validation.requireNumber("", "实付金额", 1), /有效的实付金额/);
    assert.doesNotThrow(() => validation.validateDateRange("2026-01-01", "2026-03-31"));
    assert.throws(() => validation.validateDateRange("2026-01-01", "2026-04-01"), /90 天/);
    assert.doesNotThrow(() => validation.validateImage(new File(["local mock"], "cover.JPG")));
    assert.throws(() => validation.validateImage(new File(["local mock"], "cover.gif")), /图片格式/);
    assert.throws(() => validation.validateImage(new File([], "cover.jpg")), /文件为空/);
    for (const [name, arrange, action, error, expected] of fixtures) {
      const state = await setup(name);
      arrange(state);
      requests = 0;
      await state[action]();
      assert.match(state[error].value, expected, name);
      assert.equal(requests, 0, `${name} 不应发送无效请求`);
    }
  } finally {
    await server.close();
    globalThis.fetch = fetch;
    if (window === undefined) delete globalThis.window;
    else globalThis.window = window;
    if (storage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = storage;
  }
});
