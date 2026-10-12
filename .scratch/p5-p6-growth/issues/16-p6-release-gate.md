# 16: 验收提醒回流和场馆经营完整闭环

**Status:** ready-for-agent

**GitHub Issue:** https://github.com/LikeToGe/sports-booking/issues/17

## Parent

https://github.com/LikeToGe/sports-booking/issues/1

## What to build

统一验收开售/空位/到场提醒、时段分析和顾客回访，并回归全部 P5 路径，形成 P6 开发验收与后续功能评估所需证据。

阶段：P6。用户故事：63, 64；验收：A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18。

所有功能任务包含所需的数据迁移、业务/API、页面和验证；追加迁移、不重写历史。测试优先现有 HTTP 与业务边界，关键交互在小程序/后台验收。

## Acceptance criteria

- [ ] 用隔离数据完成登记到消息到重新预约、到场提醒到本人详情、热力图到下钻、顾客分组到回访处理的完整流程。
- [ ] 覆盖 PRD A8-A18，重点验证发布后再释放、消息重试/重启/撤回、统计分钟、顾客场馆隔离、即时撤权与并发。
- [ ] 回归 P5 所有入口及现有资金/取消/退款/UGC规则，核对契约、迁移、规定构建测试和双端交互结果。
- [ ] 能够观察入口成功关联、提醒重新预约、非高峰预约利用率与回访后再预约；不把参与通过算到场、不虚报微信送达。
- [ ] 交付证据和未验证项；真机/外部能力/公开 UGC 的原有上线门禁保持显式，不将开发通过描述成允许生产上线。

## Blocked by

- https://github.com/LikeToGe/sports-booking/issues/9 — 到场提醒直达本人预约并支持可靠 Mock 投递
- https://github.com/LikeToGe/sports-booking/issues/11 — 取消、超时和重新开放后发送空位提醒
- https://github.com/LikeToGe/sports-booking/issues/13 — 比较场地表现并下钻低利用时段
- https://github.com/LikeToGe/sports-booking/issues/16 — 从顾客分组创建并处理回访待办
