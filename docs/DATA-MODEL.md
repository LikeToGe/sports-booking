# MVP 数据模型：身份、供给、订单、核销与账单

## 1. 范围

本文件冻结 T-01 至 T-12 Mock 功能闭环：身份租户、场馆供给、排期库存、交易门禁、小程序预约、Mock 支付与退款、员工绑定核销、自动完成、服务费分录和月账单。

真实支付渠道、真实退款、分账、发票和生产财务报表仍不在本数据模型范围内。

```text
Organization 1 --- 1 Store 1 --- n AdminAccount
                                  |
                                  n AdminSession

AdminAccount 1 --- n IdempotencyRecord
AdminAccount 1 --- n AuditLog

Store 1 --- n PhysicalCourt 1 --- 2 BaseResource
                                 |
                                 n SellableUnit 1 --- n SellableUnitVersion
                                                              |
                                                              n SellableUnitVersionResource
```

## 2. 公共约定

- MySQL 是主体、门店、账号、会话撤销、幂等结果和审计事实的最终来源。
- 主键使用 `BIGINT UNSIGNED`；接口将所有 ID 序列化为字符串。
- 数据库按 UTC 保存；接口按 `yyyy-MM-dd HH:mm:ss` 输出。
- 所有表必须包含以下固定字段；数据库字段命名不再使用 `created_at`、`updated_at`：

```sql
create_operator_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建用户ID',
modify_operator_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '修改用户ID',
gmt_created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
gmt_modified_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除：0未删除 1已删除'
```

- `create_operator_id`、`modify_operator_id` 保存管理端账号 ID；系统任务、登录前操作或无法归属账号的操作填 `0`，不设外键，避免历史操作员账号软删除后破坏事实记录。
- 业务查询默认附加 `is_deleted = 0`。所有逻辑删除操作必须同时更新 `modify_operator_id` 与 `gmt_modified_time`。
- 审计事实和幂等记录仅追加，不做业务更新或逻辑删除；它们仍具有上述固定字段，以保持全表结构一致。
- 枚举先以 `VARCHAR` 保存，应用层只接受本文列出的值；不为首期新增字典表。

## 3. 实体与约束

### 3.1 `organization`

经营主体。首期界面不提供主体自助注册。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `organization_no` | `VARCHAR(32)` | 全局唯一、不可变业务编号 |
| `name` | `VARCHAR(128)` | 必填 |
| `status` | `VARCHAR(16)` | `ACTIVE`、`PAUSED` |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (organization_no)`。

### 3.2 `store`

门店/场馆。首期仍保存独立门店实体，但每个主体只能有一个活动门店。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `organization_id` | `BIGINT UNSIGNED` | 外键引用 `organization.id` |
| `store_no` | `VARCHAR(32)` | 全局唯一、不可变业务编号 |
| `name` | `VARCHAR(128)` | 必填 |
| `status` | `VARCHAR(16)` | `ACTIVE`、`PAUSED` |
| `service_rate_basis_points` | `INT UNSIGNED` | 初始服务费率，按基点保存；例如 `600` 表示 6.00% |
| `introduction` | `TEXT NULL` | 场馆介绍，场馆管理员保存后立即生效 |
| `address` | `VARCHAR(255) NULL` | 展示地址 |
| `contact_phone` | `VARCHAR(32) NULL` | 场馆联系电话 |
| `longitude` | `DECIMAL(10,7) NULL` | 地图经度 |
| `latitude` | `DECIMAL(10,7) NULL` | 地图纬度 |
| `image_urls` | `JSON NULL` | 展示图片 URL 数组 |
| `facilities` | `JSON NULL` | 设施服务名称数组 |
| `traffic_note` | `TEXT NULL` | 交通说明 |
| `entry_notice` | `TEXT NULL` | 入场须知 |
| `equipment_note` | `TEXT NULL` | 器材说明 |
| `parking_note` | `TEXT NULL` | 停车说明 |
| `liability_note` | `TEXT NULL` | 场馆责任说明 |
| `booking_min_lead_minutes` | `INT UNSIGNED` | 最短提前预约分钟数，首期默认 `30` |
| `booking_max_days` | `INT UNSIGNED` | 最远可预约天数，首期默认 `7` |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：

- `FOREIGN KEY (organization_id) REFERENCES organization(id)`。
- `UNIQUE (store_no)`。
- `UNIQUE (organization_id)` 是 MVP 的单门店限制；未来多门店时以迁移删除该唯一约束，而非复制主体字段到业务表。
- `booking_min_lead_minutes` 必须是 30 分钟的正整数；`booking_max_days` 必须为正整数。T-04 仅使用它们生成和展示滚动库存，用户预约窗口在 T-06 强制校验。

### 3.3 `admin_account`

PC 管理端账号。平台管理员没有门店范围；场馆管理员必须归属一个门店。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `username` | `VARCHAR(64)` | 全局唯一、登录名不可变 |
| `password_hash` | `VARCHAR(255)` | 仅保存 Argon2id 或 bcrypt 哈希 |
| `display_name` | `VARCHAR(64)` | 必填 |
| `role` | `VARCHAR(24)` | `PLATFORM_ADMIN`、`VENUE_ADMIN` |
| `store_id` | `BIGINT UNSIGNED NULL` | `VENUE_ADMIN` 必填；`PLATFORM_ADMIN` 必须为空 |
| `status` | `VARCHAR(16)` | `ACTIVE`、`DISABLED`、`LOCKED` |
| `effective_venue_admin_store_id` | `BIGINT UNSIGNED NULL` | 生成列；仅当角色为 `VENUE_ADMIN`、状态不是 `DISABLED` 且未逻辑删除时等于 `store_id` |
| `force_password_change` | `TINYINT(1)` | 新建或重置密码后为 `1` |
| `failed_login_count` | `INT UNSIGNED` | 连续失败计数 |
| `locked_until` | `DATETIME(3) NULL` | 临时锁定到期时间 |
| `last_login_at` | `DATETIME(3) NULL` | 最近成功登录时间 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：

- `UNIQUE (username)`。
- `FOREIGN KEY (store_id) REFERENCES store(id)`。
- `CHECK ((role = 'PLATFORM_ADMIN' AND store_id IS NULL) OR (role = 'VENUE_ADMIN' AND store_id IS NOT NULL))`。
- `UNIQUE (effective_venue_admin_store_id)`，确保首期每个门店只有一个有效场馆管理员；临时锁定账号仍占用名额，只有停用或逻辑删除账号生成列为 `NULL`，历史账号可保留而不阻塞替代管理员创建。

### 3.4 `admin_session`

PC 管理端会话。浏览器只持有 `HttpOnly`、`Secure`、`SameSite` Cookie；数据库不保存原始会话令牌。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `account_id` | `BIGINT UNSIGNED` | 外键引用 `admin_account.id` |
| `session_token_hash` | `CHAR(64)` | 会话令牌 SHA-256 哈希，唯一 |
| `csrf_token_hash` | `CHAR(64)` | CSRF 令牌哈希 |
| `expires_at` | `DATETIME(3)` | 过期时间 |
| `revoked_at` | `DATETIME(3) NULL` | 撤销时间 |
| `revoke_reason` | `VARCHAR(32) NULL` | `LOGOUT`、`PASSWORD_CHANGED`、`PASSWORD_RESET`、`ACCOUNT_DISABLED` |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (session_token_hash)`、`FOREIGN KEY (account_id) REFERENCES admin_account(id)`。

密码修改、密码重置和账号停用必须在同一事务内更新账号状态并写入关联会话的 `revoked_at`；不可只删除 Cookie。

### 3.5 `idempotency_record`

管理端有副作用请求的幂等事实。首期用于创建经营主体/门店/管理员、改密、退出、门店资料更新等写操作。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `account_id` | `BIGINT UNSIGNED` | 认证账号；登录前接口允许为空 |
| `request_scope` | `VARCHAR(128)` | HTTP 方法与路径，例如 `POST:/api/admin/organizations` |
| `idempotency_key` | `VARCHAR(128)` | 请求头原值 |
| `request_hash` | `CHAR(64)` | 规范化请求内容经 SHA-256 后的 BCrypt 保护指纹；用于比较同键请求，不保存可快速枚举的密码指纹 |
| `response_body` | `JSON` | 首次处理后的完整统一响应体 |
| `http_status` | `SMALLINT UNSIGNED` | 首次处理 HTTP 状态 |
| `expires_at` | `DATETIME(3)` | 幂等窗口到期时间 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (account_id, request_scope, idempotency_key)`。同键但 `request_hash` 不同必须拒绝，不能覆盖首次结果。

### 3.6 `audit_log`

高风险管理操作审计。首期至少记录登录成功/失败、首次改密、退出、创建场馆、重置密码、门店资料更新。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `actor_account_id` | `BIGINT UNSIGNED NULL` | 发起账号；登录失败允许为空 |
| `action` | `VARCHAR(64)` | 例如 `ADMIN_LOGIN_SUCCEEDED` |
| `target_type` | `VARCHAR(32)` | `ACCOUNT`、`ORGANIZATION`、`STORE` |
| `target_id` | `BIGINT UNSIGNED NULL` | 被操作对象 |
| `organization_id` | `BIGINT UNSIGNED NULL` | 便于主体范围审计 |
| `store_id` | `BIGINT UNSIGNED NULL` | 便于门店范围审计 |
| `result` | `VARCHAR(16)` | `SUCCEEDED`、`REJECTED`、`FAILED` |
| `reason` | `VARCHAR(256) NULL` | 不记录密码、令牌或完整手机号 |
| `request_id` | `VARCHAR(64) NULL` | 调试关联标识 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

## 4. 数据范围规则

| 认证角色 | 允许范围 | 服务端取值 |
| --- | --- | --- |
| `PLATFORM_ADMIN` | 全平台 | 不接收客户端传入的主体/门店范围作为授权依据 |
| `VENUE_ADMIN` | `admin_account.store_id` 对应的唯一门店及其主体 | 由认证账号的 `store_id` 反查主体 |

所有门店级后续表只保存 `store_id`；需要主体范围时通过受约束的 `store.organization_id` 推导。请求中的 `storeId` 只能作为资源定位条件，不能替代授权范围。

## 5. 首条切片事务

### 创建场馆

平台管理员的 `POST /api/admin/organizations` 在一个事务内：

1. 校验 `Idempotency-Key`。
2. 插入 `organization`。
3. 插入唯一 `store`。
4. 插入 `VENUE_ADMIN`，`force_password_change = 1`。
5. 写入幂等结果与审计日志。

任一步失败则整体回滚；平台管理员在受控管理端设置临时密码后线下交接给场馆负责人。服务端只保存密码哈希，初始密码不写入审计、幂等响应或普通日志。

### 首次改密

认证账号提交正确的初始密码后，在一个事务内更新 `password_hash`、清空失败计数、设置 `force_password_change = 0`，撤销该账号其他会话并写入审计。

## 6. T-02 平台字典实体

### 6.1 `sport_category`

平台维护的运动分类树。首期只允许两级：一级运动大类和二级具体项目；场地在 T-03 只能关联启用的二级项目。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `parent_id` | `BIGINT UNSIGNED NULL` | 一级分类为空；二级分类引用一级分类 |
| `code` | `VARCHAR(32)` | 全局唯一、创建后可编辑但不可与历史或现有记录重复 |
| `name` | `VARCHAR(64)` | 分类名称 |
| `level` | `TINYINT UNSIGNED` | `1` 一级，`2` 二级；由 `parent_id` 推导并保存 |
| `icon` | `VARCHAR(64) NULL` | 图标标识，不保存图标资源 |
| `sort_order` | `INT UNSIGNED` | 同级展示排序，数值越小越靠前 |
| `status` | `VARCHAR(16)` | `ACTIVE`、`DISABLED` |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (code)`、`FOREIGN KEY (parent_id) REFERENCES sport_category(id)`。应用层只允许根节点或其直接子节点，禁止三级、循环引用和有子节点的层级迁移。

### 6.2 `rental_type`

平台按具体运动项目维护的出租类型，例如篮球的全场、半场。它只提供名称、筛选和统计维度，不能推导 T-03 的资源组合关系。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `sport_category_id` | `BIGINT UNSIGNED` | 必须引用二级运动项目 |
| `name` | `VARCHAR(64)` | 出租类型名称 |
| `status` | `VARCHAR(16)` | `ACTIVE`、`DISABLED` |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (sport_category_id, name)`、`FOREIGN KEY (sport_category_id) REFERENCES sport_category(id)`。分类或出租类型停用不改写历史记录；T-03 禁止新场地选择停用项。

## 7. T-03 场地资源与可售单元版本

首期每个物理场地固定拆为 A、B 两个基础资源，不开放任意数量的基础资源或任意组合。创建物理场地时，服务端在同一事务中生成 A/B 基础资源和全场、A 半场、B 半场三个可售单元的 `v1` 版本；全场使用 `[A, B]`，两个半场分别使用 `[A]`、`[B]`。

出租类型只提供名称、筛选和统计维度，资源组合始终以版本关联的基础资源集合为准，不能通过出租类型名称推导。

### 7.1 `physical_court`

场馆内可被拆分的物理场地。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `store_id` | `BIGINT UNSIGNED` | 所属门店 |
| `sport_category_id` | `BIGINT UNSIGNED` | 创建时必须是启用的二级运动项目 |
| `name` | `VARCHAR(128)` | 同一门店内唯一 |
| `sort_order` | `INT UNSIGNED` | 展示排序，数值越小越靠前 |
| `status` | `VARCHAR(16)` | `ACTIVE`、`DISABLED`；下架不删除历史资源或版本 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (store_id, name)`；外键引用 `store.id`、`sport_category.id`。场馆管理员只能维护认证会话所属门店的场地。

### 7.2 `base_resource`

物理场地的原子基础资源。首期每个物理场地只能有代码为 `A`、`B` 的两条记录，由创建场地事务一次性生成。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `physical_court_id` | `BIGINT UNSIGNED` | 所属物理场地 |
| `code` | `VARCHAR(8)` | 仅 `A`、`B` |
| `name` | `VARCHAR(128)` | 初始为 `A半场`、`B半场` |
| `sort_order` | `INT UNSIGNED` | 固定 A 在前、B 在后 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (physical_court_id, code)`；外键引用 `physical_court.id`。T-03 不提供基础资源创建、修改或删除接口，避免把固定的 A/B 模型扩展成任意组合。

### 7.3 `sellable_unit`

面向排期和用户展示的逻辑可售单元。它本身可上下架；当前实际占用的资源集合只由其 `CURRENT` 版本决定。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `physical_court_id` | `BIGINT UNSIGNED` | 所属物理场地 |
| `rental_type_id` | `BIGINT UNSIGNED` | 与物理场地运动项目相同的出租类型 |
| `name` | `VARCHAR(128)` | 初始为“物理场地名 + 全场/A 半场/B 半场” |
| `sort_order` | `INT UNSIGNED` | 展示排序 |
| `status` | `VARCHAR(16)` | `ACTIVE`、`DISABLED` |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (physical_court_id, name)`；外键引用 `physical_court.id`、`rental_type.id`。服务端校验出租类型的运动项目与物理场地一致且二者均启用后才允许新建物理场地。

### 7.4 `sellable_unit_version`

可售单元的不可变资源组合版本。`CURRENT` 表示该可售单元用于后续排期的版本，新增版本时原 `CURRENT` 原子切换为 `RETIRED`；历史版本只读保留。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `sellable_unit_id` | `BIGINT UNSIGNED` | 所属可售单元 |
| `version_no` | `INT UNSIGNED` | 自 1 起按可售单元递增 |
| `version_status` | `VARCHAR(16)` | `CURRENT`、`RETIRED` |
| `current_sellable_unit_id` | `BIGINT UNSIGNED NULL` | 生成列；仅当版本为 `CURRENT` 且未逻辑删除时等于 `sellable_unit_id` |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (sellable_unit_id, version_no)`、`UNIQUE (current_sellable_unit_id)`；外键引用 `sellable_unit.id`。前者保证版本号不可重复，后者保证一个可售单元最多一个当前版本。

### 7.5 `sellable_unit_version_resource`

版本与基础资源的关联事实。每个版本的资源集合一经写入不得修改；首期只接受同一物理场地内的非空 A/B 子集，即 `[A]`、`[B]` 或 `[A, B]`。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `sellable_unit_version_id` | `BIGINT UNSIGNED` | 可售单元版本 ID，与 `base_resource_id` 共同组成主键 |
| `base_resource_id` | `BIGINT UNSIGNED` | 基础资源 ID |
| `sort_order` | `INT UNSIGNED` | 资源集合中的稳定排序 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：两个外键均使用默认 `RESTRICT` 删除策略。T-03 不提供任何物理删除接口；T-04 及后续排期、库存、占用、订单表必须保存 `sellable_unit_version_id` 并以外键限制删除。因此已被引用的版本和基础资源不得物理删除。

可售单元资源组合变更的事务顺序为：锁定逻辑可售单元，校验请求资源全部属于该物理场地，读取并保留旧 `CURRENT` 版本，将其改为 `RETIRED`，插入下一个 `CURRENT` 版本及其关联资源，再写入 `SELLABLE_UNIT_VERSION_CREATED` 审计记录。若组合未变化则拒绝，不能制造无意义版本。

## 8. T-04 固定排期、库存与人工占用

T-04 只发布场馆预先定义的固定时段。时间按场馆业务时区 `Asia/Shanghai` 表示为 `DATE + TIME`，数据库 UTC 约定仍适用于审计时间；开始和结束时间必须对齐 30 分钟，价格以人民币分保存。库存原子始终指向 T-03 已确定的 `sellable_unit_version_id` 和实际 `base_resource_id`，不通过出租类型推导冲突。

### 8.1 `schedule_version`

门店排期配置的不可变发布版本。草稿可整体修改；发布后不可原地修改，后续修改必须创建新的草稿版本。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `store_id` | `BIGINT UNSIGNED` | 所属门店 |
| `version_no` | `INT UNSIGNED` | 按门店从 1 递增 |
| `version_status` | `VARCHAR(16)` | `DRAFT`、`PUBLISHED`、`RETIRED` |
| `effective_from` | `DATE` | 发布生效日期 |
| `effective_to` | `DATE NULL` | 被下一个发布版本替代时写入前一日；草稿为空 |
| `published_at` | `DATETIME NULL` | 发布 UTC 时间 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (store_id, version_no)`、`UNIQUE (store_id, effective_from)`；外键引用 `store.id`。发布新版本时，服务端锁定同门店已发布的最后版本，要求新 `effective_from` 晚于其开始日期，并把旧版本的 `effective_to` 写为新日期前一天。因此按日期最多命中一个已发布版本。

### 8.2 `weekly_schedule_slot`

排期版本内每周重复的固定可售时段。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `schedule_version_id` | `BIGINT UNSIGNED` | 所属排期版本 |
| `week_day` | `TINYINT UNSIGNED` | `1` 周一至 `7` 周日 |
| `sellable_unit_version_id` | `BIGINT UNSIGNED` | 发布时的明确可售单元版本 |
| `start_time`、`end_time` | `TIME` | 左闭右开时段，均对齐 30 分钟，结束晚于开始 |
| `price_fen` | `INT UNSIGNED` | 整段总价，单位分，不按原子数量换算 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

### 8.3 `special_date_schedule` 与 `special_date_slot`

特殊日期完整覆盖同日周模板。一个特殊日期头记录的类型为 `CLOSED` 或 `OVERRIDE`：`CLOSED` 没有子时段，`OVERRIDE` 至少有一个 `special_date_slot`，当天只生成这些子时段。

| 表 | 关键字段 | 规则 |
| --- | --- | --- |
| `special_date_schedule` | `schedule_version_id`、`schedule_date`、`special_type` | `UNIQUE (schedule_version_id, schedule_date)`；日期不得早于版本生效日期 |
| `special_date_slot` | `special_date_id`、`sellable_unit_version_id`、`start_time`、`end_time`、`price_fen` | 与周模板使用相同的 30 分钟、价格和资源冲突规则 |

两张表均有第 2 节的公共审计及删除字段。特殊日期类型不使用“在周模板上增量叠加”的语义，避免节假日价格和闭馆配置同时执行。

### 8.4 `inventory_slot`、`inventory_atom` 与 `inventory_slot_atom`

`inventory_slot` 是某一天由周模板或特殊日期生成的固定可售实例；`inventory_atom` 是实际参与冲突和后续订单锁定的基础资源 30 分钟原子；`inventory_slot_atom` 连接二者。一个基础资源原子可同时被全场和相应半场等多个可售实例引用，从而让用户看到多个选择，但它只能被一个人工占用或线上订单最终持有。

| 表 | 关键字段 | 规则 |
| --- | --- | --- |
| `inventory_slot` | `store_id`、`schedule_version_id`、`sellable_unit_version_id`、`inventory_date`、`start_time`、`end_time`、`price_fen`、`source_type` | 来源为 `WEEKLY` 或 `SPECIAL`；记录创建它的模板子时段 ID；同一来源和日期幂等唯一 |
| `inventory_atom` | `store_id`、`base_resource_id`、`inventory_date`、`start_time`、`inventory_status`、`manual_block_id` | 每条记录代表一个基础资源的一个 30 分钟原子；`UNIQUE (base_resource_id, inventory_date, start_time)` 是全场/半场冲突的最终约束 |
| `inventory_slot_atom` | `inventory_slot_id`、`inventory_atom_id` | 固定可售实例与基础资源原子的多对多关联；`UNIQUE (inventory_slot_id, inventory_atom_id)` 防止重复关联 |

`inventory_status` 预留 `AVAILABLE`、`PAYMENT_LOCKED`、`PROCESSING`、`UNKNOWN`、`BOOKED`、`INTERNAL_BLOCKED`、`RECONCILE_PENDING`。T-04 只创建 `AVAILABLE` 和人工占用产生的 `INTERNAL_BLOCKED`；后续切片才可写支付和订单状态。

排期允许全场与 A/B 半场等共享基础资源的可售实例在同一时段同时发布；同一可售单元版本不得发布重叠时段。重新生成窗口内库存时，只清理全部映射原子均为 `AVAILABLE` 或 `RECONCILE_PENDING` 且没有人工占用的库存槽关联；无关联且可重建的原子才被物理清理。任何 `INTERNAL_BLOCKED`、支付占用或已预约原子均保留。人工占用取消时若其排期版本已不是该日期当前版本，原子转为 `RECONCILE_PENDING`，等待后续滚动任务重建。

### 8.5 `manual_block`

人工占用是对已生成固定可售实例的场馆内部占用事实，不记录线下收款或退款。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `block_no` | `VARCHAR(40)` | 全局唯一业务编号 |
| `store_id` | `BIGINT UNSIGNED` | 所属门店 |
| `inventory_slot_id` | `BIGINT UNSIGNED` | 被占用的固定可售实例 |
| `sellable_unit_version_id` | `BIGINT UNSIGNED` | 占用时的可售单元版本快照 |
| `block_type` | `VARCHAR(24)` | `OFFLINE_RESERVATION`、`MAINTENANCE`、`EVENT`、`SELF_USE` |
| `contact_name`、`contact_phone` | `VARCHAR NULL` | 线下预约联系人可选；不作为用户身份或收款记录 |
| `remark` | `VARCHAR(255) NULL` | 占用备注 |
| `block_status` | `VARCHAR(16)` | `ACTIVE`、`CANCELLED` |
| `cancelled_at` | `DATETIME NULL` | 取消 UTC 时间 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

创建人工占用时按 `base_resource_id, inventory_date, start_time` 的稳定顺序锁定全部原子，全部为 `AVAILABLE` 才插入占用并改为 `INTERNAL_BLOCKED`。仅未来占用可取消，取消时只比较更新仍归属该 `manual_block_id` 的原子。

T-04 不提供任意非排期时间的人工占用，必须从库存日历中选择固定可售实例；完整线下订单和任意时间封锁属于后续范围。

## 9. T-05 交易门禁与暂停 blocker

交易门禁把“是否允许新增预约”与经营主体、门店资料状态分离。门禁只阻止新增支付占用，不删除、取消或释放既有订单、人工占用及处于 `PAYMENT_LOCKED`、`PROCESSING`、`UNKNOWN`、`BOOKED` 的库存。

### 9.1 `transaction_gate`

每个经营主体和门店各有一条长期存在的门禁聚合行。预约创建事务必须按“主体门禁、门店门禁”的稳定顺序 `FOR UPDATE` 锁定，并确认两者均为 `OPEN` 后才可以锁库存；暂停/恢复操作锁定同一行，因此由数据库提交顺序决定预约与暂停竞争的结果。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `scope_type` | `VARCHAR(16)` | `ORGANIZATION`、`STORE` |
| `scope_id` | `BIGINT UNSIGNED` | 对应主体或门店 ID |
| `gate_status` | `VARCHAR(16)` | `OPEN`、`BLOCKED`；由活动 blocker 派生维护 |
| `gate_version` | `BIGINT UNSIGNED` | 每次 blocker 新增或解除递增，便于后续订单持有门禁快照 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (scope_type, scope_id)`。新建主体和门店必须在同一事务内创建各自的门禁行；V5 为已有记录补齐门禁行。`scope_id` 为多态引用，服务端创建和锁定时根据 `scope_type` 校验目标存在，避免无效外键组合。

### 9.2 `transaction_blocker`

暂停原因是独立事实，不通过覆盖门禁状态相互抵消。一个原因解除时只关闭自己的 blocker；门禁只有在该范围内不存在活动 blocker 时恢复 `OPEN`。预约要求主体和门店两级门禁都 `OPEN`。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `transaction_gate_id` | `BIGINT UNSIGNED` | 所属门禁聚合行 |
| `scope_type`、`scope_id` | 与门禁一致 | 用于业务唯一约束和范围查询 |
| `cause_type` | `VARCHAR(32)` | `MANUAL`、`PLATFORM_STATUS`、`BALANCE_INSUFFICIENT`、`RISK` |
| `cause_id` | `BIGINT UNSIGNED` | 原因实体 ID；手工暂停使用发起管理端账号 ID，平台状态切换使用 `0` |
| `blocker_status` | `VARCHAR(16)` | `ACTIVE`、`RELEASED` |
| `reason` | `VARCHAR(255)` | 必填暂停说明，不记录敏感信息 |
| `released_at` | `DATETIME NULL` | 解除 UTC 时间 |
| `released_operator_id` | `BIGINT UNSIGNED NULL` | 解除人；系统解除可为 `0` |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

约束：`UNIQUE (scope_type, scope_id, cause_type, cause_id)`。手工暂停由当前账号作为 `cause_id`，允许平台与场馆管理员分别保留独立原因；`PLATFORM_STATUS` 由现有主体/门店状态接口写入并使用 `cause_id = 0`。同一原因解除后再次暂停会重新激活其 blocker 并追加审计记录，不能创建第二个同原因 blocker 或影响其他活动原因。

## 10. T-06 小程序身份与待支付预约

### 10.1 `mini_user` 与 `mini_session`

小程序用户的提供方标识只保存哈希。首期 `MOCK_WECHAT` 适配器在开发和测试环境接受 `mock:` 授权码；真实微信授权码交换在真实环境适配器中替换，不把 AppID、密钥或原始 `openid` 写入数据库、日志或接口响应。

`mini_session` 保存访问令牌和刷新令牌的哈希。访问令牌有效期 2 小时，刷新令牌有效期 30 天；刷新时撤销旧会话并签发新的一对令牌，旧刷新令牌不可重放。两张表均有第 2 节固定字段。

### 10.2 `booking_order`

订单在支付前即固定门店、时段、可售单元版本、价格和费率快照，避免后续排期或费率变化影响已提交预约。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `order_no` | `VARCHAR(40)` | 全局唯一订单编号 |
| `mini_user_id` | `BIGINT UNSIGNED` | 预约用户 |
| `organization_id`、`store_id` | `BIGINT UNSIGNED` | 下单时主体、门店快照 |
| `inventory_slot_id`、`schedule_version_id`、`sellable_unit_version_id` | `BIGINT UNSIGNED` | 固定场次、排期和组合资源版本快照 |
| `inventory_date`、`start_time`、`end_time` | `DATE`、`TIME` | 预约时段快照 |
| `price_fen`、`service_rate_basis_points` | `INT UNSIGNED` | 订单金额和服务费率快照 |
| `contact_name`、`contact_phone` | `VARCHAR` | 预约联系人；管理端默认脱敏，完整查看在后续订单查询接口审计 |
| `privacy_policy_version`、`privacy_consented_at`、`privacy_consent_scene` | `VARCHAR`、`DATETIME` | 下单时隐私同意事实 |
| `order_status` | `VARCHAR(24)` | `PENDING_PAYMENT`、`PAYMENT_PROCESSING`、`PAYMENT_UNKNOWN`、`CONFIRMED`、`CANCELLED`、`COMPLETED` |
| `hold_expires_at` | `DATETIME` | 待支付占用安全到期时间 |
| 公共审计及删除字段 | 见第 2 节 | 必填，默认值见公共约定 |

### 10.3 库存占用、用户配额与来源压力桶

`inventory_atom.occupying_order_id` 和 `hold_expires_at` 记录当前订单占用。创建订单必须依次锁定主体门禁、门店门禁、用户配额行和按 `base_resource_id, start_time` 排序的所有关联原子；只有全部原子为 `AVAILABLE` 且不属于人工占用或其他订单时，才能将其全部改为 `PAYMENT_LOCKED` 并关联新订单。

`user_booking_quota` 是每用户唯一可锁定配额行：最多同时 2 笔待支付占用；后续支付关闭、取消和到期任务必须在同一订单事务中安全减计数。`booking_request_bucket` 对设备标识和来源地址哈希建立 10 分钟桶，每桶最多 20 次创建请求；订单表自身再限制同一用户 10 分钟最多创建 5 次。三个表都使用第 2 节固定字段。

公开场馆查询和用户下单只接受当前已发布排期版本、主体与门店门禁均 `OPEN`、资料状态均 `ACTIVE`、并且关联原子全部 `AVAILABLE` 的固定时段。排期、人工封场、线上订单占用和门禁变更都不能产生旧价格或冲突资源短暂重新可售的窗口。

## 11. T-07 支付事实与可靠投递

`payment_order` 与 `booking_order` 为一对一关系，固定支付金额、币种和 Provider；首期 `provider` 只能为 `MOCK`。状态只能单向推进 `CREATED`、`PROCESSING`、`SUCCEEDED`、`FAILED`、`CLOSED`、`UNKNOWN`，金额和订单关联不可改写。

`payment_event_inbox` 以 `UNIQUE(provider, provider_event_no)` 消除 Provider 重复或乱序事件。`payload_json` 保留受控 Mock 事件载荷，`process_result` 记录 `PENDING`、`APPLIED`、`IGNORED`、`REJECTED`。回调、支付查询与关单争用同一支付/订单事务锁，`UNKNOWN` 不允许释放库存。

`payment_command_outbox` 保存 `CREATE`、`CLOSE` 命令；`UNIQUE(payment_order_id, command_type)` 防止同一支付单重复发起同类命令。三表均有第 2 节固定字段。

## 12. T-08 取消、退款与库存释放

### 12.1 `booking_cancellation`

每个订单最多一条取消事实，`UNIQUE(booking_order_id)`。记录取消类型、原因、发起方和发起方 ID；用户取消、场馆取消、支付失败和支付过期均与订单行、支付行、库存原子在同一事务内处理。取消不删除订单，不允许恢复。

### 12.2 `refund_order` 与 `refund_command_outbox`

`refund_order` 固定原订单、原支付单、退款来源、范围、金额和币种。首期只支持 `FULL` 和 `CNY`，退款状态为 `REFUND_PENDING`、`REFUND_PROCESSING`、`REFUND_SUCCEEDED`、`REFUND_UNKNOWN`。`UNIQUE(refund_source_type, refund_source_id, refund_scope)` 确保同一取消或晚到支付事实最多产生一笔退款义务。

`refund_command_outbox` 与退款单一对一，唯一约束覆盖退款单和命令编号，保证进程在取消事务后异常退出时仍可继续投递。退款成功不恢复订单或库存；若原排期版本过期，释放的原子保持 `RECONCILE_PENDING`。

## 13. T-09 店员、凭证与核销

`venue_employee` 按主体和门店归属店员，状态只有 `ACTIVE`、`DISABLED`。停用同时撤销活动绑定、店员会话和未使用绑定码，历史核销不删除。

`employee_binding_code` 只保存一次性绑定码哈希、签发管理员、状态和 10 分钟有效期。`employee_wechat_binding` 用 Provider 身份哈希保存绑定历史，借助两个仅在活动绑定时非空的生成键，分别保证一个店员只能有一个活动身份、一个身份只能绑定一个活动店员。

`staff_session` 只保存访问和刷新令牌哈希，刷新时轮换并撤销旧令牌。`booking_verification_credential` 保存二维码哈希或店铺范围短码哈希，`UNIQUE(store_id, active_short_code_hash)` 防止同店活动短码碰撞；二维码与短码原文只在签发响应中出现。

`booking_verification` 以 `UNIQUE(booking_order_id)` 保存首次成功核销事实。`verification_attempt` 追加保存成功、重复和拒绝尝试及失败原因，既用于店员本人记录，也用于异常审计。订单自动完成任务只更新尚未取消订单的完成时间，不改写核销事实。

## 14. T-10 不可变服务费与月账单

`service_fee_ledger` 是不可变分录。订单完成满 24 小时按下单时费率快照生成 `ACCRUAL`；退款成功后按归属账期生成负向 `ADJUSTMENT`。`UNIQUE(source_type, source_id)` 防止定时任务或退款回调重复计提。分录可在生成账单时关联 `monthly_bill_id`，历史归属不被后续计算改写。

`monthly_bill` 以 `UNIQUE(store_id, billing_period)` 固定每门店每账期一张账单，`billing_period` 必须为当月第一天。状态机为 `DRAFT -> ISSUED -> PAID`，签发后到期可进入 `OVERDUE`，付款后仍只允许进入 `PAID`。

`bill_payment_record` 一张账单最多一条线下收款记录，`UNIQUE(monthly_bill_id)`；记录平台管理员、实收金额、时间和凭证备注。收款金额必须与应付金额匹配，账单金额和已归属分录均不做静默回写。

## 15. T-11/T-12 终端与验收边界

PC 管理端和小程序端不新增业务事实表，均消费上述服务端接口。所有列表继续以 `current`、`size`、`total`、`records` 置于 `data` 中，前三个计数为字符串；所有表均保留第 2 节审计与逻辑删除字段。运行时验收脚本以创建的独立测试主体、场馆、用户、订单、支付、退款、员工、核销和账单事实完成闭环，避免依赖预置数据。
