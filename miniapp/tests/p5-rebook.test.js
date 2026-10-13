const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
let definition, navigated, fail = false;
const calls = [];
Module._load = function(name, parent, main) {
  if (name === '../../utils/api' && parent?.filename.includes('/pages/rebook/')) return {
    dateInZhengzhou: () => '2026-10-14',
    time: s => new Date(s).toLocaleTimeString('en-GB', {timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit'}),
    request: async path => {
      calls.push(path);
      if (fail) throw new Error('预约不存在');
      return {booking: {id: 99, venueId: 1, courtId: 4, sportTypeId: 2,
        startAt: '2026-10-01T11:00:00Z', amountFen: 100, contactName: '历史联系人'}};
    },
  };
  return originalLoad.call(this, name, parent, main);
};
global.Page = value => { definition = value; };
global.wx = {navigateTo: value => {navigated = value.url;}};
require('../pages/rebook/index.js');
const page = {...definition, data: structuredClone(definition.data), setData(value) {Object.assign(this.data, value);}};
(async () => {
  await page.onLoad({bookingId: '99'});
  assert.equal(page.data.date, '', '必须明确选择新日期，不能预选后直接预约');
  page.viewSlots(); assert.equal(navigated, undefined);
  page.selectDate({detail: {value: '2026-10-20'}});
  page.viewSlots();
  const params = new URLSearchParams(navigated.split('?')[1]);
  assert.equal(params.get('id'), '1'); assert.equal(params.get('courtId'), '4');
  assert.equal(params.get('sportTypeId'), '2'); assert.equal(params.get('date'), '2026-10-20');
  assert.equal(params.get('preferredStartTime'), '19:00'); assert.equal(params.get('entryType'), 'BOOK_AGAIN');
  assert.ok(!params.has('amountFen') && !params.has('contactName') && !params.has('endTime'));
  assert.deepEqual(calls, ['/mini/bookings/99']);
  fail = true; navigated = undefined; await page.load(); page.viewSlots();
  assert.equal(navigated, undefined); assert.equal(page.data.booking, null);
  assert.equal(page.data.error, '预约不存在');
  console.log('P5 rebook: explicit date, owned history, current slot flow and error recovery passed');
})().finally(() => { Module._load = originalLoad; }).catch(error => {console.error(error); process.exitCode = 1;});
