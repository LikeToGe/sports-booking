const assert = require("node:assert/strict");
const path = require("node:path");

let definition;
const storage = new Map([
  ["yuechangApiBase", "http://10.24.0.125:8089/api"],
  ["yuechangMiniToken", "stale-token"],
]);

global.App = value => { definition = value; };
global.wx = {
  getStorageSync: key => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: key => storage.delete(key),
};

const appPath = path.join(__dirname, "..", "app.js");
delete require.cache[require.resolve(appPath)];
require(appPath);

const app = { ...definition, globalData: { ...definition.globalData } };
app.onLaunch();
assert.equal(app.globalData.apiBase, "http://10.24.0.125:8089/api");

app.setApiBase("http://10.24.0.126:8089/api/");
assert.equal(app.globalData.apiBase, "http://10.24.0.126:8089/api");
assert.equal(storage.get("yuechangApiBase"), "http://10.24.0.126:8089/api");
assert.equal(storage.has("yuechangMiniToken"), false);
assert.equal(app.globalData.token, "");

console.log("local API configuration checks passed");
