# 03: 收藏场馆和访问最近预约过的场馆

**Status:** ready-for-agent

**GitHub Issue:** https://github.com/LikeToGe/sports-booking/issues/4

## Parent

https://github.com/LikeToGe/sports-booking/issues/1

## What to build

用户收藏或取消收藏场馆，并从常约入口打开本人收藏与最近预约过的场馆；同一账号跨设备保持收藏。

阶段：P5。用户故事：9, 10, 11, 15, 16；验收：A3, A17, A18。

所有功能任务包含所需的数据迁移、业务/API、页面和验证；追加迁移、不重写历史。测试优先现有 HTTP 与业务边界，关键交互在小程序/后台验收。

## Acceptance criteria

- [ ] 收藏按用户与场馆唯一保存，重复收藏/取消幂等；用户身份由服务端会话推导，不可读写他人偏好。
- [ ] 最近预约过的场馆从本人已确认及终态预约去重，排除仅待支付记录，不将曾预约标记为已到场。
- [ ] 收藏与最近场馆入口可进入现有场馆详情；停用馆明确不可订，仍不绕过当前库存或规则。
- [ ] 账号注销停止使用并取消相关偏好，保留历史预约的原有访问与数据边界。
- [ ] 验证多账号隔离、跨设备读取、重复操作、停用状态及迁移兼容，完成手机加载/空/失败反馈验收。

## Blocked by

- None (can start immediately).
