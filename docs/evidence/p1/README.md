# P1 验收证据

验收日期：2026-09-15。分支：`feature/operations-membership`。

## 自动化与运行态

- 后端 `./mvn-project package`：biz 61 个测试、server 13 个测试通过。
- 管理后台 `npm run typecheck`、`npm run build` 通过；小程序全部 JavaScript 通过 `node --check`。
- V5 空库 V1-V5 和 V4 升级验证通过；线上历史回填覆盖 `REFUNDED / PAID / UNPAID`，线下历史回填为 `UNRECORDED`。
- `backend/scripts/verify-mvp.zsh` 通过，原 62 路径 MVP 主流程未回退。
- `backend/scripts/verify-p1.zsh` 通过：顾客建档、员工会员识别、平台脱敏汇总、日视图、线下登记收款、取消后退款登记、批量原子创建/全冲突日期、同价改期。
- 运行态 Springdoc 与 `docs/openapi.yaml` 均为 73 条路径，路径集合一致。
- 数据库核对：运行态线下样本生成 2 条收款状态日志、1 条改期日志，支付、退款、核销记录均为 0。

## 管理后台多视口

- `today`、`bookings`、`customers` 均完成 320、375、414、768、1440 宽度截图。
- 三页根级 `scrollWidth` 均不大于视口；日视图横向滚动仅限网格容器。
- 375 宽度额外覆盖场次操作抽屉、批量预约模态和顾客详情抽屉；抽屉边界为全屏 `x=0,width=375,height=812`。
- 截图位于 `docs/evidence/p1/admin/`。

## 小程序模拟器

- 微信开发者工具 Stable `2.02.2608060` 普通编译通过，模拟器为 iPhone 12/13 (Pro)。
- 首页三家场馆图片与“订场 / 预约 / 会员 / 我的”四 Tab 正常。
- 会员页空态和非空态均通过；非空态显示场馆、普通顾客身份、会员码二维码、实付/赠送余额 `¥0` 和空流水。
- 员工工作页保留预约核销并新增会员识别模式，可扫描会员码或输入会员码/完整手机号；页面宽度 390/390。
- 会员页 WebView 内容宽度 375、小于 390 viewport；二维码组件为完整 Data URL，尺寸稳定。
- 证据：`miniapp-devtools.png`、`miniapp-membership.png`、`miniapp-membership-page.png`、`miniapp-staff-member.png`。

## 未完成上线门禁

- 物理真机因尚未申请 AppID 仍未验证。经用户确认，该项不阻塞 P2-P4 开发；P1 只能标记为“开发门禁已通过，真机待验”，不得标记为完全验收通过或允许上线。
