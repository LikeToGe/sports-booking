import assert from "node:assert/strict";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { createServer } from "vite";
import { createSSRApp, h } from "vue";
import { renderToString } from "vue/server-renderer";
import { CircleAlert, CircleCheck, CircleX, Clock3 } from "@lucide/vue";

test("排场文字、图标和颜色保持一致，不被历史收款状态覆盖", async () => {
  const previousStorage = globalThis.localStorage;
  globalThis.localStorage = { getItem: () => null };
  const server = await createServer({
    root: fileURLToPath(new URL("../", import.meta.url)),
    server: { middlewareMode: true },
    appType: "custom",
  });
  try {
    const { default: view } = await server.ssrLoadModule("/src/views/TodayView.vue");
    let presentation;
    await renderToString(createSSRApp({
      setup() {
        presentation = view.setup({}, { expose() {} });
        return () => h("div");
      },
    }));
    const icons = { CircleAlert, CircleCheck, CircleX, Clock3 };
    const cases = [
      [{ status: "OPEN", bookingStatus: "CANCELLED", paymentStatus: "PAID" }, "可预约", "active", "CircleCheck"],
      [{ status: "CLOSED", paymentStatus: "PAID" }, "已关闭", "danger", "CircleX"],
      [{ status: "HELD", paymentStatus: "PAID" }, "支付占位", "warning", "Clock3"],
      [{ status: "BOOKED", bookingSource: "ONLINE", bookingStatus: "CONFIRMED", paymentStatus: "PAID" }, "待核销", "info", "Clock3"],
      [{ status: "BOOKED", bookingSource: "ONLINE", bookingStatus: "CANCELLED", paymentStatus: "PAID" }, "已取消", "danger", "CircleX"],
      [{ status: "BOOKED", bookingSource: "ONLINE", bookingStatus: "NO_SHOW", paymentStatus: "PAID" }, "未到场", "danger", "CircleAlert"],
      [{ status: "BOOKED", bookingSource: "OFFLINE", paymentStatus: "PAID" }, "线下 · 已支付", "active", "CircleCheck"],
      [{ status: "BOOKED", bookingSource: "OFFLINE" }, "线下 · 未记录", "warning", "Clock3"],
    ];
    for (const [slot, label, tone, icon] of cases) {
      assert.equal(presentation.cellLabel(slot), label);
      assert.equal(presentation.cellTone(slot), tone);
      assert.equal(presentation.cellIcon(slot), icons[icon]);
    }
  } finally {
    await server.close();
    if (previousStorage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = previousStorage;
  }
});
