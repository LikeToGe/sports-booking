const assert = require('node:assert/strict');
const Module = require('node:module');
const path = require('node:path');

const originalLoad = Module._load;
const calls = [];
let definition;
let favourites = [{ id: 2, name: '已收藏馆', district: '金水区', address: '测试路', status: 'ACTIVE' }];
Module._load = function (name, parent, main) {
  if (name === '../../utils/api' && parent?.filename.includes('/pages/')) {
    return {
      assetUrl: value => value,
      request: async (url, options) => {
        calls.push([url, options]);
        if (url === '/mini/favourite-venues') return favourites;
        if (url === '/mini/recent-venues') return [{ id: 3, name: '暂停馆', address: '另一条路', status: 'SUSPENDED' }];
        if (url === '/mini/favourite-venues/4' && options?.method === 'POST') {
          favourites = [...favourites, { id: 4, name: '当前馆', status: 'ACTIVE' }];
          return true;
        }
        throw new Error(`unexpected request ${url}`);
      },
    };
  }
  return originalLoad.call(this, name, parent, main);
};
global.Page = page => { definition = page; };
global.wx = { navigateTo: () => {} };

function load() {
  const file = path.join(__dirname, '..', 'pages', 'favourites', 'index.js');
  delete require.cache[require.resolve(file)];
  require(file);
  const page = { ...definition, data: structuredClone(definition.data) };
  page.setData = values => Object.assign(page.data, values);
  return page;
}

async function main() {
  const page = load();
  await page.onLoad({ venueId: '4' });
  assert.equal(page.data.targetIsFavourite, false);
  await page.addTarget();
  assert.equal(page.data.targetIsFavourite, true);
  assert.equal(calls.filter(([url]) => url === '/mini/favourite-venues/4').length, 1);
  assert.equal(page.data.recent[0].status, 'SUSPENDED');
  console.log('P5 favourites page add, reload and suspended display checks passed');
}

main().finally(() => { Module._load = originalLoad; }).catch(error => {
  console.error(error);
  process.exitCode = 1;
});
