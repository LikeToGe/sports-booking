// 只验证源码样式的窄屏呈现；原生 picker、手势和跨页合成仍须微信端点验。
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(process.argv[2]);
const root = path.resolve(__dirname, "../../..");
const mini = path.join(root, "miniapp");
const read = file => fs.readFileSync(path.join(mini, file), "utf8");
function css(file, width) {
  return read(file).replace(/@import\s+"([^"]+)";/g, (_, name) => css(path.join(path.dirname(file), name), width))
    .replace(/([\d.]+)rpx/g, (_, size) => `${Number(size) * width / 750}px`)
    .replace(/(?<![-\w.])page(?=\s*[{,])/g, "body").replace(/(?<![-\w.])view(?=[\s,{>])/g, "div");
}
function html(source, data = {}) {
  return source.replace(/\s(?:wx:[\w-]+|bind\w+|catch\w+)=(?:"[^"]*"|'[^']*')/g, "")
    .replace(/\{\{(.*?)\}\}/g, (_, expression) => Function("data", `with(data) { return (${expression}); }`)(data) ?? "")
    .replace(/\sdisabled="false"/g, "")
    .replace(/\s(?:wx:else|enhanced)\b/g, "")
    .replace(/<(\/?)view\b/g, "<$1div").replace(/<(\/?)text\b/g, "<$1span")
    .replace(/<image\b[^>]*\/>/g, "");
}
function feedback(busy, done) {
  const source = read("components/action-feedback/index.wxml").split("\n")
    .filter(line => !(line.includes("action-spinner") && !busy) && !(line.includes("action-check\"") && (!done || busy))).join("\n");
  return html(source, { busy, done, label: "确认预约", busyLabel: "正在创建预约", doneLabel: "预约已创建" });
}
function selection(checked) { return html(read("components/selection-mark/index.wxml"), { checked }); }
function specimen(width) {
  const styles = ["app.wxss", "components/action-feedback/index.wxss", "components/selection-mark/index.wxss", "pages/membership/index.wxss", "pages/content-agreement/index.wxss", "custom-tab-bar/index.wxss"]
    .map(file => css(file, width)).join("\n");
  const plan = read("pages/membership/index.wxml").match(/<button wx:for="{{plans}}"[\s\S]*?<\/button>/)[0];
  const card = html(plan, { selectedPlanId: 1, creatingRecharge: false, payingRecharge: false, cancellingRecharge: false,
    item: { id: 1, name: "晚场常约储值档位", validityLabel: "赠送余额 30 天有效", paidAmount: "¥500.00", giftLabel: "赠 ¥50.00" } })
    .replace(/<selection-mark[^>]*\/>/, `<selection-mark>${selection(true)}</selection-mark>`);
  return `<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>${styles}
    body { margin:0; } div,span,button { box-sizing:border-box; } button { border:0; font-family:inherit; } .page { display:grid; align-content:start; gap:16px; } .fixture-title { font-size:12px; color:var(--muted); } .plan-row > selection-mark { display:flex; } .agreement-check > selection-mark { display:flex; } .fixture-actions { display:grid; gap:12px; } .fixture-actions button { width:100%; } action-feedback { display:block; width:100%; } .is-selected selection-mark { flex-shrink:0; }
    </style><body><main class="page"><span class="screen-title">预约交互</span><span class="fixture-title">源码样式辅助检查 · 非微信运行态</span>
    <div class="fixture-actions"><button class="button-primary">${feedback(false, false)}</button><button class="button-primary is-busy" disabled>${feedback(true, false)}</button><button class="button-primary is-complete" disabled>${feedback(false, true)}</button></div>
    <div class="error" role="alert">当前场次已被预约，请重新选择。</div>
    <div class="agreement-check"><selection-mark>${selection(false)}</selection-mark><span>我已阅读并同意当前版本</span></div>
    <div class="agreement-check is-checked"><selection-mark>${selection(true)}</selection-mark><span>我已阅读并同意当前版本</span></div>
    <div class="membership-page">${card}</div></main></body>`;
}
function wheel(width) {
  const hours = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, "0"));
  const minutes = Array.from({ length: 60 }, (_, i) => String(i).padStart(2, "0"));
  let source = read("components/time-wheel/index.wxml");
  source = source.replace(/<picker-view\b[\s\S]*?<\/picker-view>/, `<div class="wheel-picker fixture-native-picker">${[hours.slice(17, 22), minutes.slice(28, 33)].map((values, column) => `<div>${values.map(item => `<div class="wheel-option">${item}<span>${column ? "分" : "时"}</span></div>`).join("")}</div>`).join("")}<div class="wheel-indicator fixture-indicator"></div></div>`);
  return specimen(width) + `<style>${css("components/time-wheel/index.wxss", width)} .fixture-native-picker { display:flex; position:relative; overflow:hidden; } .fixture-native-picker>div:not(.fixture-indicator) { flex:1; } .fixture-indicator { position:absolute; left:0; right:0; top:88px; pointer-events:none; } .wheel-picker::after { content:""; position:absolute; inset:0; pointer-events:none; background:linear-gradient(white,transparent 40%,transparent 60%,white); }</style>${html(source, { label: "开始时间", value: "19:30", placeholder: "不限", disabled: false, opened: true, spinning: false, hours, minutes, draft: [19, 30] })}`;
}
(async () => {
  const browser = await chromium.launch({ executablePath: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", headless: true });
  const results = [];
  const out = path.join(__dirname, "previews"); fs.mkdirSync(out, { recursive: true });
  for (const width of [320, 375, 390, 414]) {
    const page = await browser.newPage({ viewport: { width, height: 844 } });
    await page.setContent(specimen(width));
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: path.join(out, `controls-${width}.png`), fullPage: true });
    const controls = await page.evaluate(() => ({ overflow: document.documentElement.scrollWidth > innerWidth,
      buttonHeights: [...document.querySelectorAll(".fixture-actions button")].map(button => button.getBoundingClientRect().height),
      selected: getComputedStyle(document.querySelector(".selection-mark.is-checked")).backgroundColor }));
    assert.equal(controls.overflow, false); assert.ok(controls.buttonHeights.every(height => height >= 44));
    assert.equal(controls.selected, "rgb(8, 127, 114)");
    await page.setContent(wheel(width));
    await page.waitForTimeout(260);
    await page.screenshot({ path: path.join(out, `wheel-${width}.png`) });
    const sheet = await page.locator(".wheel-sheet").boundingBox();
    assert.ok(sheet.x >= 0 && sheet.x + sheet.width <= width && sheet.y + sheet.height <= 845);
    results.push({ width, ...controls, sheet });
    await page.setContent(specimen(width));
    await page.evaluate(() => {
      const x = innerWidth - 24, y = innerHeight - 32;
      const radius = Math.ceil(Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y)));
      const overlay = document.createElement("div"); overlay.className = "tab-transition";
      overlay.innerHTML = `<div class="tab-transition-circle" style="left:${x-radius}px;top:${y-radius}px;width:${radius*2}px;height:${radius*2}px"></div>`;
      document.body.append(overlay);
      requestAnimationFrame(() => requestAnimationFrame(() => overlay.classList.add("is-expanded")));
    });
    await page.waitForTimeout(120); await page.screenshot({ path: path.join(out, `circle-middle-${width}.png`) });
    await page.waitForTimeout(220); await page.screenshot({ path: path.join(out, `circle-full-${width}.png`) });
    assert.equal(await page.locator(".tab-transition-circle").evaluate(node => {
      const rect = node.getBoundingClientRect(), radius = rect.width / 2;
      return [0, innerWidth].every(x => [0, innerHeight].every(y => Math.hypot(x - rect.x - radius, y - rect.y - radius) <= radius + 1));
    }), true);
    await page.emulateMedia({ reducedMotion: "reduce" });
    assert.ok(parseFloat(await page.locator(".tab-transition-circle").evaluate(node => getComputedStyle(node).transitionDuration)) <= .001);
    await page.close();
  }
  fs.writeFileSync(path.join(__dirname, "visual-checks.json"), JSON.stringify({ kind: "source-style-fixtures", nativeGesturesVerified: false, results }, null, 2) + "\n");
  await browser.close();
  console.log("4 widths: controls, wheel layout, circular coverage and reduced motion passed (style fixtures only)");
})().catch(error => { console.error(error); process.exit(1); });
