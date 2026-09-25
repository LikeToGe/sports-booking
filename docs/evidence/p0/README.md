# P0 截图基线

采集日期：2026-09-14。基线分支：`feature/operations-membership`。

## 管理后台

截图使用本地 Chrome + Playwright 采集，数据来自本地 MySQL/Redis 和运行中的后端服务。

| 页面 | 视口 | 证据 |
|---|---|---|
| 登录 | 1440 x 900 | `admin/login-1440.png` |
| 登录 | 375 x 812 | `admin/login-375.png` |
| 平台场馆 | 1440 x 900 | `admin/platform-venues-1440.png` |
| 平台场馆 | 375 x 812 | `admin/platform-venues-375.png` |
| 平台经营报表 | 1440 x 900 | `admin/platform-reports-1440.png` |
| 场馆今日场次 | 1440 x 900 | `admin/venue-today-1440.png` |
| 场馆今日场次 | 375 x 812 | `admin/venue-today-375.png` |
| 场馆今日场次 | 768 x 1024 | `admin/venue-today-768.png` |
| 场馆预约 | 1440 x 900 | `admin/venue-bookings-1440.png` |
| 场馆预约 | 320 x 700 | `admin/venue-bookings-320.png` |
| 场馆预约 | 375 x 812 | `admin/venue-bookings-375.png` |
| 场馆预约 | 414 x 896 | `admin/venue-bookings-414.png` |
| 场馆固定排期 | 1440 x 900 | `admin/venue-schedule-1440.png` |
| 场馆固定排期 | 375 x 812 | `admin/venue-schedule-375.png` |

目视结论：页面没有横向溢出、控件遮挡或不可操作状态；桌面表格在移动宽度正确降级为字段列表，按钮和导航标签未换行。

## 微信小程序

- 微信开发者工具普通编译问题数为 0，桌面模拟器首页已确认三家场馆图片正常加载。
- 物理真机主流程截图因尚未申请 AppID 暂缓并转为上线前门禁；不阻塞 P2-P4 开发，但未取得真机证据前不得将 P1 标记为完全验收通过或允许上线。
- 真机至少覆盖：订场首页、场馆详情、确认预约、模拟支付、预约详情/凭证、取消、员工核销。

## 工具说明

Chrome 浏览器连接器因本机 Codex 鉴权方式不可用，管理后台改用 Playwright CLI 精确控制视口。微信开发者工具的自动化窗口句柄在采集阶段失效，且 CLI 服务端口未启用，因此不以桌面模拟器冒充物理真机证据；原 P0 真机步骤继续保留，作为 AppID 就绪后的上线前门禁执行。
