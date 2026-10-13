const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
let definition, saved, url, fail = false;
const calls = [];
const slots = ['OPEN', 'HELD', 'BOOKED', 'CLOSED', 'UNAVAILABLE'].map((status, i) => ({
  id: i + 1, courtId: i % 2 + 1, courtName: `${i % 2 + 1}号场`, sportTypeId: 1, sportName: '羽毛球', status,
  startAt: '2026-10-13T19:00:00+08:00', endAt: i === 0 ? '2026-10-13T21:30:00+08:00' : '2026-10-13T20:00:00+08:00',
  priceFen: 12345, cancelDeadline: '2026-10-13T17:00:00+08:00',
}));
Module._load = function(name, parent, main) {
  if (name === '../../utils/api' && parent?.filename.includes('/pages/venue/')) return {
    dateInZhengzhou: () => '2026-10-13', assetUrl: s => s, time: s => s.slice(11, 16),
    request: async path => {
      calls.push(path);
      if (fail) throw new Error('场次加载失败');
      if (path.includes('/slot-comparison')) return slots;
      if (path.startsWith('/mini/venues/')) return { venue: {name: '测试馆', district: '二七区', entryNotice: '穿运动鞋'}, sports: [], courts: [{id: 1, name: '1号场'}, {id: 2, name: '2号场'}] };
      return false;
    }
  };
  return originalLoad.call(this, name, parent, main);
};
global.Page = p => { definition = p; };
global.wx = {setNavigationBarTitle() {}, setStorageSync: (_, s) => { saved = s; }, navigateTo: o => { url = o.url; }};
require('../pages/venue/index.js');
const page = {...definition, data: structuredClone(definition.data), setData(v) { Object.assign(this.data, v); }};
(async () => {
  await page.onLoad({id: '3', date: '2026-10-13', sportTypeId: '1', startTime: '19:00', endTime: '22:00', minPriceFen: '100', district: '金水区', courtId: '2', preferredStartTime: '19:00', bookAgainId: '99', entryType: 'BOOK_AGAIN'});
  assert.ok(calls.some(p => p.includes('/slot-comparison') && p.includes('startTime=19%3A00') && p.includes('minPriceFen=100')));
  assert.equal(page.data.groups.flatMap(g => g.slots).length, 5);
  assert.equal(page.data.venue.districtLabel, '二七区', '详情展示场馆维护值，不展示搜索条件金水区');
  assert.equal(page.data.matrixRows.length, 2, '保留不同长度的真实区间，不拆小时格');
  assert.ok(page.data.preferenceNotice, '偏好不可订时说明替代需主动选择');
  page.selectView({currentTarget: {dataset: {mode: 'matrix'}}});
  assert.equal(page.data.viewMode, 'matrix');
  for (const id of [2, 3, 4, 5]) page.book({currentTarget: {dataset: {id}}});
  assert.equal(saved, undefined, '所有不可订状态禁止跳转');
  page.book({currentTarget: {dataset: {id: 1}}});
  assert.equal(saved.endAt, '2026-10-13T21:30:00+08:00');
  assert.equal(saved.priceFen, 12345); assert.equal(saved.bookAgainId, 99);
  assert.equal(saved.entryNotice, '穿运动鞋'); assert.equal(url, '/pages/confirm/index');
  fail = true; await page.load();
  assert.equal(page.data.error, '场次加载失败');
  assert.equal(page.data.startTime, '19:00');
  saved = undefined; page.book({currentTarget: {dataset: {id: 1}}}); assert.equal(saved, undefined);
  fail = false; await page.load(); assert.equal(page.data.error, '');
  console.log('P5 complete slot comparison, public states, filters, preferences and retry passed');
})().finally(() => {Module._load = originalLoad;}).catch(e => {console.error(e); process.exitCode = 1;});
