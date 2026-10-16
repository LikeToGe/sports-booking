const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.argv[2]);
const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, '../../ui-design-courtside/manifest.json')));
const sessions = { platform: JSON.parse(fs.readFileSync('/tmp/courtside-admin-session.json')), venue: JSON.parse(fs.readFileSync('/tmp/courtside-venue1-session.json')) };
const api = async (scope, url, method='GET', data) => {
  const r = await fetch('http://127.0.0.1:8091/api'+url, { method, headers: {'X-Admin-Token':sessions[scope].token,'Content-Type':'application/json'}, body:data && JSON.stringify(data) });
  const body=await r.json(); assert.equal(body.code,0,body.msg);return body.data;
};
(async()=>{
  const roles = await api('venue','/venue-admin/roles');
  let role;
  if (!roles.length) role = await api('venue','/venue-admin/roles','POST',{name:'场边 UI 点验角色',status:'ACTIVE',permissions:['DASHBOARD_VIEW']});
  const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
  const results=[];
  try {
    for (const width of [1440,768,375,320]) for (const spec of manifest.filter(p=>p.platform==='admin'&&p.extra)) {
      const ctx=await browser.newContext({viewport:{width,height:1000}});
      if(!spec.login) await ctx.addInitScript(s=>{localStorage.setItem('yuechangAdminToken',s.token);localStorage.setItem('yuechangAdminPrincipal',JSON.stringify(s.principal));},sessions[spec.scope || "venue"]);
      const page=await ctx.newPage();page.setDefaultTimeout(10000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
      if(spec.id==='a-case-evidence') {
        const record={id:300001,userId:990011,targetType:'POST',targetId:1,reason:'OTHER',description:'隔离 UI 布局夹具：长文本、提交版本与当前版本分别展示。',status:'PENDING',createdAt:'2026-10-14T08:00:00'};
        await page.route('**/api/platform/content-governance/reports**',route=>route.fulfill({json:{code:0,msg:'UI test fixture',data:route.request().url().endsWith('/evidence')?{caseRecord:record,evidenceSource:'REPORT_SUBMISSION',evidenceRevisionId:300001,currentStatus:'PUBLISHED',currentRevisionId:300001,revisions:[{revisionId:300001,revisionNo:1,body:'这是接口响应夹具，用于验证真实 Vue 证据抽屉在长文本和窄屏下的布局。'.repeat(8),moderationStatus:'APPROVED',createdAt:'2026-10-14T08:00:00',purgedMediaCount:0}],decisions:[]}: [record]}}));
        await page.route('**/api/platform/content-media/POST/300001',route=>route.fulfill({json:{code:0,msg:'UI test fixture',data:[]}}));
      }
      let release;
      if(spec.id==='a-state-loading') await page.route('**/api/venue-admin/bookings/list?*',async route=>{await new Promise(r=>{release=r;});await route.continue();});
      if(spec.id==='a-state-error') await page.route('**/api/venue-admin/bookings/list?*',r=>r.abort());
      await page.goto('http://127.0.0.1:8088'+spec.route);
      if(spec.id!=='a-state-loading') await page.waitForLoadState('networkidle');
      let status=spec.id==='a-case-evidence'?'rendered: controlled UI response fixture':'rendered';
      if(spec.state==='扫码登录') await page.getByRole('tab',{name:'员工扫码',exact:true}).click();
      else if(spec.state==='创建线下预约') await page.getByRole('button',{name:'线下预约',exact:true}).click();
      else if(spec.state==='批量线下预约') await page.getByRole('button',{name:'批量预约',exact:true}).click();
      else if(spec.state==='保存收款状态') await page.getByRole('button',{name:'收款状态',exact:true}).first().click();
      else if(spec.state==='场次操作') await page.locator(width <= 640 ? '.mobile-session' : '.slot-cell').first().click();
      else if(spec.state==='顾客详情') await page.getByRole('button',{name:'查看与标签',exact:true}).first().click();
      else if(spec.state==='编辑角色') await page.getByRole('button',{name:'编辑角色',exact:true}).first().click();
      else if(spec.state==='编辑场馆') await page.getByRole('button',{name:'编辑',exact:true}).first().click();
      else if(spec.state==='案件证据') {
        await page.locator('.pending-filter input').uncheck(); await page.waitForLoadState('networkidle');
        const button=page.getByRole('button',{name:'查看案件证据',exact:true}).first();
        if(await button.count()) await button.click();else status='blocked: 隔离实例无可见案件记录';
      }
      else if(spec.state==='空列表') await page.locator('input[type=search]').fill('UI验收不存在的预约关键词');
      else if(!['加载','失败'].includes(spec.state)) await page.getByRole('tab',{name:spec.state,exact:true}).click();
      if(spec.id!=='a-state-loading') await page.waitForLoadState('networkidle');
      if(spec.id==='a-case-evidence') { await page.locator('.evidence-body').waitFor(); assert.ok((await page.locator('.evidence-body').innerText()).includes('接口响应夹具')); }
      if(await page.locator('dialog[open]').count()) {
        await page.keyboard.press('Tab');
        const focus=await page.evaluate(()=>({tag:document.activeElement.tagName,inside:!!document.activeElement.closest('dialog[open]'),modal:!!document.querySelector('dialog:modal'),outer:document.activeElement.outerHTML.slice(0,180)})); assert.ok(focus.inside || focus.modal && focus.tag === 'BODY', '模态键盘焦点不能进入背景控件');
        // 仅有关闭按钮时，原生 Tab 可暂移到浏览器 chrome；反向 Tab 回到弹窗。
        if(!focus.inside) { await page.keyboard.press('Shift+Tab'); assert.ok(await page.evaluate(()=>!!document.activeElement.closest('dialog[open]'))); }
      }
      const info=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth+1,dialog:!!document.querySelector('dialog[open]'),focusVisible:!!document.activeElement?.matches(':focus-visible'),alerts:[...document.querySelectorAll('[role=alert]')].map(e=>e.textContent)}));
      await page.screenshot({path:path.join(__dirname,'admin',`${spec.id}-${width}.png`),fullPage:!info.dialog});
      if(release)release();
      if(info.dialog) { await page.keyboard.press('Escape');assert.equal(await page.locator('dialog[open]').count(),0); }
      results.push({id:spec.id,width,status,...info,errors});await ctx.close();
    }
    // 浏览器权限门禁只检查路由与入口；后端权限仍由既有接口验证。
    const ctx=await browser.newContext({viewport:{width:375,height:900}});
    await ctx.addInitScript(s=>{localStorage.setItem('yuechangAdminToken',s.token);localStorage.setItem('yuechangAdminPrincipal',JSON.stringify({...s.principal,role:'VENUE_EMPLOYEE',permissions:['DASHBOARD_VIEW']}));},sessions.venue);
    const page=await ctx.newPage();await page.goto('http://127.0.0.1:8088/bookings');await page.waitForLoadState('networkidle');assert.equal(new URL(page.url()).pathname,'/today');
    await page.getByRole('button',{name:'打开导航',exact:true}).click();assert.equal(await page.getByRole('link',{name:'预约管理',exact:true}).count(),0);await page.getByRole('link',{name:'今日运营',exact:true}).click();await ctx.close();
  } finally {
    await browser.close();if(role?.id) await api('venue',`/venue-admin/roles/${role.id}`,'DELETE');
  }
  fs.writeFileSync(path.join(__dirname,'admin-modes.json'),JSON.stringify(results,null,2));
  const failures=results.filter(r=>r.overflow||r.errors.length);
  console.log(JSON.stringify({screenshots:results.length,failures,blocked:results.filter(r=>r.status.startsWith('blocked'))},null,2));assert.deepEqual(failures,[]);
})().catch(e=>{console.error(e);process.exitCode=1});
