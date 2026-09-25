const assert = require("node:assert/strict");
const Module = require("node:module");
const path = require("node:path");

let definition;
let requestHandler;
const storage = new Map();
const navigations = [];
const originalLoad = Module._load;

Module._load = function load(request, parent, isMain) {
  if (request === "../../utils/api") return { request: (...args) => requestHandler(...args), money: value => `¥${value / 100}` };
  return originalLoad.call(this, request, parent, isMain);
};
global.Page = value => { definition = value; };
global.wx = {
  getStorageSync: key => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  navigateTo: options => navigations.push(options.url),
};

function loadPage(relativePath) {
  const file = path.join(__dirname, "..", "pages", relativePath, "index.js");
  delete require.cache[require.resolve(file)];
  require(file);
  const page = { ...definition, data: structuredClone(definition.data) };
  page.setData = update => Object.assign(page.data, update);
  return page;
}

async function main() {
  const verify = loadPage("staff-verify");
  verify.applyVenue([{
    venueId: 8, venueName: "二号馆", permissions: ["BOOKING_VERIFY", "CUSTOMER_VIEW", "MEMBER_RECHARGE"],
  }], 0);
  let called;
  requestHandler = async (url, options) => { called = { url, options }; return { customerId: 3, contactName: "测试会员" }; };
  await verify.verify("123456", "SHORT_CODE");
  assert.equal(called.url, "/staff/verifications/verify?venueId=8", "核销必须显式携带所选场馆");
  assert.deepEqual(called.options.data, { credential: "123456", method: "SHORT_CODE" });
  await verify.identifyMember("13800000000");
  assert.match(called.url, /^\/staff\/members\?venueId=8&phone=/);
  verify.data.memberResult = { customerId: 3, contactName: "测试会员" };
  verify.recharge();
  assert.match(navigations.pop(), /venueId=8&customerId=3/);

  const webLogin = loadPage("staff-web-login");
  webLogin.data.current = { venueId: 8 };
  webLogin.data.ticket = webLogin.ticketFromContent("sports-booking://staff/web-login?ticket=ticket-1");
  requestHandler = async (url, options) => { called = { url, options }; return {}; };
  await webLogin.confirm();
  assert.equal(called.url, "/staff/web-login-tickets/ticket-1/confirm");
  assert.deepEqual(called.options.data, { venueId: 8 });

  const recharge = loadPage("staff-recharge");
  recharge.data.venueId = 8;
  recharge.data.customerId = 3;
  recharge.data.selectedPlanId = 4;
  let attempts = 0;
  const keys = [];
  requestHandler = async (_url, options) => {
    keys.push(options.data.clientIdempotencyKey);
    attempts += 1;
    if (attempts === 1) throw new Error("response lost");
    return { id: 9, planName: "充 100" };
  };
  await recharge.submit();
  await recharge.submit();
  assert.equal(keys[0], keys[1], "人工充值重试必须复用幂等键");

  console.log("p3 staff page behavior checks passed");
}

main().finally(() => { Module._load = originalLoad; });
