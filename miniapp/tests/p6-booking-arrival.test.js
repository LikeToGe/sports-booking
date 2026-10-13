const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
let definition, location;
Module._load = function(name, parent, main) {
  if (name === '../../utils/api' && parent?.filename.includes('/pages/booking/')) return {
    money: value => String(value), date: value => value, time: value => value, dateTime: value => value,
    request: async path => path === '/public/ugc-config' ? false : {
      booking: {id: 7, venueName: '预约快照名称', status: 'CANCELLED', amountFen: 5050},
      venue: {name: '当前场馆名称', latitude: 34.7, longitude: 113.7, address: '本地测试地址', entryNotice: '穿运动鞋'},
    },
  };
  return originalLoad.call(this, name, parent, main);
};
global.Page = page => {definition = page;};
global.wx = {openLocation: value => {location = value;}};
require('../pages/booking/index.js');
const page = {...definition, data: structuredClone(definition.data), setData(value) {Object.assign(this.data, value);}};
(async () => {
  await page.onLoad({id: '7'});
  assert.equal(page.data.booking.venueName, '预约快照名称');
  assert.equal(page.data.venue.entryNotice, '穿运动鞋');
  page.openMap();
  assert.equal(location.latitude, 34.7); assert.equal(location.longitude, 113.7);
  page.data.venue = null; location = undefined; page.openMap(); assert.equal(location, undefined);
  console.log('P6 owned booking navigation and current entry notice preserve booking snapshot passed');
})().finally(() => {Module._load = originalLoad;}).catch(error => {console.error(error); process.exitCode=1;});
