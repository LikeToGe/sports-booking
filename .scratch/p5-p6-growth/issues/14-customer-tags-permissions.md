# 14: 维护场馆顾客标签并控制经营编辑权限

**Status:** ready-for-agent

**GitHub Issue:** https://github.com/LikeToGe/sports-booking/issues/15

## Parent

https://github.com/LikeToGe/sports-booking/issues/1

## What to build

场馆超级管理员或有顾客经营编辑权限的员工在现有顾客页面维护本馆标签；只读人员可查看但不能改动。

阶段：P6。用户故事：49, 52, 53, 54；验收：A13, A17, A18。

所有功能任务包含所需的数据迁移、业务/API、页面和验证；追加迁移、不重写历史。测试优先现有 HTTP 与业务边界，关键交互在小程序/后台验收。

## Acceptance criteria

- [ ] 固定权限目录增加一个顾客经营编辑权限，控制标签与后续回访写入；超级管理员天然拥有，员工显式授权，不借用资金权限。
- [ ] 标签和顾客关联均验证当前场馆，关联唯一幂等，旧顾客无标签正常升级，标签不参与会员身份、折扣或计价。
- [ ] 后台可维护标签和顾客关联，并明确只读/编辑权限；服务端校验，不能仅隐藏按钮。
- [ ] 撤权下一请求拒绝编辑，记录修改人员与时间；平台不能访问个人顾客标签，公开页面不泄露标签。
- [ ] 完成越馆读写、重复关联、即时撤权、迁移兼容及后台真实操作验收。

## Blocked by

- https://github.com/LikeToGe/sports-booking/issues/8 — 验收 P5 订场与找搭子的完整路径
