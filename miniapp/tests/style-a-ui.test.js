const assert = require("node:assert/strict");
const Module = require("node:module");
const path = require("node:path");

let definition;
let today = "2026-12-29";
let requestHandler = async () => [];
const calls = [];
const originalLoad = Module._load;
Module._load = function (request, parent, isMain) {
  if (request === "../../utils/api" && parent?.filename.includes("/pages/")) return {
    request: (...args) => { calls.push(args); return requestHandler(...args); },
    dateInZhengzhou: () => today, money: value => String(value), time: value => value, assetUrl: value => value,
  };
  return originalLoad.call(this, request, parent, isMain);
};
global.Page = value => { definition = value; };
global.wx = { switchTab() {} };
function load(pageName) {
  const file = path.join(__dirname, "..", "pages", pageName, "index.js");
  delete require.cache[require.resolve(file)];
  require(file);
  const page = { ...definition, data: structuredClone(definition.data) };
  page.setData = values => Object.assign(page.data, values);
  return page;
}

async function main() {
  const home = load("home");
  assert.deepEqual(home.data.dateOptions.map(item => item.date), ["2026-12-29", "2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02", "2027-01-03", "2027-01-04"]);
  assert.equal(home.data.dateOptions[0].label, "今天");
  await home.selectDate({ detail: {}, currentTarget: { dataset: { date: "2027-01-02" } } });
  assert.equal(home.data.date, "2027-01-02");
  assert.ok(calls.some(([url]) => url.includes("date=2027-01-02")), "快捷日期必须进入原有查询条件");
  await home.selectDate({ detail: { value: "2027-01-15" } });
  assert.equal(home.data.dateLabel, "01-15", "原生日期选择仍支持七天以外日期");
  today = "2028-02-27";
  await home.load();
  assert.ok(home.data.dateOptions.some(item => item.date === "2028-02-29"));
  assert.equal(home.data.dateOptions[6].date, "2028-03-04");

  const mine = load("mine");
  mine.data.profile = { contactName: "旧联系人", phone: "" };
  mine.data.contactName = "新联系人";
  mine.data.phone = "13800000000";
  mine.toggleProfileForm();
  assert.equal(mine.data.showProfileForm, true);
  await mine.save();
  assert.equal(mine.data.showProfileForm, false);
  assert.equal(mine.data.profile.contactName, "新联系人", "保存后摘要必须同步");
  mine.toggleProfileForm();
  requestHandler = async () => { throw new Error("保存未完成"); };
  await mine.save();
  assert.equal(mine.data.showProfileForm, true, "失败时保留输入表单供修正");
  assert.equal(mine.data.error, "保存未完成");

  const membership = load("membership");
  membership.data.loading = false;
  membership.data.memberships = [{ venueId: 1 }, { venueId: 2 }];
  membership.toggleMemberCode();
  assert.equal(membership.data.showMemberCode, true);
  membership.loadVenue = async () => {};
  await membership.selectMembership({ detail: { value: 1 } });
  assert.equal(membership.data.showMemberCode, false, "切换场馆时收起上一场馆凭证");
  const posts = load("posts");
  assert.equal(posts.data.showEditor, false);
  posts.toggleEditor();
  assert.equal(posts.data.showEditor, true);
  posts.toggleEditor();
  posts.applyEdit({ id: 1, body: "保留原有动态内容", sportTypeId: 1, media: [] });
  assert.equal(posts.data.showEditor, true, "编辑已有动态必须展开编辑器");
  assert.equal(posts.data.body, "保留原有动态内容");
  console.log("style A date, profile, member-code and editor UI checks passed");
}
main().finally(() => { Module._load = originalLoad; }).catch(error => { console.error(error); process.exitCode = 1; });
