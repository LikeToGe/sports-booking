# Design — 约场

这是“约场”微信小程序与管理后台共用的设计系统。两端共享品牌、颜色、字体、控件状态和文案口径，但根据使用场景采用不同密度。

## Genre

- 用户小程序：`playful`，明亮、直接、以真实场馆和可预约时间为主。
- 管理后台：`modern-minimal`，安静、任务优先、适合反复操作。

## Macrostructure Family

- 用户小程序：`Ecosystem Index`。首页通过运动、日期和场馆形成多个发现入口；场馆图片是第一视觉信号。
- 管理后台：`Index-First`。今日场次、预约、场地、排期等对象直接成为导航，不使用营销式 Hero 或指标墙。
- 内容页：不建立独立家族；资料与规则使用窄栏表单。

## Theme

- Paper：`oklch(98% 0.004 250)`，中性冷白，避免后台大面积泛绿。
- Ink：`oklch(21% 0.018 250)`，偏冷的近黑。
- Accent：`oklch(52% 0.155 145)`，草地绿，用于主操作、选中与可预约状态。
- Signal：`oklch(53% 0.17 31)`，珊瑚橙，仅用于价格、临近时间和少量重点。
- Focus：`oklch(16% 0.08 250)`，在浅色页面和草绿控件上保持清晰；深侧栏改用 Paper 焦点环。

## Typography

- Wordmark：`Songti SC` 700，只用于“约场”文字标识。
- Body / UI：`PingFang SC` 400；Windows 回退 `Microsoft YaHei`。
- Numeric：平台数字字体，所有时间、价格和数量启用 `tabular-nums`。
- 字距固定为 `0`；正文不小于 `14px`，小程序正文默认 `16px`。

## Spacing And Shape

- 4pt 间距体系，以根 [tokens.css](./tokens.css) 为准。
- 卡片最大圆角 `8px`，表单 `6px`，小型状态块 `4px`。
- 不使用卡片套卡片、渐变、玻璃效果、装饰光斑或大面积强调色。

## Motion

- `120ms` 按压、`220ms` 菜单与状态、`360ms` 模态框。
- 仅动画 `transform` 与 `opacity`；焦点环立即出现。
- `prefers-reduced-motion` 下空间移动退化为不超过 `150ms` 的透明度变化。

## Microinteractions

- 成功结果在当前界面直接呈现，不弹“成功”Toast。
- 错误就近显示，并通过 `aria-live` 或小程序等价能力播报。
- 按钮、输入、选择和列表项覆盖默认、按压、禁用、加载、错误与成功状态。
- Web 控件触达面积至少 `44 × 44px`；小程序同样遵守。

## Navigation

- 小程序底部固定三个入口：找场馆、我的预约、我的。
- 员工核销从“我的”进入，只对已绑定且启用的员工显示。
- 后台桌面使用固定侧栏；窄屏折叠为顶部菜单，业务内容不横向滚动。

## Data Presentation

- 今日场次：按时间顺序排列，待核销与即将开始优先。
- 预约：桌面表格，手机列表；状态必须有文字，不只依赖颜色。
- 排期：按场地进入，周一至周日标签切换，场次使用行式编辑。
- 经营报表：筛选区、必要汇总指标和一张按日明细表；平台可额外筛选场馆。
- 首页场馆：图片主导的纵向列表，展示运动、区域、起价和最近可约时间。

## What Pages Must Share

- “约场”文字标识、草绿主色、珊瑚信号色和焦点色。
- 按钮高度、输入高度、焦点环、错误结构和状态命名。
- 日期按郑州本地时间展示，价格统一使用人民币元。

## What Pages May Differ

- 小程序允许场馆封面占据主要视觉面积；后台不使用装饰图片。
- 后台密度高于小程序，但不降低触达面积和正文字号。
- 平台管理员与场馆管理员共享后台框架，导航项按固定角色变化。

## Exports

### tokens.css

根目录 [tokens.css](./tokens.css) 是完整 CSS 导出和 Web 端唯一令牌来源。

### Tailwind v4

```css
@theme {
  --color-paper: oklch(98% 0.004 250);
  --color-ink: oklch(21% 0.018 250);
  --color-accent: oklch(52% 0.155 145);
  --color-signal: oklch(53% 0.17 31);
  --font-body: "PingFang SC", "Microsoft YaHei", sans-serif;
  --radius-card: 0.5rem;
}
```

### DTCG

```json
{
  "color": {
    "paper": { "$value": "oklch(98% 0.004 250)", "$type": "color" },
    "ink": { "$value": "oklch(21% 0.018 250)", "$type": "color" },
    "accent": { "$value": "oklch(52% 0.155 145)", "$type": "color" }
  },
  "radius": { "card": { "$value": "0.5rem", "$type": "dimension" } }
}
```

### shadcn/ui

```css
:root {
  --background: 0.98 0.004 250;
  --foreground: 0.21 0.018 250;
  --primary: 0.52 0.155 145;
  --primary-foreground: 0.98 0.008 150;
  --border: 0.84 0.018 150;
  --ring: 0.16 0.08 250;
  --radius: 0.5rem;
}
```
