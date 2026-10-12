# 15: 从顾客分组创建并处理回访待办

**Status:** ready-for-agent

**GitHub Issue:** https://github.com/LikeToGe/sports-booking/issues/16

## Parent

https://github.com/LikeToGe/sports-booking/issues/1

## What to build

有顾客经营编辑权限的场馆人员从 30 天未再预约分组创建回访待办，记录完成或无需处理与简短备注，使其他授权人员能看到处理结果。

阶段：P6。用户故事：50, 51, 52, 53, 54, 64；验收：A12, A13, A17, A18。

所有功能任务包含所需的数据迁移、业务/API、页面和验证；追加迁移、不重写历史。测试优先现有 HTTP 与业务边界，关键交互在小程序/后台验收。

## Acceptance criteria

- [ ] 只针对已有本馆顾客创建，显示分组来源、创建人与时间；同一顾客同一目的不重复存在未完成待办。
- [ ] 状态仅待处理、已完成、无需处理，处理人员与时间留痕，标签/回访使用同一固定编辑权限。
- [ ] 当前馆归属与即时撤权由服务端校验，平台仅场馆级汇总；备注不公开，不新增敏感属性采集。
- [ ] 首版只生成后台待办，不自动营销群发、不导出联系方式、不新增完整 CRM。
- [ ] 可结合既有预约事实观察处理后的再预约，不把时间相关性宣称为营销因果。
- [ ] 运行态验证分组到创建到处理、重复与并发创建、越权/撤权和历史迁移，后台列表与详情结果一致。

## Blocked by

- https://github.com/LikeToGe/sports-booking/issues/14 — 查看顾客预约习惯和 30 天未再预约分组
- https://github.com/LikeToGe/sports-booking/issues/15 — 维护场馆顾客标签并控制经营编辑权限
