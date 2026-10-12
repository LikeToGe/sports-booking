# 小程序线性图标

从仓库已安装的 `@lucide/vue` 1.34.0 导出静态 SVG，组件名：

- courts.svg：PanelsTopLeft
- community.svg：MessagesSquare
- calendar.svg：CalendarDays
- membership.svg：CreditCard
- user.svg：UserRound
- search.svg：Search
- location.svg：MapPin
- chevron-down.svg：ChevronDown
- arrow-up-right.svg：ArrowUpRight

统一 24 × 24 画布、1.8 线宽；描边取根 `tokens.css` 的 `--color-ink`（#233c31）。SVG 图片不能继承页面变量，修改该 Token 时同步重新导出；选中背景由 WXSS 控制。许可见同目录 LICENSE。
