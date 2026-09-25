const assert = require("node:assert/strict");
const Module = require("node:module");
const path = require("node:path");
const timeUtils = require("../utils/api");

let requestHandler;
let definition;
const redirects = [];
const api = {
  request: (...args) => requestHandler(...args),
  money: fen => `¥${Number(fen) / 100}`,
  date: value => String(value).slice(0, 10),
  time: value => String(value).slice(11, 16),
  dateTime: value => String(value).replace("T", " ").slice(0, 16),
};
const originalLoad = Module._load;

Module._load = function load(request, parent, isMain) {
  if (request === "../../utils/api") return api;
  return originalLoad.call(this, request, parent, isMain);
};
global.Page = page => { definition = page; };
global.wx = {
  redirectTo: ({ url }) => redirects.push(url),
  removeStorageSync: () => {},
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
  assert.equal(timeUtils.date("2026-09-16T13:00:00Z"), "2026-09-16");
  assert.equal(timeUtils.time("2026-09-16T13:00:00Z"), "21:00");
  assert.equal(timeUtils.dateTime("2026-10-14T16:00:00Z"), "2026-10-15 00:00");

  const payment = loadPage("payment");
  payment.data.id = 1;
  requestHandler = async () => ({
    booking: { id: 1, status: "PENDING_PAYMENT", paymentStatus: "PAYING", amountFen: 8000 },
    composition: { giftBalanceAmountFen: 3000, paidBalanceAmountFen: 4000, externalAmountFen: 1000 },
    payment: null,
  });
  await payment.load();
  assert.equal(payment.data.canPay, false, "没有外部支付记录时不得显示支付操作");

  requestHandler = async () => ({
    booking: { id: 1, status: "PENDING_PAYMENT", paymentStatus: "PAYING", amountFen: 8000 },
    composition: { giftBalanceAmountFen: 3000, paidBalanceAmountFen: 4000, externalAmountFen: 1000 },
    payment: { status: "PENDING" },
  });
  await payment.load();
  assert.equal(payment.data.canPay, true);
  assert.equal(payment.data.canQuery, true);
  let mockPayOptions;
  requestHandler = async (url, options) => {
    if (url.endsWith("/mock-pay")) {
      mockPayOptions = options;
      return { status: "PENDING_PAYMENT" };
    }
    return {
      booking: { id: 1, status: "PENDING_PAYMENT", paymentStatus: "PAYING", amountFen: 8000 },
      composition: { giftBalanceAmountFen: 3000, paidBalanceAmountFen: 4000, externalAmountFen: 1000 },
      payment: { status: "PENDING" },
    };
  };
  await payment.pay();
  assert.equal(mockPayOptions.data, undefined, "客户端不得指定支付成功或失败");

  requestHandler = async () => ({ booking: { id: 2, status: "CONFIRMED" } });
  payment.data.id = 2;
  await payment.load();
  assert.equal(redirects.pop(), "/pages/booking/index?id=2", "全余额确认后必须直达详情");

  const confirm = loadPage("confirm");
  confirm.data = { slot: { id: 9 }, contactName: "测试用户", contactPhone: "13800000000", idempotencyKey: "booking-key" };
  let createBody;
  requestHandler = async (url, options) => {
    if (url === "/mini/bookings/create") {
      createBody = options.data;
      return { id: 3, status: "CONFIRMED" };
    }
    return { booking: { id: 3, status: "CONFIRMED" } };
  };
  await confirm.submit();
  assert.equal(createBody.idempotencyKey, "booking-key");
  assert.equal(redirects.pop(), "/pages/booking/index?id=3");

  const membership = loadPage("membership");
  membership.data.current = { venueId: 5 };
  membership.data.selectedPlanId = 6;
  const keys = [];
  requestHandler = async (_url, options) => {
    keys.push(options.data.idempotencyKey);
    if (keys.length === 1) throw new Error("response lost");
    return { id: 7, status: "PENDING", paidAmountFen: 50000, giftAmountFen: 5000 };
  };
  await membership.createRecharge();
  await membership.createRecharge();
  assert.equal(keys[0], keys[1], "充值创建重试必须复用同一幂等键");

  console.log("p2 miniapp behavior checks passed");
}

main().finally(() => { Module._load = originalLoad; });
