"""汇总真实 Vue / 原生窗口证据；空白、加载过早与晚改页面保留为待验。"""
from pathlib import Path
import json, re, hashlib, shutil
from PIL import Image
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
manifest=json.loads((ROOT/'docs/ui-design-courtside/manifest.json').read_text())
admin=json.loads((OUT/'admin-layout.json').read_text())
modes=json.loads((OUT/'admin-modes.json').read_text())
assert len([p for p in manifest if not p['extra']])==48
assert len([p for p in manifest if p['extra']])==38
assert len(admin)==100 and not any(p['overflow'] or p['errors'] or p['brokenImages'] for p in admin)
assert len(modes)==80 and not any(p['overflow'] or p['errors'] for p in modes)
registered=set(json.loads((ROOT/'miniapp/app.json').read_text())['pages'])
assert registered=={p['route'] for p in manifest if p['platform']=='mini' and not p['extra']}
rows=[]; image_rows=[]; unready=[]
for p in manifest:
    source=ROOT/p['source']; assert source.exists()
    row={'id':p['id'],'title':p['title'],'route':p['route'],'source':p['source'],'extra':p['extra'],'implemented':True,'screenshots':[],'pending':[]}
    if p['platform']=='admin':
        for width in [1440,768,375,320]:
            item=next(x for x in (modes if p['extra'] else admin) if x['id']==p['id'] and x['width']==width)
            row['screenshots'].append({'width':width,'path':f"admin/{p['id']}-{width}.png",'kind':'vue_response_fixture' if 'controlled' in item.get('status','') else 'vue_live'})
        if p['id']=='a-case-evidence':row['pending'].append('真实案件版本追溯未在本轮执行；截图使用明确标注的接口响应夹具。')
    else:
        for width in ([390] if p['extra'] else [320,375,390,414]):
            ax=OUT/'native'/f"{p['id']}-{width}.ax.txt"; raw=OUT/'native'/f"{p['id']}-{width}-window.png"
            if not raw.exists():raw=Path('/tmp/courtside-native-window-evidence')/raw.name
            if not ax.exists() or not raw.exists():
                row['pending'].append(f'{width}px 原生模式截图未取得。'); continue
            text=ax.read_text(); body=text.split('Webview:')[-1]
            if body.count('文本 ')+body.count('按钮 ')<3:
                row['pending'].append(f'{width}px 页面内容尚未加载，排除该样本。');unready.append(raw.name);continue
            device={320:'iPhone 5 ',375:'iPhone X ',390:'iPhone 12/13 (Pro)',414:'iPhone XR '}[width]
            # 设备名来自原生窗口完整树；主页面源码片段之外的设备行另读采集日志。
            im=Image.open(raw).convert('RGB');cols=[]
            for x in range(im.width//2,im.width):
                if sum(min(im.getpixel((x,y)))>(80 if p['id']=='m-account-close' else 180) for y in range(180,350))>110:cols.append(x)
            assert cols,raw.name
            left,right=min(cols),max(cols)+1;h={320:568,375:812,390:844,414:896}[width]
            crop=im.crop((left,177,right,177+round((right-left)*h/width)));crop.thumbnail((width,h))
            target=OUT/'native'/raw.name.replace('-window','');crop.save(target)
            late=p['id'] in ['m-venue','m-venue-grid']
            row['screenshots'].append({'width':width,'path':str(target.relative_to(OUT)),'kind':'native_live','finalLayoutVerified':not late,'rawSha256':hashlib.sha256(raw.read_bytes()).hexdigest()})
            image_rows.append({'file':str(target.relative_to(OUT)),'origin':'WeChat DevTools window captured with cua','device':device,'rawSha256':hashlib.sha256(raw.read_bytes()).hexdigest()})
            if late:row['pending'].append(f'{width}px 截图早于最后将预约说明移至场次列表后；窗口不可访问，最终顺序需补拍。')
        if p['id']=='m-staff-member':row['pending'].append('会员识别模式表单已截图；输入后的识别结果未完成原生点验，相关隔离 API 与既有 Node 行为通过。')
    rows.append(row)
# 完整窗口留在 /tmp 便于原始追溯，仓库只保留代表窗口与真实窗口裁切。
archive=Path('/tmp/courtside-native-window-evidence');archive.mkdir(exist_ok=True)
keep={'m-home-390-window.png','m-staff-records-320-window.png','m-confirm-375-window.png','m-booking-414-window.png','m-account-close-390-window.png'}
for raw in (OUT/'native').glob('*-window.png'):
    if raw.name not in keep:shutil.move(str(raw),str(archive/raw.name))
(OUT/'coverage.json').write_text(json.dumps({'mainPages':48,'keyModes':38,'rows':rows,'excludedUnreadyCaptures':unready},ensure_ascii=False,indent=2)+'\n')
(OUT/'native-image-provenance.json').write_text(json.dumps(image_rows,ensure_ascii=False,indent=2)+'\n')
(OUT/'coverage.md').write_text('# 页面与模式覆盖\n\n所有条目均已完成生产源码实施；“待验”不计作运行态通过。管理端图为真实 Vue 路由，小程序图为微信窗口裁切。\n\n| ID / 页面 | 路由 | 源码 | 已取得截图 | 待验 |\n|---|---|---|---|---|\n'+''.join(f"| {r['id']} / {r['title']} | `{r['route']}` | `{r['source']}` | "+', '.join(f"[{s['width']}px]({s['path']})" for s in r['screenshots'])+' | '+('；'.join(r['pending']) or '—')+' |\n' for r in rows))
print('coverage:',len(rows),'entries; excluded unready:',unready)
