// 用已有 Playwright 与本机 Chrome 渲染并检查图册，无业务接口调用。
const { chromium } = require(process.argv[2] || 'playwright');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const root = __dirname;
const manifest = JSON.parse(fs.readFileSync(path.join(root,'manifest.json'),'utf8'));
const base = pathToFileURL(path.join(root,'index.html')).href;
(async()=>{
  const browser = await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
  const results=[];
  let failures=[];
  await Promise.all(['admin','mini'].map(async platform=>{
    const ctx=await browser.newContext({viewport:{width:platform==='admin'?1440:390,height:platform==='admin'?1000:844},deviceScaleFactor:1});
    const page=await ctx.newPage();
    const errors=[];
    page.on('pageerror',err=>errors.push(err.message));
    for(const p of manifest.filter(x=>x.platform===platform)){
      const oldErrors=errors.length;
      await page.goto(base+'?shot='+p.id,{waitUntil:'load'});
      await page.evaluate(()=>document.fonts.ready);
      const layout=await page.evaluate(()=>({
        overflow:document.documentElement.scrollWidth>window.innerWidth+1,
        width:document.documentElement.scrollWidth,
        height:document.documentElement.scrollHeight,
        brokenImages:[...document.images].filter(i=>!i.complete||i.naturalWidth===0).length,
        title:document.title,
        galleryChrome:!!document.querySelector('.gallery-header'),
        canvasCount:document.querySelectorAll('.admin-main,.mobile,.login').length,
        text:document.body.innerText.length,
        overlay:!!document.querySelector('.dialog-backdrop,.drawer-backdrop')
      }));
      const screenshot=path.join(root,p.preview);
      await page.screenshot({path:screenshot,fullPage:!layout.overlay});
      const pass=!layout.galleryChrome&&layout.canvasCount===1&&!layout.overflow&&layout.brokenImages===0&&layout.text>35&&errors.length===oldErrors;
      const r={id:p.id,title:p.title,pass,viewport:[p.width,p.height],...layout,jsErrors:errors.slice(oldErrors)};
      results.push(r);if(!pass)failures.push(r);
    }
    await ctx.close();
  }));
  const ctx=await browser.newContext({viewport:{width:1440,height:1000}});
  const page=await ctx.newPage();
  const galleryErrors=[];page.on('pageerror',e=>galleryErrors.push(e.message));
  await page.goto(base+'?page=a-today');
  await page.locator('#mini-switch').click();
  await page.locator('#title').filter({hasText:'订场首页'}).waitFor();
  const frame=page.frameLocator('#frame');
  await frame.getByRole('button',{name:'查看场次',exact:true}).click();
  await page.locator('#title').filter({hasText:'场馆与场次'}).waitFor();
  await frame.getByRole('button',{name:'预约 18:00–19:00　¥60',exact:true}).click();
  await page.locator('#title').filter({hasText:'确认预约'}).waitFor();
  await frame.getByRole('button',{name:'创建预约　¥60.00',exact:true}).click();
  await page.locator('#title').filter({hasText:'预约支付'}).waitFor();
  await frame.getByRole('button',{name:'完成 Mock 支付',exact:true}).click();
  await page.locator('#title').filter({hasText:'预约支付 · 已支付'}).waitFor();
  await page.locator('#admin-switch').click();
  await page.frameLocator('#frame').getByRole('button',{name:'登记线下预约',exact:true}).click();
  await page.locator('#title').filter({hasText:'预约管理 · 创建线下预约'}).waitFor();
  await page.frameLocator('#frame').getByRole('button',{name:'关闭',exact:true}).click();
  await page.locator('#title').filter({hasText:'预约管理'}).waitFor();
  await page.locator('#search').fill('场馆资料');
  const searchPass=(await page.locator('#catalog [data-page]').count())===1;
  await page.locator('#search').fill('');
  await page.locator('#overview-switch').click();
  const adminOverview=(await page.locator('.overview-card').count())===25;
  await page.locator('#mini-switch').click();await page.locator('#overview-switch').click();
  const miniOverview=(await page.locator('.overview-card').count())===23;
  await page.setViewportSize({width:768,height:1000});
  const gallery768=await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1);
  await page.setViewportSize({width:390,height:844});
  const gallery390=await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1);
  // 在较窄画板上重复检查每一个小程序设计。
  const narrow=[];
  await page.setViewportSize({width:320,height:844});
  for(const p of manifest.filter(x=>x.platform==='mini')){
    await page.goto(base+'?shot='+p.id);
    const ok=await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1);
    narrow.push({id:p.id,pass:ok});if(!ok)failures.push({id:p.id,viewport:[320,844],reason:'root overflow'});
  }
  const summary={date:'2026-10-14',rendered:results.length,mainAdmin:25,mainMini:23,extra:manifest.filter(p=>p.extra).length,pass:failures.length===0&&galleryErrors.length===0&&searchPass&&adminOverview&&miniOverview&&gallery768&&gallery390,failures,gallery:{errors:galleryErrors,bookingFlow:true,offlineModalOpenClose:true,searchPass,adminOverview,miniOverview,at768:gallery768,at390:gallery390},screens:results.sort((a,b)=>a.id.localeCompare(b.id)),miniAt320:narrow};
  fs.writeFileSync(path.join(root,'verification.json'),JSON.stringify(summary,null,2));
  await browser.close();
  console.log(JSON.stringify({rendered:summary.rendered,pass:summary.pass,failures:summary.failures,gallery:summary.gallery},null,2));
  if(!summary.pass)process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});
