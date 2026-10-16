// 对真实 Vue 路由做隔离浏览器验收；只读已有 8091 测试实例，不接生产。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.argv[2]);
const root = path.resolve(__dirname, '../../..');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'docs/ui-design-courtside/manifest.json')));
const out = path.join(__dirname, 'admin');
const sessions = {
  platform: JSON.parse(fs.readFileSync('/tmp/courtside-admin-session.json')),
  venue: JSON.parse(fs.readFileSync('/tmp/courtside-venue1-session.json')),
};
(async () => {
  const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true });
  const results = [];
  for (const width of [1440, 768, 375, 320]) {
    for (const spec of manifest.filter(p => p.platform === 'admin' && !p.extra)) {
      const ctx = await browser.newContext({ viewport: { width, height: 1000 }, deviceScaleFactor: 1 });
      const session = sessions[spec.scope];
      if (!spec.login) await ctx.addInitScript(session => {
        localStorage.setItem('yuechangAdminToken', session.token);
        localStorage.setItem('yuechangAdminPrincipal', JSON.stringify(session.principal));
      }, session);
      const page = await ctx.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto('http://127.0.0.1:8088' + spec.route);
      await page.waitForLoadState('networkidle');
      await page.evaluate(() => document.fonts.ready);
      const layout = await page.evaluate(() => ({
        overflow: document.documentElement.scrollWidth > innerWidth + 1,
        width: innerWidth, route: location.pathname,
        brokenImages: [...document.images].filter(i => !i.complete || !i.naturalWidth).map(i => i.getAttribute('src')),
        alerts: [...document.querySelectorAll('[role="alert"]')].map(i => i.textContent),
        textLength: document.body.innerText.length,
      }));
      await page.screenshot({ path: path.join(out, `${spec.id}-${width}.png`), fullPage: true });
      results.push({ id: spec.id, ...layout, errors });
      await ctx.close();
    }
  }
  fs.writeFileSync(path.join(__dirname, 'admin-layout.json'), JSON.stringify(results, null, 2));
  await browser.close();
  assert.equal(results.length, 100);
  const failures = results.filter(r => r.overflow || r.errors.length || r.route !== manifest.find(p => p.id === r.id).route);
  console.log(JSON.stringify({ pages: 25, screenshots: results.length, layoutFailures: failures, brokenImagePages: results.filter(r => r.brokenImages.length), alerts: results.filter(r => r.alerts.length) }, null, 2));
  assert.deepEqual(failures, []);
})().catch(error => { console.error(error); process.exitCode = 1; });
