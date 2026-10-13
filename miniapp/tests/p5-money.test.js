const assert = require('node:assert/strict');
const {money} = require('../utils/api');
assert.equal(money(5050), '¥50.50', '搜索到预约详情必须保留同一个整数分价格');
assert.equal(money(1), '¥0.01');
assert.equal(money(0), '¥0.00');
console.log('P5 monetary display preserves cents passed');
