# 13: 查看顾客预约习惯和 30 天未再预约分组

**Status:** ready-for-agent

**GitHub Issue:** https://github.com/LikeToGe/sports-booking/issues/14

## Parent

https://github.com/LikeToGe/sports-booking/issues/1

## What to build

有顾客查看权限的场馆人员在现有顾客列表查看本馆预约/核销信息、次数及常约时段，筛选可明确解释的 30 天未再预约顾客。

阶段：P6。用户故事：48, 52, 53；验收：A12, A13, A18。

所有功能任务包含所需的数据迁移、业务/API、页面和验证；追加迁移、不重写历史。测试优先现有 HTTP 与业务边界，关键交互在小程序/后台验收。

## Acceptance criteria

- [ ] 有效预约次数排除待支付与取消，线上到场事实只来自 COMPLETED，线下履约未跟踪不推断已到场；常约时段稳定聚合。
- [ ] 预设分组仅包含曾完成线上预约、最近完成已满 30 天且没有未来已确认预约的本馆顾客，严格验证时区和 30 天边界。
- [ ] 读取按 CUSTOMER_VIEW 与当前场馆限制，不合并其他馆资料/余额，平台不获得个人顾客列表。
- [ ] 列表与详情明确预约/核销来源，空记录显示暂无，不把分组称为确定流失或推断线下活动。
- [ ] 覆盖当前/其他馆数据、临界时间、未来预约、仅线下记录与撤权，完成 HTTP 和后台查询交互验收。

## Blocked by

- https://github.com/LikeToGe/sports-booking/issues/8 — 验收 P5 订场与找搭子的完整路径
