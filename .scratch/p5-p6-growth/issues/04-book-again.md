# 04: 从本人历史预约再次预约

**Status:** ready-for-agent

**GitHub Issue:** https://github.com/LikeToGe/sports-booking/issues/5

## Parent

https://github.com/LikeToGe/sports-booking/issues/1

## What to build

用户从本人历史预约进入再订，主动选择新日期，复用场馆、运动、场地和时段偏好，并按当前具体场次价格完成一次新预约。

阶段：P5。用户故事：12, 13, 14, 15, 16, 64；验收：A4, A7, A16, A18。

所有功能任务包含所需的数据迁移、业务/API、页面和验证；追加迁移、不重写历史。测试优先现有 HTTP 与业务边界，关键交互在小程序/后台验收。

## Acceptance criteria

- [ ] 服务端校验历史预约属于本人，偏好仅包含场馆、运动、场地和开始时间；由用户明确选择新日期。
- [ ] 不复制旧价格、余额冻结、支付组成、服务费率或核销码，联系人沿用现有确认流程。
- [ ] 偏好不可用时显示当前结果和由用户主动选择的替代；停用馆、场地、运动类型不得绕过校验。
- [ ] 新预约使用原创建/支付/取消/退款服务，留下最少的再订入口与成功预约关联，不新增长期合同或自动付款。
- [ ] 覆盖价格变化、满场、越权历史记录、并发竞争和原构成退款的 HTTP/业务回归，并验收完整小程序再订流程。

## Blocked by

- https://github.com/LikeToGe/sports-booking/issues/2 — 按可用时段和价格查找具体场次
