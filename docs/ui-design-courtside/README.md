# 约场 · 场边工作台双端设计图册

2026-10-14。基于用户认可的白色导航、青绿强调、冷灰工作区方向，先交付全页面 UI 样例；本目录不修改生产前端和后端。

[打开离线图册](index.html?page=a-today) / [管理端总览](admin-overview.png) / [小程序总览](mini-overview.png) / [双端对照](dual-preview.png)

双击 `index.html` 即可浏览，不需启动后端、联网或安装依赖。可按平台切换、搜索页面、打开页面总览，右上角“打开示例图”可查看完整 PNG。图册中的导航、部分表单选择和预约流程支持演示；保存、支付、核销等不会调用业务接口。

## 范围

- 管理端：25 个实际路由，包含场馆运营、平台资源、资金异常、内容治理和登录。
- 小程序：`app.json` 注册的全部 23 个页面，包含预约、会员、社区与员工工作。
- 关键模式和状态：38 张额外示例，包含 8 个报表视图、9 个管理端操作状态、15 个小程序关键状态、两端各 3 个加载/空列表/失败示例。
- 共 86 张独立 PNG。完整清单、路由及源码映射见 [manifest.json](manifest.json)。

## 视觉约定

青绿 `#087F72` 用于主操作，白色 `#FFFFFF` 用于内容，冷灰 `#F5F8F7` 用于工作区，深绿 `#243936` 用于主文字，浅绿 `#E9F4EE` 用于选中和预约，柔和琥珀用于待处理状态。

管理端以左对齐、分组导航、排场和表格为主，不给每个指标增加独立卡片。小程序以触控和一次一个任务为主：场馆图、时间、价格靠前；会员余额区保留实付与赠送两个口径。中文使用平台字体，不加载远程字体，不增加依赖。状态同时有文字和颜色。

领域边界沿用现有页面：线上收退款与线下人工登记分开；平台会员页只展示场馆汇总；会员余额按场馆独立；单次预约一个完整场次；提醒不保留名额或自动付款；找搭子不代收 AA；注销条件与现有确认文案一致。

现有 `design.md` 和 `tokens.css` 暂不替换。图册是后续实施参考，具体行为、字段和权限仍以当前业务实现为准，不用静态演示数据反推业务规则。

## 管理端主页面

| 页面 | 路由 | 完整示例图 |
|---|---|---|
| 预约管理 | `/bookings` | [查看](previews/a-bookings.png) |
| 今日运营 | `/today` | [查看](previews/a-today.png) |
| 顾客与会员 | `/customers` | [查看](previews/a-customers.png) |
| 充值档位 | `/recharge-plans` | [查看](previews/a-plans.png) |
| 场地管理 | `/courts` | [查看](previews/a-courts.png) |
| 固定排期 | `/schedule` | [查看](previews/a-schedule.png) |
| 具体场次 | `/sessions` | [查看](previews/a-sessions.png) |
| 员工管理 | `/employees` | [查看](previews/a-employees.png) |
| 角色权限 | `/roles` | [查看](previews/a-roles.png) |
| 场馆资料 | `/profile` | [查看](previews/a-profile.png) |
| 场地评价 | `/venue-reviews` | [查看](previews/a-reviews.png) |
| 官方动态 | `/official-posts` | [查看](previews/a-official.png) |
| 官方动态 | `/platform/official-posts` | [查看](previews/a-platform-official.png) |
| 场馆管理 | `/platform/venues` | [查看](previews/a-venues.png) |
| 运动类型 | `/platform/sports` | [查看](previews/a-sports.png) |
| 退款异常 | `/platform/refunds` | [查看](previews/a-refunds.png) |
| 内容审核与处置 | `/platform/moderation` | [查看](previews/a-moderation.png) |
| 内容举报 | `/platform/content-reports` | [查看](previews/a-content-reports.png) |
| 内容申诉 | `/platform/content-appeals` | [查看](previews/a-content-appeals.png) |
| 账号内容权限 | `/platform/content-restrictions` | [查看](previews/a-restrictions.png) |
| 审核配置 | `/platform/content-settings` | [查看](previews/a-content-settings.png) |
| 评价标签 | `/platform/review-tags` | [查看](previews/a-review-tags.png) |
| 经营报表 | `/reports` | [查看](previews/a-reports.png) |
| 经营总报表 | `/platform/reports` | [查看](previews/a-platform-reports.png) |
| 管理端登录 | `/login` | [查看](previews/a-login.png) |

## 小程序主页面

| 页面 | 注册路径 | 完整示例图 |
|---|---|---|
| 订场首页 | `pages/home/index` | [查看](previews/m-home.png) |
| 场馆与场次 | `pages/venue/index` | [查看](previews/m-venue.png) |
| 确认预约 | `pages/confirm/index` | [查看](previews/m-confirm.png) |
| 预约支付 | `pages/payment/index` | [查看](previews/m-payment.png) |
| 我的预约 | `pages/bookings/index` | [查看](previews/m-bookings.png) |
| 预约详情 | `pages/booking/index` | [查看](previews/m-booking.png) |
| 再次预约 | `pages/rebook/index` | [查看](previews/m-rebook.png) |
| 常约场馆 | `pages/favourites/index` | [查看](previews/m-favourites.png) |
| 提醒登记 | `pages/reminders/index` | [查看](previews/m-reminders.png) |
| 场地评价 | `pages/review/index` | [查看](previews/m-review.png) |
| 社区协议 | `pages/content-agreement/index` | [查看](previews/m-content-agreement.png) |
| 社区记录 | `pages/community/index` | [查看](previews/m-community.png) |
| 球友主页 | `pages/community/user` | [查看](previews/m-community-user.png) |
| 场馆公开评价 | `pages/venue-reviews/index` | [查看](previews/m-venue-reviews.png) |
| 运动动态 | `pages/posts/index` | [查看](previews/m-posts.png) |
| 动态详情与找搭子 | `pages/posts/detail` | [查看](previews/m-post-detail.png) |
| 场馆会员 | `pages/membership/index` | [查看](previews/m-membership.png) |
| 个人中心 | `pages/mine/index` | [查看](previews/m-mine.png) |
| 绑定员工身份 | `pages/staff-bind/index` | [查看](previews/m-staff-bind.png) |
| Web 后台登录 | `pages/staff-web-login/index` | [查看](previews/m-staff-web-login.png) |
| 核销与会员识别 | `pages/staff-verify/index` | [查看](previews/m-staff-verify.png) |
| 员工人工充值 | `pages/staff-recharge/index` | [查看](previews/m-staff-recharge.png) |
| 我的核销记录 | `pages/staff-records/index` | [查看](previews/m-staff-records.png) |

## 验证与限制

[verification.json](verification.json) 记录最终检查：

- 通过路由清单集合比对，确保 25 + 23 个主页面无遗漏、无重复，源文件存在，所有设计导航目标有效。
- 86 个画板均在浏览器渲染并导出截图；主画布唯一、未混入图册外框、根级无横向溢出、无缺失图片、无脚本错误。
- 管理端画板 1440px；小程序画板 390px，并逐一复核 320px 根级布局。局部表格或场次网格可横向浏览。
- 图册在 768px 和 390px 下无根级溢出；页面搜索、25/23 张总览导航、示例预约流程以及线下预约弹窗打开/关闭通过。
- 人工复看代表页面及两端总览；图册截图是静态 HTML 设计样例，不是真实 Vue 页面或微信原生页面的截图。

未做微信真机、原生 picker、输入法、安全区、实际业务接口与生产权限验收；本轮未改业务代码，不运行业务构建或后端测试。长页面 PNG 展示完整内容，总览图只显示首屏。所有姓名、手机号、金额、二维码、统计和场次均为演示，示例二维码不可用于登录、核销或识别会员。

场馆图片复用仓库素材，许可与来源见 [SOURCES.md](../../miniapp/assets/venues/SOURCES.md)。PNG 与离线 HTML 已内嵌图片，不依赖外部图库。

## 再生成

`python3 generate.py` 生成图册与清单，并执行页面覆盖断言。`node render.cjs /path/to/existing/playwright` 使用现有 Playwright 与 macOS Chrome 导出截图并复核图册。`python3 overviews.py` 使用已有 Pillow 生成总览。脚本只写本目录的设计资产。
