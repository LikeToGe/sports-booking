import assert from "node:assert/strict";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { createServer } from "vite";
import { createSSRApp, h } from "vue";
import { renderToString } from "vue/server-renderer";

test("预约查看不依赖场次或顾客查看权限", async () => {
  const previousStorage = globalThis.localStorage;
  const previousFetch = globalThis.fetch;
  globalThis.localStorage = { getItem: () => null };
  const server = await createServer({
    root: fileURLToPath(new URL("../", import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: "custom",
  });
  try {
    const { auth } = await server.ssrLoadModule("/src/auth.ts");
    const { default: component } = await server.ssrLoadModule("/src/views/BookingsView.vue");
    for (const permissions of [["BOOKING_VIEW"], ["BOOKING_VIEW", "SESSION_VIEW"]]) {
      auth.principal = { role: "VENUE_EMPLOYEE", venueId: 1, permissions };
      const requests = [];
      globalThis.fetch = async url => {
        requests.push(url);
        const bookingRead = url.includes("/bookings/list");
        const allowed = bookingRead || url.includes("/sessions/list") && permissions.includes("SESSION_VIEW");
        return { ok: allowed, status: allowed ? 200 : 403,
          json: async () => allowed ? { code: 0, data: bookingRead ? [{ id: 7 }] : [] }
            : { code: 403, msg: "没有操作权限" } };
      };
      let state;
      await renderToString(createSSRApp({ setup() {
        state = component.setup({}, { expose() {} });
        return () => h("div");
      } }));
      await state.load();
      assert.equal(state.error.value, "");
      assert.deepEqual(state.bookings.value, [{ id: 7 }]);
      assert.equal(requests.some(url => url.includes("/customers/list")), false);
      assert.equal(requests.some(url => url.includes("/sessions/list")), permissions.includes("SESSION_VIEW"));
    }
  } finally {
    await server.close();
    globalThis.fetch = previousFetch;
    if (previousStorage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = previousStorage;
  }
});

test("官方动态发布权限可作为员工登录后的落地页", async () => {
  const previousStorage = globalThis.localStorage;
  globalThis.localStorage = { getItem: () => null };
  const server = await createServer({
    root: fileURLToPath(new URL("../", import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: "custom",
  });
  try {
    const { auth, defaultRoute } = await server.ssrLoadModule("/src/auth.ts");
    auth.principal = { role: "VENUE_EMPLOYEE", venueId: 1, permissions: ["POST_PUBLISH"] };
    assert.equal(defaultRoute(), "/official-posts");
  } finally {
    await server.close();
    if (previousStorage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = previousStorage;
  }
});
