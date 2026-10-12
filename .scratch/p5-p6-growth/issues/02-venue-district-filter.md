# 02: 维护场馆区县并按区县找空场

**Status:** ready-for-agent

**GitHub Issue:** https://github.com/LikeToGe/sports-booking/issues/3

## Parent

https://github.com/LikeToGe/sports-booking/issues/1

## What to build

平台及获授权场馆人员维护场馆所属区县，用户在时段找场时选择郑州区县，查看明确归属该区县的可订具体场次。

阶段：P5。用户故事：4；验收：A2, A17, A18。

所有功能任务包含所需的数据迁移、业务/API、页面和验证；追加迁移、不重写历史。测试优先现有 HTTP 与业务边界，关键交互在小程序/后台验收。

## Acceptance criteria

- [ ] 提供可选结构化区县，复用场馆资料编辑权限；旧场馆升级不要求猜测或重置地址。
- [ ] 小程序不选区县时保留所有符合其他条件的场馆，指定区县时只返回明确匹配的场馆；未配置场馆展示未设置。
- [ ] 区县条件与时间、运动、名称及价格共同生效，进入详情保留相关条件。
- [ ] 不读取用户实时定位，不从地址推断行政区，不引入多城市行政区平台。
- [ ] 覆盖旧数据/空库迁移、资料编辑越权、缺失区县和筛选组合的接口与两端页面验收。

## Blocked by

- https://github.com/LikeToGe/sports-booking/issues/2 — 按可用时段和价格查找具体场次
