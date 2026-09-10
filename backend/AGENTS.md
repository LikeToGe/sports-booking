# 后端 Agent 规范

## 工作流程

1. 修改前读取受影响调用链中的 Controller、Service、Mapper 接口、Mapper XML、模型和相关测试。
2. 修改公共 Service 方法或认证行为前，追踪全部生产调用方。
3. 保留工作区中的无关修改。后端任务仅修改 `backend/`，只有接口契约明确要求时才联动其他目录。
4. 开发中运行相关测试，完成前执行 `./mvn-project package`。命令无法运行时，说明原因和未验证风险。

## 模块规范

- Maven reactor 只包含 `sports-booking-base`、`sports-booking-biz` 和 `sports-booking-server`。只有用户明确要求时才新增 SDK 模块。
- `sports-booking-base` 存放持久化对象、枚举、Mapper 接口、Mapper XML 和内部查询 `*BO`。
- `sports-booking-biz` 存放业务常量、业务异常、`*Request`、`*VO`、Service 接口和 ServiceImpl。
- `sports-booking-server` 存放应用启动类、Controller、统一响应、配置、拦截器、认证上下文和启动 Runner。
- 固定依赖方向：base 不依赖 biz 或 server；biz 不依赖 server；server 可以使用 biz 和 base。

## 边界模型

- 客户端 JSON 模型放在 `biz.pojo.request`，以 `Request` 结尾，使用私有字段的普通类，并添加 Lombok `@Data`、`@NoArgsConstructor` 和 `@AllArgsConstructor`。
- API 返回模型放在 `biz.pojo.vo`，以 `VO` 结尾，使用私有字段的普通类。传输模型不使用 Java record。
- 每个 Request 和 VO 字段都要有简洁中文 JavaDoc、`@ApiModelProperty` 和 Springdoc `@Schema` 说明。
- Mapper 查询投影放在 `base.bo`，以 `BO` 结尾，不添加 API 注解。Mapper 接口和 XML 只返回 PO 或 BO，不返回 VO。
- 在 biz 层把 BO 或 PO 转换为 VO，持久化对象不直接作为公开接口返回值。

## Controller

- Controller 只负责协议参数、认证上下文读取、Service 调用和响应转换。
- Controller 依赖使用 `@Autowired` 字段注入，不添加显式构造方法。
- 完整方法签名不超过 120 个字符时保持单行；超过时只在稳定的参数边界换行。
- 只有 `@RequestBody` 等客户端复合请求体使用 `*Request`。路径参数、查询参数、请求头、文件和认证身份不属于 Request 模型。
- 从 `AuthContext` 读取当前登录用户，不添加 `@RequestAttribute` 身份参数，也不接收客户端传入的用户 ID。
- 平台和场馆角色分别通过 `Principals.platform()`、`Principals.venue()` 校验。

## 认证

- `AuthInterceptor` 通过 `SessionService` 校验 Token，成功后把可信身份写入 `AuthContext`。
- 认证前和 `afterCompletion` 中都要清理 `AuthContext`，防止 Servlet 线程复用造成身份串用。
- ThreadLocal 只在 server 边界使用。把可信的用户 ID、场馆 ID 显式传给 biz Service，保持业务代码可测试。
- 会话缺失、过期或无效时，在进入 Controller 前失败。认证失败不得降级处理，也不信任客户端提供的身份字段。

## Service 与持久化

- 一个完整业务流程沿用现有 Service 与 ServiceImpl。没有第二个真实使用点时，不拆 Query/Command、不增加工厂、Repository 层或单用途抽象。
- 业务编排、事务、状态流转、Redis 访问和副作用放在 biz Service。
- 自定义 SQL 写入对应的 `sports-booking-base/src/main/resources/mapper/*Mapper.xml`，Mapper 接口只声明语义化方法。
- 批量读取在循环前完成，批量写入按集合执行，不在逐项循环中查询数据库。
- 除非任务明确要求，否则保持现有 HTTP 路径、JSON 字段、错误码、事务边界和状态流转语义。

## 注释与安全

- 关键业务原因、状态流转、授权、幂等、事务边界和兼容逻辑使用简洁中文注释。
- 私有方法承载业务规则时保留 JavaDoc。注释解释原因和边界，不复述语法。
- 日志和提交内容不得包含真实 Token、密钥、Cookie、手机号、身份证号或完整地址；测试数据使用明确的本地 Mock 占位值。
- 不在无关任务中升级依赖、格式化无关文件或扩大校验规则。

## 条件参考

遇到本文未明确的架构或代码落位问题时，查看 `/Users/gjm/GjmWork/AiCodingWork/prod-repos/backend-app/jworker-app/` 中对应实现，以及 `/Users/gjm/GjmWork/JddWork/backend/jworker-app/docs/standards/` 下的规范。只复用结构约定，不复制 SDK 代码或无关业务行为。

