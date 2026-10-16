"""将已导出的页面排成总览；完整页面仍保留在独立 PNG 中。"""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parent
pages=json.loads((OUT/'manifest.json').read_text())
font_path='/System/Library/Fonts/STHeiti Medium.ttc'
def font(size): return ImageFont.truetype(font_path,size)
def overview(platform):
    items=[p for p in pages if p['platform']==platform and not p['extra']]
    cols=3 if platform=='admin' else 5
    tw,th=(510,354) if platform=='admin' else (232,502)
    pad,gap,caption=32,24,65
    width=pad*2+cols*tw+(cols-1)*gap
    rows=math.ceil(len(items)/cols)
    height=140+rows*(th+caption+gap)+pad
    canvas=Image.new('RGB',(width,height),'#F1F6F2'); d=ImageDraw.Draw(canvas)
    d.text((pad,28),'管理端 · 全页面设计总览' if platform=='admin' else '小程序 · 全页面设计总览',fill='#243936',font=font(32))
    d.text((pad,80),f'{len(items)} 个主页面 / 场边工作台 / 独立 PNG 与离线图册可查看完整内容',fill='#5F7369',font=font(16))
    for n,p in enumerate(items):
        x=pad+(n%cols)*(tw+gap); y=140+(n//cols)*(th+caption+gap)
        source=Image.open(OUT/p['preview']).convert('RGB')
        # 总览显示首屏，完整长图通过图册打开。
        crop_h=min(source.height,round(source.width*th/tw))
        tile=source.crop((0,0,source.width,crop_h))
        tile.thumbnail((tw,th),Image.Resampling.LANCZOS)
        d.rounded_rectangle((x-1,y-1,x+tw+1,y+th+caption+1),radius=8,fill='white',outline='#D8E5DC')
        canvas.paste(tile,(x,y))
        d.text((x+11,y+th+11),p['title'],fill='#243936',font=font(18 if platform=='admin' else 15))
        d.text((x+11,y+th+39),p['route'],fill='#5F7369',font=font(11 if platform=='admin' else 9))
    filename=OUT/(platform+'-overview.png');canvas.save(filename)
    return filename

def comparison():
    canvas=Image.new('RGB',(1860,1110),'#F1F6F2');d=ImageDraw.Draw(canvas)
    d.text((38,28),'约场 · 场边工作台',fill='#243936',font=font(35))
    d.text((38,80),'管理端与小程序共享青绿、白色与冷灰；按各自任务组织页面',fill='#5F7369',font=font(19))
    for filename,x,y,w,h in [('a-today.png',35,185,1370,880),('m-home.png',1450,145,370,950)]:
        im=Image.open(OUT/'previews'/filename).convert('RGB')
        crop_h=min(im.height,round(im.width*h/w));im=im.crop((0,0,im.width,crop_h));im.thumbnail((w,h),Image.Resampling.LANCZOS)
        d.rounded_rectangle((x-1,y-1,x+w+1,y+h+1),radius=8,fill='white',outline='#D8E5DC');canvas.paste(im,(x,y))
    d.text((35,143),'管理端 / 今日运营',fill='#243936',font=font(20))
    d.text((1450,113),'小程序 / 订场首页',fill='#243936',font=font(18))
    canvas.save(OUT/'dual-preview.png')

assert len([p for p in pages if not p['extra']])==48
for p in pages: assert (OUT/p['preview']).is_file(),p['id']
for platform in ['admin','mini']:print(overview(platform))
comparison()
