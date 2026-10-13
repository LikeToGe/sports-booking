const assert = require('node:assert/strict');
const Module = require('node:module');
const path = require('node:path');
const originalLoad = Module._load;
let definition;
const calls = [];
let navigateUrl = '';
let pendingSlot;
let fail = false;
Module._load = function (name, parent, main) {
  if (name === '../../utils/api' && parent?.filename.includes('/pages/')) return {
    dateInZhengzhou: () => '2026-10-12', money: fen => `¥${fen / 100}`, time: s => s.slice(11, 16), assetUrl: s => s,
    date: s => s.slice(0, 10),
    request: async (url, options) => {
      calls.push([url, options]);
      if (fail) throw new Error('网络失败');
      if (url.startsWith('/mini/slots/search')) return [{ id: 12, venueId: 2, venueName: '测试馆', courtId: 3, courtName: '1 号场', sportTypeId: 1, sportName: '羽毛球', startAt: '2026-10-13T19:00:00+08:00', endAt: '2026-10-13T20:00:00+08:00', priceFen: 5050 }];
      if (url.includes('/slot-comparison')) return [{ id: 12, courtId: 3, priceFen: 5050, status: 'OPEN', startAt: '2026-10-13T19:00:00+08:00', endAt: '2026-10-13T20:00:00+08:00', cancelDeadline: '2026-10-13T17:00:00+08:00' }];
      if (url.startsWith('/mini/venues/')) return { venue: { id: 2, name: '测试馆' }, sports: [], courts: [{ id: 3 }], slots: [{ id: 12, courtId: 3, priceFen: 5050, startAt: '2026-10-13T19:00:00+08:00', endAt: '2026-10-13T20:00:00+08:00' }] };
      if (url === '/public/ugc-config') return false;
      return [];
    },
  };
  return originalLoad.call(this, name, parent, main);
};
global.Page = page => { definition = page; };
global.wx = { navigateTo: ({ url }) => { navigateUrl = url; }, setNavigationBarTitle() {}, redirectTo() {}, removeStorageSync() {}, setStorageSync: (_, value) => { pendingSlot = value; }, getStorageSync: () => pendingSlot };
function load(name) {
  const file = path.join(__dirname, '..', 'pages', name, 'index.js');
  delete require.cache[require.resolve(file)];
  require(file);
  const page = { ...definition, data: structuredClone(definition.data) };
  page.setData = values => Object.assign(page.data, values);
  return page;
}
async function main() {
  const home = load('home');
  Object.assign(home.data, { date: '2026-10-13', selectedSportId: 1, startTime: '19:00', endTime: '21:00', minPrice: '50.50', maxPrice: '70' });
  await home.search();
  assert.equal(home.data.filtersDirty, false);
  const searchUrl = calls.at(-1)[0];
  assert.ok(searchUrl.startsWith('/mini/slots/search?'), '时间条件必须查询真实具体场次');
  assert.ok(searchUrl.includes('minPriceFen=5050') && searchUrl.includes('maxPriceFen=7000'));
  assert.equal(home.data.slots[0].price, '¥50.50');
  home.openVenue({ currentTarget: { dataset: { id: 2 } } });
  assert.ok(navigateUrl.includes('sportTypeId=1') && navigateUrl.includes('startTime=19%3A00') && navigateUrl.includes('maxPriceFen=7000'));
  const venue = load('venue');
  await venue.onLoad(Object.fromEntries(new URLSearchParams(navigateUrl.split('?')[1])));
  assert.ok(calls.some(([url]) => url.startsWith('/mini/venues/2?') && url.includes('startTime=19%3A00') && url.includes('minPriceFen=5050')));
  venue.book({ currentTarget: { dataset: { id: 12 } } });
  assert.equal(pendingSlot.entryType, 'TIME_SEARCH');
  const confirm = load('confirm');
  await confirm.onLoad();
  await confirm.submit();
  const create = calls.find(([url]) => url === '/mini/bookings/create')[1].data;
  assert.equal(create.expectedPriceFen, 5050);
  assert.equal(create.entryType, 'TIME_SEARCH');
  home.onFilter({ currentTarget: { dataset: { field: 'startTime' } }, detail: { value: '19:00' } });
  assert.equal(home.data.filtersDirty, true, '尚未提交的条件不显示已查无结果');
  await home.search();
  assert.equal(home.data.filtersDirty, false);
  fail = true;
  await home.search();
  assert.equal(home.data.startTime, '19:00', '失败不放宽条件');
  assert.equal(home.data.error, '网络失败');
  assert.equal(home.data.loading, false);
  fail = false;
  home.data.minPrice = '50.505';
  const before = calls.length;
  await home.search();
  assert.equal(calls.length, before, '超精度金额不能舍入后提交');
  assert.ok(home.data.error);
  await home.clearFilters();
  assert.equal(home.data.startTime, '');
  assert.equal(home.data.date, '2026-10-13', '主动清除不改变日期');
  console.log('P5 time search, condition preservation, price and entry checks passed');
}
main().finally(() => { Module._load = originalLoad; }).catch(error => { console.error(error); process.exitCode = 1; });
