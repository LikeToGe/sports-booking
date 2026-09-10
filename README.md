# 约场

郑州市多场馆固定场次预约 MVP。

## 目录

- `backend/`：Spring Boot + MyBatis-Plus 后端。
- `admin-web/`：Vue 3 管理后台。
- `miniapp/`：微信原生小程序。
- `infra/`：本地 MySQL 与 Redis Docker Compose。
- `PRD.md`：产品基线。
- `CONTEXT.md`：统一领域词汇。
- `design.md`：两端共用设计系统。
- `docs/openapi.yaml`：接口契约。

## 本地运行

1. 按 `infra/.env.example` 创建 `infra/.env`，执行 `docker compose up -d`。
2. 按 [backend/README.md](./backend/README.md) 启动后端。
3. 在 `admin-web/` 执行 `npm install && npm run dev`。
4. 使用微信开发者工具导入 `miniapp/`；当前使用游客 AppID 与模拟登录。

管理后台地址：`http://127.0.0.1:8088`。

## 范围

当前只提供本地 Mock 闭环。真实微信登录、支付、退款、订阅消息、定位推荐和生产部署均未实现。
