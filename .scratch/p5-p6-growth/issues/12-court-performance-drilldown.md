# 12: 比较场地表现并下钻低利用时段

**Status:** ready-for-agent

**GitHub Issue:** https://github.com/LikeToGe/sports-booking/issues/13

## Parent

https://github.com/LikeToGe/sports-booking/issues/1

## What to build

运营人员从热力图查看场地对比、低利用时段和取消情况，并在权限允许时跳到相同条件的具体场次或预约视图处理经营。

阶段：P6。用户故事：42, 44, 45, 46, 47；验收：A11, A18。

所有功能任务包含所需的数据迁移、业务/API、页面和验证；追加迁移、不重写历史。测试优先现有 HTTP 与业务边界，关键交互在小程序/后台验收。

## Acceptance criteria

- [ ] 场地对比与低利用清单复用已验收分钟口径和当前日期/运动筛选，稳定排序，不另建不一致的利用率公式。
- [ ] 取消情况沿用原预约日期口径并说明范围，不把取消后库存当过去履约，也不将线下登记推断为实际到场。
- [ ] 下钻保留场地、日期和时段条件，复用已有场次/预约视图；缺少目标权限时不暴露明细。
- [ ] 金额保持现有独立口径，平台只看场馆级分析，所有视图显示生成时间和历史数据限制。
- [ ] 接口与后台验证对比数值、无容量/无数据、排序、权限及下钻条件一致，不新增综合导出中心或自动调价。

## Blocked by

- https://github.com/LikeToGe/sports-booking/issues/12 — 按星期和小时查看预约利用率热力图
