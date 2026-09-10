# 约场后端

Spring Boot + MyBatis-Plus + MySQL/Redis 的本地 Mock MVP。

## 代码分层

后端参考 `jworker-app` 使用三个 Maven 模块，不提供 SDK：

- `sports-booking-base`：枚举、Mapper、PO、Mapper 查询 BO 和 Mapper XML。
- `sports-booking-biz`：业务异常、常量、Request、VO 以及 `Service + ServiceImpl`。
- `sports-booking-server`：启动类、统一响应、应用配置、Controller、认证拦截器和 Runner。

依赖方向固定为 `server -> biz -> base`。Controller 只处理协议参数、身份上下文和响应转换；Mapper 只返回 base 的 PO/BO，Service 负责转换成 biz VO。

## 启动

```sh
cd ../infra
docker compose up -d

cd ../backend
set -a
source ../infra/.env
set +a
./mvn-project package
/Users/gjm/Library/Java/project-jdks/zulu17.66.19-ca-jdk17.0.19-macosx_aarch64/Contents/Home/bin/java \
  -jar sports-booking-server/target/sports-booking-server.jar
```

接口基址：`http://127.0.0.1:8089/api`。

Swagger 文档：

- Swagger UI：`http://127.0.0.1:8089/api/swagger-ui/index.html`
- OpenAPI JSON：`http://127.0.0.1:8089/api/v3/api-docs`

本地默认账号：

- 平台管理员：`admin / admin123`
- 三个演示场馆管理员：`venue1`、`venue2`、`venue3`，密码均为 `venue123`

生产环境必须覆盖后台密码、`APP_CREDENTIAL_SECRET`，并设置 `APP_DEMO_SEED_ENABLED=false`。

## 验证

```sh
./mvn-project test
./mvn-project package
```

真实微信能力不在当前范围；模拟支付和退款仅用于本地演示。
