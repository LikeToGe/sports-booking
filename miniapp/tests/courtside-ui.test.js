const assert = require("node:assert/strict");
const path = require("node:path");
const vm = require("node:vm");
const fs = require("node:fs");
const test = require("node:test");

function page(file, api) {
  let definition;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), {
    Page: value => { definition = value; },
    require: name => { assert.equal(name, "../../utils/api"); return api; },
  });
  return { ...definition, data: structuredClone(definition.data), setData(values) { Object.assign(this.data, values); } };
}

test("收起首页筛选保留条件、结果和待提交标记", () => {
  const home = page("pages/home/index.js", { dateInZhengzhou: () => "2026-10-14" });
  assert.equal(home.data.showFilters, false);
  Object.assign(home.data, { startTime: "18:00", endTime: "20:00", minPrice: "0.29", filtersDirty: true, slots: [{ id: 1 }] });
  home.toggleFilters(); home.toggleFilters();
  assert.equal(home.data.showFilters, false);
  assert.equal(home.data.startTime, "18:00");
  assert.equal(home.data.minPrice, "0.29");
  assert.equal(home.data.filtersDirty, true);
  assert.equal(home.data.slots[0].id, 1);
});

test("动态图片失败保留正文，旧图片下载不覆盖较新筛选", async () => {
  let finishOld;
  let markStarted;
  const started = new Promise(resolve => { markStarted = resolve; });
  let calls = 0;
  const feed = page("pages/posts/index.js", {
    request: async () => ++calls === 1 ? [{ id: 1, body: "旧筛选", media: [{ mediaType: "IMAGE", url: "/old" }] }]
      : [{ id: 2, body: "新筛选", media: [{ mediaType: "IMAGE", url: "/new" }] }],
    downloadMedia: url => url === "/old" ? new Promise(resolve => { finishOld = resolve; markStarted(); }) : Promise.reject(new Error("媒体不可见")),
  });
  const oldLoad = feed.load();
  await started;
  await feed.load();
  assert.equal(feed.data.posts[0].body, "新筛选");
  assert.equal(feed.data.posts[0].previewError, true);
  assert.equal(feed.data.error, "");
  finishOld("temporary-old-image");
  await oldLoad;
  assert.equal(feed.data.posts[0].id, 2);
  assert.equal(feed.data.loading, false);
});

test("五个 Tab 页面在原生组件就绪后刷新稳定路径选中态", () => {
  for (const name of ["home", "bookings", "membership", "mine", "posts"]) {
    let definition;
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, "..", "pages", name, "index.js"), "utf8"), {
      Page: value => { definition = value; },
      require: () => ({ dateInZhengzhou: () => "2026-10-14" }),
    });
    let refreshes = 0;
    definition.onReady.call({ getTabBar: () => ({ refresh: () => { refreshes++; } }) });
    assert.equal(refreshes, 1, name);
  }
});
