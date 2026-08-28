# Sports Booking Admin API

已完成 Mock MVP 功能闭环：身份与租户、场馆供给、库存与排期、预约与 Mock 支付、取消退款、员工核销、自动完成、服务费账单，以及 PC 管理端和用户端。接口契约见 `../docs/openapi.yaml`。

## 本地启动

1. 按 `../infra/.env.example` 配置并启动 MySQL 和 Redis。
2. 在当前 shell 导出 `MYSQL_USER`、`MYSQL_PASSWORD`；如数据库地址不是默认值，再导出 `APP_DB_URL`。
3. 首次启动时，同时导出 `APP_BOOTSTRAP_ADMIN_USERNAME`、`APP_BOOTSTRAP_ADMIN_PASSWORD`，用于创建唯一的平台管理员。应用不记录该密码；同名账号已存在时不执行任何覆盖。
4. 执行 `./mvn-project -Dmaven.repo.local=target/m2 spring-boot:run`。该脚本只为当前命令使用项目专用 Java 17，不修改系统的 Java 8 环境变量。Flyway 会自动执行 V1 至 V11 迁移。

新建场馆管理员时，平台管理员在受控管理端设置临时密码并线下交接；接口响应、审计和普通日志均不包含该密码。

## 验收

在 `backend/` 运行以下命令可验证本地 MySQL 上的完整 Mock 闭环，脚本会创建独立的临时数据并在结束时停止应用：

```sh
./mvn-project -Dmaven.repo.local=target/m2 test
./mvn-project -Dmaven.repo.local=target/m2 package
zsh scripts/verify-t06-runtime.zsh
```

运行态脚本还会短暂停止并恢复本地 Redis，验证管理端登录、小程序/店员刷新、店员绑定和短码核销在 Redis 不可用时返回 503，恢复后主链路继续可用。
