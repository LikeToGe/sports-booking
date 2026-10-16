const assert = require("node:assert/strict");
const Module = require("node:module");
const path = require("node:path");
let definition;
let requestHandler;
const app = { globalData: { apiBase: "http://127.0.0.1:8091/api",profile: { id: 9 } },ensureLogin: async () => "test-token" };
const originalLoad = Module._load;
const calls = [];
Module._load = function (request,parent,isMain) {
  if (["../../utils/api","../utils/api","./api"].includes(request) && parent && parent.filename
      && (parent.filename.includes("/pages/") || parent.filename.includes("/custom-tab-bar/") || parent.filename.endsWith("/utils/content.js"))) return {
    request: (...args) => { calls.push(args); return requestHandler(...args); },
    uploadFile: async () => ({ id: 12 }),downloadMedia: async url => url,
    dateTime: value => value,money: value => String(value),assetUrl: value => value,
  };
  return originalLoad.call(this,request,parent,isMain);
};
global.Page = value => { definition = value; };
global.Component = value => { definition = { ...value,...value.methods }; };
global.getApp = () => app;
global.getCurrentPages = () => [{ route: "pages/home/index" }];
global.wx = { navigateTo() {},switchTab() {},removeStorageSync() {} };
function load(relative) {
  const file = path.join(__dirname,"..",relative);
  delete require.cache[require.resolve(file)];
  require(file);
  const page = { ...definition,data: structuredClone(definition.data) };
  page.setData = values => Object.assign(page.data,values);
  return page;
}
async function main() {
  const editor = load("pages/posts/index.js");
  requestHandler = async url => {
    if (url === "/mini/sports") return [{ id: 1,name: "羽毛球" }];
    if (url === "/mini/venues") return [{ id: 2,name: "运动馆" }];
    if (url === "/mini/bookings/list") return [{ id: 6,status: "CONFIRMED" },{ id: 7,status: "PENDING_PAYMENT" }];
    if (url === "/mini/bookings/6") return { booking: { sportTypeId: 1,venueId: 2 } };
    if (url === "/mini/posts/create") return { id: 5 };
    if (url.startsWith("/mini/posts/list")) return [];
    throw new Error("Unexpected path " + url);
  };
  editor.onLoad(); await editor.ready;
  assert.equal(editor.data.bookings.length,2,"发布器只能列出已确认预约");
  await editor.selectBooking({ detail: { value: 1 } });
  editor.data.body = "运动现场";
  editor.data.selectedMedia = [{ path: "local.jpg",uploadId: 12 }];
  await editor.publish();
  const payload = calls.find(([url]) => url === "/mini/posts/create")[1].data;
  assert.equal(payload.bookingId,6);
  assert.equal(payload.venueId,2);
  assert.deepEqual(payload.uploadIds,[12]);

  const detail = load("pages/posts/detail.js");
  detail.data.id = 5;
  detail.data.post = { id: 5,bookingId: 6 };
  detail.selectLevel({ detail: { value: 1 } });
  detail.data.feeMode = "AA";
  detail.data.aaAmount = "0.29";
  requestHandler = async url => {
    if (url === "/mini/posts/5/recruitment") return null;
    if (url === "/mini/posts/5") return { id: 5,bookingId: 6,media: [] };
    if (url.endsWith("/comments")) return [];
    throw new Error("Unexpected path " + url);
  };
  await detail.openRecruitment();
  const recruitment = calls.find(([url,options]) => url === "/mini/posts/5/recruitment" && options?.method === "POST");
  assert.equal(recruitment[1].data.aaAmountFen,29,"金额应精确转换为分");
  assert.equal(recruitment[1].data.level,"CASUAL","选择中文水平后仍提交约定枚举");
  detail.data.aaAmount = "1.005";
  const previousCalls = calls.length;
  await detail.openRecruitment();
  assert.equal(calls.length,previousCalls,"三位小数不得静默舍入提交");

  detail.data.body = "新评论";
  detail.data.parentId = 8;
  requestHandler = async (url,options) => {
    if (url.endsWith("/comments") && options?.method === "POST") return { id: 10,moderationStatus: "PENDING" };
    if (url.endsWith("/comments")) return [{ id: 9,body: "旧公开评论" }];
    if (url.endsWith("/recruitment")) return null;
    return { id: 5,media: [] };
  };
  await detail.submitComment();
  assert.deepEqual(detail.data.comments.map(row => row.id),[9],"待审评论不能被当成公开评论插入");
  assert.match(detail.data.message,/审核/);
  requestHandler = async (url,options) => {
    if (url === "/mini/posts/5/comments") return [{ id: 10,body: "待审错字",isMine: true,moderationStatus: "PENDING",contentStatus: "PENDING" }];
    if (url === "/mini/posts/comments/10" && options?.method === "PUT") return { id: 10,moderationStatus: "PENDING" };
    if (url === "/mini/posts/comments/10" && options?.method === "DELETE") return true;
    if (url.endsWith("/recruitment")) return null;
    return { id: 5,media: [] };
  };
  await detail.load();
  assert.match(detail.data.comments[0].moderationLabel,/仅自己可见/);
  detail.editComment({ currentTarget: { dataset: { id: 10 } } });
  assert.equal(detail.data.body,"待审错字","作者可以加载当前待审正文进行修改");
  detail.data.body = "已改错字";
  await detail.submitComment();
  assert.equal(calls.find(([url,options]) => url === "/mini/posts/comments/10" && options?.method === "PUT")[1].data.body,"已改错字");
  wx.showModal = options => options.success({ confirm: true,content: "本地测试说明" });
  await detail.removeComment({ currentTarget: { dataset: { id: 10 } } });
  assert.ok(calls.some(([url,options]) => url === "/mini/posts/comments/10" && options?.method === "DELETE"));

  const communityUser = load("pages/community/user.js");
  requestHandler = async url => url.startsWith("/mini/posts/users/") ? { id: 12,nickname: "本地测试球友",posts: [] } : true;
  await communityUser.onLoad({ id: 12 });
  wx.showActionSheet = options => options.success({ tapIndex: 1 });
  await communityUser.reportUser();
  assert.ok(calls.some(([url,options]) => url.startsWith("/mini/content/reports?targetType=USER&targetId=12&reasonCode=ABUSE") && options?.method === "POST"));
  assert.match(communityUser.data.message,/账号举报已提交/);

  detail.data.recruitment = { id: 2,members: [{ userId: 3 }] };
  requestHandler = async () => { throw new Error("数据不存在"); };
  await detail.load();
  assert.equal(detail.data.post,null);
  assert.equal(detail.data.recruitment,null,"失效落地页不能残留成员信息");
  assert.deepEqual(detail.data.comments,[]);

  const tabs = load("custom-tab-bar/index.js");
  requestHandler = async () => false;
  await tabs.refresh();
  assert.equal(tabs.data.entries.length,4);
  assert.equal(tabs.data.entries.some(item => item.ugc),false);
  const iconsWithoutCommunity = Object.fromEntries(tabs.data.entries.map(item => [item.route,item.icon]));
  assert.equal(iconsWithoutCommunity["pages/bookings/index"],"calendar");
  assert.equal(iconsWithoutCommunity["pages/membership/index"],"membership");
  assert.equal(iconsWithoutCommunity["pages/mine/index"],"user");
  requestHandler = async () => true;
  await tabs.refresh();
  assert.equal(tabs.data.entries.length,5);
  for (const [route,icon] of Object.entries(iconsWithoutCommunity)) {
    assert.equal(tabs.data.entries.find(item => item.route === route).icon,icon,"社区开关不能改变其他入口的图标");
  }
  const records = load("pages/community/index.js");
  records.data.mode = "posts";
  requestHandler = async url => url === "/public/ugc-config" ? false : [];
  await records.load();
  assert.equal(records.data.mode,"messages","关闭 UGC 时记录页只保留消息");

  const transport = require("../utils/api");
  let logins = 0;
  app.ensureLogin = async () => { logins++; return "test-token"; };
  wx.request = options => options.success({ statusCode: 200,data: { code: 0,data: [] } });
  await transport.request("/public/venues/2/reviews");
  assert.equal(logins,0,"公开评价不应强制登录");
  await transport.request("/mini/posts/list");
  assert.equal(logins,1);
  console.log("p4 community page behavior checks passed");
}
main().finally(() => { Module._load = originalLoad; }).catch(error => { console.error(error); process.exitCode = 1; });
