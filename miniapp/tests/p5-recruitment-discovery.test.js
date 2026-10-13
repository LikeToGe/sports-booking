const assert = require('node:assert/strict');
const Module = require('node:module');
const path = require('node:path');
const fs = require('node:fs');
const { dateTime, time } = require('../utils/api');

const originalLoad = Module._load;
let definition;
let recruitments = [{ recruitmentId: 1, postId: 2, startAt: '2026-10-13T16:27:00Z',
  endAt: '2026-10-13T17:27:00Z', levelTag: 'CASUAL', feeMode: 'FREE',
  approvedCount: 0, maxMembers: 2, remainingCount: 2 }];
let failRecruitments = false;
const queuedRecruitments = [];
const calls = [];
Module._load = function (name, parent, main) {
  if (name === '../../utils/api' && parent?.filename.includes('/pages/posts/')) {
    return { dateTime, time, uploadFile: async () => ({ id: 1 }), request: async url => {
      calls.push(url);
      if (url === '/mini/sports' || url === '/mini/venues' || url === '/mini/bookings/list') return [];
      if (url === '/mini/posts/list') return [];
      if (url.startsWith('/mini/recruitments')) {
        if (queuedRecruitments.length) return queuedRecruitments.shift().promise;
        if (failRecruitments) throw new Error('网络失败');
        return recruitments;
      }
      if (url.startsWith('/mini/content/events?')) return true;
      throw new Error(`unexpected request ${url}`);
    } };
  }
  return originalLoad.call(this, name, parent, main);
};
global.Page = page => { definition = page; };
global.getApp = () => ({ globalData: {} });
const navigated = [];
global.wx = { navigateTo: value => navigated.push(value.url) };

async function main() {
  const template = fs.readFileSync(path.join(__dirname, '..', 'pages', 'posts', 'index.wxml'), 'utf8');
  assert.match(template, /wx:if="\{\{enabled && mode === 'recruitments'\}\}"/,
    'UGC 关闭时不能展示找搭子旧卡片');
  const file = path.join(__dirname, '..', 'pages', 'posts', 'index.js');
  delete require.cache[require.resolve(file)];
  require(file);
  const page = { ...definition, data: structuredClone(definition.data) };
  page.setData = values => Object.assign(page.data, values);
  page.onLoad();
  await page.onShow();
  page.switchMode({ currentTarget: { dataset: { mode: 'recruitments' } } });
  await page.loadRecruitments();
  assert.equal(page.data.recruitments.length, 1);
  assert.equal(page.data.recruitments[0].startLabel, '2026-10-14 00:27');
  assert.equal(page.data.recruitments[0].endLabel, '01:27');
  page.openRecruitment({ currentTarget: { dataset: { postId: 2 } } });
  assert.equal(navigated.at(-1), '/pages/posts/detail?id=2');
  assert.ok(calls.some(url => url.includes('eventType=RECRUITMENT_DISCOVERY_OPEN&targetType=POST&targetId=2')));

  recruitments = [{ ...recruitments[0], recruitmentId: 3 }];
  await page.onShow();
  assert.equal(page.data.recruitments[0].recruitmentId, 3, '前台刷新当前找搭子视图');
  assert.ok(calls.filter(url => url.startsWith('/mini/recruitments')).length >= 2);

  failRecruitments = true;
  await page.loadRecruitments();
  assert.deepEqual(page.data.recruitments, [], '失败时不能显示过期卡片');
  assert.equal(page.data.error, '网络失败');
  failRecruitments = false;
  await page.retryLoad();
  assert.equal(page.data.recruitments.length, 1);
  assert.equal(page.data.error, '');

  const pending = () => {
    let resolve;
    const promise = new Promise(done => { resolve = done; });
    return { promise, resolve };
  };
  const oldRequest = pending();
  const newRequest = pending();
  queuedRecruitments.push(oldRequest, newRequest);
  const first = page.loadRecruitments();
  const second = page.loadRecruitments();
  newRequest.resolve([{ ...recruitments[0], recruitmentId: 4 }]);
  await second;
  oldRequest.resolve([{ ...recruitments[0], recruitmentId: 5 }]);
  await first;
  assert.equal(page.data.recruitments[0].recruitmentId, 4, '旧筛选响应不能覆盖新结果');
  assert.equal(page.data.loading, false);
  console.log('P5 recruitment refresh, failure and retry checks passed');
}

main().finally(() => { Module._load = originalLoad; }).catch(error => {
  console.error(error);
  process.exitCode = 1;
});
