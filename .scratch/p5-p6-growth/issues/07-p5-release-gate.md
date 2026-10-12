# 07: 验收 P5 订场与找搭子的完整路径

**Status:** ready-for-agent

**GitHub Issue:** https://github.com/LikeToGe/sports-booking/issues/8

## Parent

https://github.com/LikeToGe/sports-booking/issues/1

## What to build

通过统一隔离数据演示时段找场、区县筛选、常约再订、场次比较和找搭子参与，并形成可核查的 P5 开发验收结果，作为进入 P6 的阶段门禁。

阶段：P5。用户故事：63, 64；验收：A1, A2, A3, A4, A5, A6, A7, A16, A17, A18。

所有功能任务包含所需的数据迁移、业务/API、页面和验证；追加迁移、不重写历史。测试优先现有 HTTP 与业务边界，关键交互在小程序/后台验收。

## Acceptance criteria

- [ ] 用同一组隔离 MySQL/Redis 数据跑通所有 P5 入口及完整预约、取消、原构成退款和招募申请；不向已有演示资金写验收流水。
- [ ] 完成 PRD A1-A7 和 A16-A18 的 P5 相关验收，含迁移升级、并发、权限、手机及后台交互，不用编译通过冒充运行验收。
- [ ] 核对运行接口契约与双端请求，执行规定的后端打包/相关测试、后台类型检查/构建和小程序行为检查/原生编译。
- [ ] 核对最少入口来源与成功事实，可观察搜索、再订、约球路径；不将曝光或申请当成核销到场。
- [ ] 保留 P4 端侧/真机缺口的独立说明；记录真实通过与未验证项，满足开发门禁后才允许 P6 开始。

## Blocked by

- https://github.com/LikeToGe/sports-booking/issues/2 — 按可用时段和价格查找具体场次
- https://github.com/LikeToGe/sports-booking/issues/3 — 维护场馆区县并按区县找空场
- https://github.com/LikeToGe/sports-booking/issues/4 — 收藏场馆和访问最近预约过的场馆
- https://github.com/LikeToGe/sports-booking/issues/5 — 从本人历史预约再次预约
- https://github.com/LikeToGe/sports-booking/issues/6 — 按时间和场地比较具体场次
- https://github.com/LikeToGe/sports-booking/issues/7 — 按日期、水平和剩余名额寻找搭子
