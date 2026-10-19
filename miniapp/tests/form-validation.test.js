const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");
const validation = require("../utils/validation");

test("小程序表单指出具体字段并阻止无效请求和媒体上传", async () => {
  let requests = 0;
  const api = {
    dateInZhengzhou: () => "2026-10-15",
    request: async () => { requests++; throw new Error("不应发送无效请求"); },
    uploadFile: async () => { requests++; throw new Error("不应上传无效表单的媒体"); },
  };
  function page(name) {
    let definition;
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, "..", "pages", `${name}.js`), "utf8"), {
      Page: value => { definition = value; },
      require: name => name === "../../utils/validation" ? validation : api,
      wx: { showModal: options => options.success({ confirm: true, content: "" }) },
    });
    return { ...definition, data: structuredClone(definition.data), setData(values) { Object.assign(this.data, values); } };
  }
  const cases = [
    ["confirm/index", {}, "submit", "error", /联系人姓名/],
    ["confirm/index", { contactName: "测试", contactPhone: "123" }, "submit", "error", /11 位手机号/],
    ["mine/index", {}, "save", "error", /联系人姓名/],
    ["staff-bind/index", {}, "bind", "error", /绑定码/],
    ["staff-recharge/index", {}, "submit", "error", /充值档位/],
    ["staff-web-login/index", {}, "confirm", "error", /工作场馆/],
    ["membership/index", { loading: false }, "createRecharge", "actionError", /充值场馆/],
    ["reminders/index", {}, "save", "error", /场馆/],
    ["reminders/index", { venues: [{ id: 1 }], sports: [{ id: 2 }], startTime: "21:00", endTime: "19:00" }, "save", "error", /结束时间.*开始时间/],
    ["posts/index", { body: "测试动态" }, "publish", "error", /运动类型/],
    ["posts/detail", {}, "submitComment", "error", /评论内容/],
    ["posts/detail", { post: { bookingId: 1 }, level: "CASUAL", maxMembers: 21 }, "openRecruitment", "error", /招募人数/],
    ["community/index", {}, "appeal", "error", /申诉说明/],
    ["review/index", { body: "测".repeat(2001) }, "save", "error", /评价内容.*2000/],
  ];
  for (const [name, data, action, error, expected] of cases) {
    const instance = page(name);
    Object.assign(instance.data, data);
    requests = 0;
    await instance[action]();
    assert.match(instance.data[error], expected, name);
    assert.equal(requests, 0, name);
  }
  const verifier = page("staff-verify/index");
  verifier.data.current = { venueId: 1 };
  await verifier.verify("123", "SHORT_CODE");
  assert.match(verifier.data.error, /6 位数字/);
  await verifier.identifyMember("");
  assert.match(verifier.data.error, /会员码或完整手机号/);
  assert.equal(requests, 0);
});

test("合法手机号、人数和分钟时段继续通过，不截断遗留秒数", () => {
  validation.validateContact("本地测试", "18800000000");
  validation.requireNumber(1, "人数", 1, 20);
  validation.requireNumber(20, "人数", 1, 20);
  validation.validateTimeRange("09:02", "10:00");
  assert.throws(() => validation.validateTimeRange("19:00:30", "21:00"), /精确到分钟/);
});
