# 约场数据模型

## 表

| 表 | 用途 |
|---|---|
| `s_venue` | 场馆公开资料、经纬度、预约规则、当前服务费率和状态 |
| `s_admin_account` | 平台/场馆管理员账号，场馆管理员直接关联场馆 |
| `s_user_account` | 模拟微信身份、联系人和手机号 |
| `s_venue_customer` | 用户与单个场馆的顾客/会员关系、场馆内会员码和会员激活时间 |
| `s_sport_type` | 平台类型 `owner_venue_id IS NULL`；场馆类型保存所属场馆 |
| `s_court` / `s_court_sport` | 独占场地、主图及其支持的运动 |
| `s_weekly_slot` | 星期、开始/结束、运动和价格模板 |
| `s_session_slot` | 具体日期的可预约场次、OPEN/HELD/BOOKED/CLOSED 状态及人工覆盖标记 |
| `s_booking` | 预约主体、场馆顾客关联、独立收款状态和场馆/场地/运动/时间/价格/服务费率快照 |
| `s_booking_payment_status_log` | 线下登记收款状态每次修改前后值、可信操作者、原因和时间 |
| `s_booking_reschedule_log` | 线下预约同价改期前后具体场次、可信操作者、原因和时间 |
| `s_recharge_plan` | 场馆充值档位及实付、赠送、有效天数和启停状态 |
| `s_member_recharge` | 充值业务号、档位快照、渠道、操作者、幂等键、结果和整笔撤销事实 |
| `s_member_balance_lot` | 每笔充值形成的实付/赠送余额批次、可用/冻结额、到期和撤销状态 |
| `s_booking_balance_hold` | 预约按余额批次固定的冻结、消费或释放明细 |
| `s_member_balance_flow` | 充值、冻结、消费、释放、退款、到期和撤销的不可变幂等流水 |
| `s_payment` | 线上预约的单一外部支付差额；全余额预约没有该记录 |
| `s_refund` | 真实外部支付对应的原业务号退款及处理、失败、关闭和重试状态 |
| `s_employee` | 场馆员工及一次性绑定码 |
| `s_venue_role` | 场馆自定义普通员工角色及启停状态 |
| `s_venue_role_permission` | 角色与后端固定权限码的多对多关系 |
| `s_employee_role` | 普通员工与场馆角色的多对多关系 |
| `s_verification` | 成功核销记录，一笔预约最多一次 |
| `s_notification` | 站内消息及关联业务；预约关联可空 |
| `s_notification_delivery` | Mock 或未来真实外部消息渠道的独立投递结果 |

## 预约状态

```text
PENDING_PAYMENT ──全余额/支付成功──> CONFIRMED ──核销──> COMPLETED
       │                              │
       ├──明确失败/超时──> CANCELLED  ├──取消──> CANCELLED
       └──结果未知──> PENDING_PAYMENT └──场次结束──> NO_SHOW
```

线下预约不进入核销或未到场判断，具体场次结束后进入 `ENDED_UNTRACKED`。

## 预约收款状态

线上预约整体收款状态独立于履约状态：

```text
PAYING -> PAID -> REFUNDING -> REFUNDED
   │                   │
   └-> PAYMENT_FAILED  └-> REFUND_FAILED -> REFUNDING
```

外部支付使用 `PENDING / SUCCESS / FAILED / CLOSED`；外部退款使用 `PROCESSING / SUCCESS / FAILED / CLOSED`。超时关闭后晚到的支付成功不会恢复预约或场次，而是保留真实支付事实并使用同一退款业务号收敛。

线下预约只记录人工确认的收款事实：

```text
UNRECORDED -> UNPAID -> PAID
          └----------> PAID

CANCELLED + PAID -> REFUNDED
```

每次线下状态修改必须写 `s_booking_payment_status_log`；取消线下预约本身不自动改变登记状态，也不生成支付、退款或余额流水。

## 具体场次状态

```text
OPEN ──创建线上预约──> HELD ──支付──> BOOKED
 │                       │             │
 ├──关闭──> CLOSED       └──超时──> OPEN/CLOSED
 └──线下预约──────────────────> BOOKED
```

取消后仅当周场次仍启用且尚未开始时重新进入 `OPEN`，否则进入 `CLOSED`。

## 一致性

- 创建预约按完整 Redis key 取得具体场次和会员余额锁，再使用 `SELECT ... FOR UPDATE` 锁定 `s_session_slot` 与可用余额批次。
- 用户首次在场馆创建线上预约时，以 `(venue_id, user_id)` 幂等创建 `s_venue_customer`；同一用户跨场馆分别建档。
- 预约写入、场次占用、按批次冻结和固定支付组成在同一事务内；全余额预约同事务消费并确认，且不创建 `s_payment`。
- 有外部差额时，每笔预约只创建一条支付记录；Adapter 发起、查单和退款发生在数据库事务与 Redis 锁外，结果再按预约、场次和会员余额锁进入事务收敛。
- 消费优先级固定为有效赠送余额按 `expires_at,id`，再按实付批次 `id`；退款和释放只按原 `s_booking_balance_hold` 恢复。
- 已过期赠送额在释放或退款时立即写失效流水，不回到可用余额；到期和 7 天提醒均以批次幂等键防重。
- 充值、预约创建、余额流水、支付、退款和提醒均以数据库唯一约束兜底幂等；所有余额变化保留批次和不可变流水。
- 整笔充值只在从未发生消费且批次未冻结时撤销；线下渠道还必须记录资金已自行处理确认。
- 订单快照不被场馆资料、价格或周场次后续修改覆盖。
- 任意状态的具体场次均可人工编辑；编辑后 `manual_override = 1`，并以 `template_slot_date` 保留原模板日期，周场次同步不再覆盖、重复生成或随模板停用关闭该记录。
- 修改具体场次不改写已有预约的场馆、场地、运动、时间或价格快照。
- 任意状态的线下预约可修改联系人姓名和手机号，线上预约联系人不允许由场馆后台修改。
- 批量线下预约先对日期去重，按完整 Redis key 和数据库 ID 排序加锁；任一日期冲突时整批回滚并返回全部冲突日期。
- 线下改期同时锁定原、新具体场次，只允许同馆、未开始且同价的目标场次，并保持登记收款状态不变。
- 服务端从后台会话或小程序会话推导场馆、用户和员工归属，不接受客户端传入归属范围。
- 普通员工后台会话只保存 `employee_id + venue_id`；每个请求重新查询员工绑定、启停、有效角色和权限并集，角色调整、停用或解绑在下一请求生效。
- `s_booking_payment_status_log`、`s_booking_reschedule_log` 的管理员与员工操作者必须恰有一个非空；充值和余额流水允许用户自助时两者均空。
- 角色权限码不落独立字典表，由后端 `VenuePermission` 固定枚举校验；失效角色不参与权限并集。
- 所有具体时间以 UTC 存储和传输，`slot_date`、周场次和页面展示使用 `Asia/Shanghai`。

## 平台服务费

- `s_venue.service_fee_rate_basis_points` 保存场馆当前服务费率，范围为 `0～3000`；`525` 表示 `5.25%`。
- 线上预约创建时把当前费率写入 `s_booking.service_fee_rate_basis_points`；线下及历史预约保持 `0`。
- 月度结算只查询费率快照大于 `0`、来源为 `ONLINE` 且状态为 `COMPLETED` 或 `NO_SHOW` 的预约。
- 账期按 `end_at` 在 `Asia/Shanghai` 所属自然月确定。
- 服务费使用 `amount_fen × service_fee_rate_basis_points` 得到整数微元后精确汇总，不舍入或截断。
- 当前阶段不保存服务费分录或月账单，月度结算由预约快照实时生成。

## 索引

- `s_session_slot(court_id, start_at, end_at)` 唯一，阻止同一场地重复生成相同场次。
- `s_session_slot(venue_id, slot_date, status)` 支撑详情与日程。
- `s_booking(user_id, status, created_at)` 支撑我的预约与待支付限制。
- `s_booking(venue_id, start_at, status)` 支撑今日场次与未到场任务。
- `s_venue_customer(venue_id, user_id)` 唯一，阻止同馆重复建档；`(venue_id, member_code)` 唯一，保证会员码只需在场馆范围内识别。
- `s_booking(venue_id, payment_status, start_at)` 支撑日视图与线下登记收款筛选。
- `s_member_recharge(venue_customer_id, client_idempotency_key)` 唯一，保证同一会员充值重试返回原结果。
- `s_member_balance_lot(source_recharge_id, type)` 唯一，保证每笔充值最多形成一个实付和一个赠送批次。
- `s_booking_balance_hold(booking_id, lot_id)` 唯一，固定预约与余额批次组成。
- `s_member_balance_flow(idempotency_key)` 唯一，防止重复通知、取消、到期或撤销追加重复流水。
- `s_payment(booking_id)`、`s_refund(booking_id)` 和 `s_refund(payment_id)` 唯一，保证单一外部支付与退款。
- `s_notification(idempotency_key)` 与 `s_notification_delivery(notification_id, channel)` 唯一，保证提醒与投递可重放。
- `s_employee(venue_id, user_id)` 唯一，允许同一微信用户分别绑定多个场馆，但禁止同一场馆重复绑定。
- `s_venue_role(venue_id, name)` 唯一；角色权限和员工角色均以复合主键阻止重复授权。
- 两类预约审计表均按 `(booking_id, created_at)` 查询完整变更轨迹。
- `s_sport_type(scope_venue_id, normalized_name)` 唯一，阻止平台/场馆范围内重名。
