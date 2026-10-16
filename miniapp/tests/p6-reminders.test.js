const assert = require('node:assert/strict');
const Module = require('node:module');
const path = require('node:path');

const originalLoad = Module._load;
const calls = [];
const navigation = [];
let definition;
let registrations = [{ id: 5, venueId: 1, sportTypeId: 2, targetDate: '2026-10-15',
  startTime: '19:00:00', endTime: '21:00:00', type: 'RELEASED', status: 'ACTIVE',
  bookingDaysAhead: 2, minBookingMinutes: 30 }];
Module._load = function (name, parent, main) {
  if (name === '../../utils/api' && parent?.filename.includes('/pages/')) {
    return { dateInZhengzhou: () => '2026-10-13', time: value => value.slice(11, 16),
      dateTime: value => value, request: async (url, options) => {
        calls.push([url, options]);
        if (url === '/mini/sports') return [{ id: 2, name: '羽毛球' }];
        if (url === '/mini/venues') return [{ id: 1, name: '测试馆' }];
        if (url.startsWith('/mini/venues/1?date=')) return { venue: { bookingDaysAhead: 2, minBookingMinutes: 30 } };
        if (url === '/mini/reminder-subscriptions' && options?.method === 'POST') return { id: 5, availableNow: true };
        if (url === '/mini/reminder-subscriptions') return registrations;
        if (url.startsWith('/public/venues/1/slot-comparison')) return [
          { id: 9, courtName: '一号场', startAt: '2026-10-15T19:00:00+08:00', endAt: '2026-10-15T20:00:00+08:00', status: 'BOOKED' },
          { id: 10, courtName: '二号场', startAt: '2026-10-15T20:00:00+08:00', endAt: '2026-10-15T21:00:00+08:00', status: 'OPEN' },
        ];
        if (url === '/mini/reminder-subscriptions/5' && options?.method === 'DELETE') {
          registrations = [{ ...registrations[0], status: 'CANCELLED' }]; return true;
        }
        if (url === '/public/ugc-config') return false;
        if (url === '/mini/content/notifications') return [{ id: 20, title: '有空位了', businessType: 'SESSION_SLOT',
          businessId: 9, subscriptionId: 5, type: 'SLOT_RELEASED_ALERT' }];
        if (url === '/mini/content/notifications/20/read') return true;
        throw new Error(`unexpected request ${url}`);
      } };
  }
  return originalLoad.call(this, name, parent, main);
};
global.Page = page => { definition = page; };
global.wx = { navigateTo: value => navigation.push(value.url) };

function page(relative) {
  const file = path.join(__dirname, '..', 'pages', relative, 'index.js');
  delete require.cache[require.resolve(file)];
  require(file);
  const instance = { ...definition, data: structuredClone(definition.data) };
  instance.setData = values => Object.assign(instance.data, values);
  return instance;
}

async function main() {
  const reminders = page('reminders');
  reminders.onLoad({ subscriptionId: '5', slotId: '9' });
  await reminders.onShow();
  assert.equal(reminders.data.dateMax, '2026-10-15', '只允许当前窗口和下一开放日期');
  assert.match(reminders.data.rows[0].ruleText, /预计 2026-10-14 进入预约窗口/);
  assert.match(reminders.data.supplyNotice, /原空位已预约/);
  assert.equal(reminders.data.supply.find(row => row.id === 10).statusLabel, '可预约');
  reminders.openVenue({ currentTarget: { dataset: {} } });
  assert.match(navigation.at(-1), /date=2026-10-15&sportTypeId=2&startTime=19:00&endTime=21:00&entryType=REMINDER_ENTRY/);
  await reminders.save();
  assert.equal(calls.find(([url, options]) => url === '/mini/reminder-subscriptions' && options?.method === 'POST')[1].data.type,
    'RELEASED');
  assert.match(reminders.data.message, /已有可预约/);
  await reminders.cancel({ currentTarget: { dataset: { id: 5 } } });
  assert.equal(reminders.data.rows[0].status, 'CANCELLED');

  registrations = [{ ...registrations[0], startTime: '19:00:30', endTime: '21:00:00.001' }];
  await reminders.load();
  assert.equal(reminders.data.startTime, '19:00:30', '遗留非零秒不能截断到分钟');
  assert.equal(reminders.data.endTime, '21:00:00.001', '遗留小数秒不能截断到分钟');
  assert.match(calls.findLast(([url]) => url.startsWith('/public/venues/1/slot-comparison'))[0],
    /startTime=19%3A00%3A30&endTime=21%3A00%3A00.001/);
  reminders.openVenue({ currentTarget: { dataset: {} } });
  assert.match(navigation.at(-1), /startTime=19:00:30&endTime=21:00:00.001/,
    '消息回流保留原条件，不静默扩展可订范围');

  registrations = [{ ...registrations[0], venueId: 99, sportTypeId: 99 }];
  await reminders.load();
  assert.equal(reminders.data.venueIndex, -1, '停用馆不能错选成第一家活跃馆');
  assert.equal(reminders.data.sportIndex, -1);
  assert.match(reminders.data.ruleText, /当前不可预约/);
  const writes = calls.filter(([url, options]) => url === '/mini/reminder-subscriptions' && options?.method === 'POST').length;
  await reminders.save();
  assert.equal(calls.filter(([url, options]) => url === '/mini/reminder-subscriptions' && options?.method === 'POST').length,
    writes, '失效馆不能误登记到其他场馆');

  const messages = page('community');
  await messages.load();
  assert.equal(messages.data.mode, 'messages', 'UGC 关闭仍可读取提醒消息');
  await messages.openMessage({ currentTarget: { dataset: { id: 20 } } });
  assert.equal(navigation.at(-1), '/pages/reminders/index?subscriptionId=5&slotId=9');
  console.log('P6 reminder registration, current supply and message landing checks passed');
}

main().finally(() => { Module._load = originalLoad; }).catch(error => {
  console.error(error);
  process.exitCode = 1;
});
