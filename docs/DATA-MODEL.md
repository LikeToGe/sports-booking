# 约场数据模型

## 表

| 表 | 用途 |
|---|---|
| `s_venue` | 场馆公开资料、经纬度、预约规则、当前服务费率和状态 |
| `s_admin_account` | 平台/场馆管理员账号，场馆管理员直接关联场馆 |
| `s_user_account` | 模拟微信身份、联系人和手机号 |
| `s_sport_type` | 平台类型 `owner_venue_id IS NULL`；场馆类型保存所属场馆 |
| `s_court` / `s_court_sport` | 独占场地、主图及其支持的运动 |
| `s_weekly_slot` | 星期、开始/结束、运动和价格模板 |
| `s_session_slot` | 具体日期的可预约场次及 OPEN/HELD/BOOKED/CLOSED 状态 |
| `s_booking` | 预约主体和场馆/场地/运动/时间/价格/服务费率快照 |
| `s_payment` / `s_refund` | 当前 Mock、未来可替换真实微信单号的支付和全额退款记录 |
| `s_employee` | 场馆员工及一次性绑定码 |
| `s_verification` | 成功核销记录，一笔预约最多一次 |
| `s_notification` | 模拟订阅消息触发记录 |

## 预约状态

```text
PENDING_PAYMENT ──模拟支付──> CONFIRMED ──核销──> COMPLETED
       │                          │
       └──超时──> CANCELLED       ├──取消──> CANCELLED
                                  └──场次结束──> NO_SHOW
```

## 具体场次状态

```text
OPEN ──创建线上预约──> HELD ──支付──> BOOKED
 │                       │             │
 ├──关闭──> CLOSED       └──超时──> OPEN/CLOSED
 └──线下预约──────────────────> BOOKED
```

取消后仅当周场次仍启用且尚未开始时重新进入 `OPEN`，否则进入 `CLOSED`。

## 一致性

- 创建预约前使用 `SELECT ... FOR UPDATE` 锁定 `s_session_slot`。
- 预约写入后更新同一行状态和 `hold_booking_id`，整个操作在同一事务内。
- 订单快照不被场馆资料、价格或周场次后续修改覆盖。
- 服务端从后台会话或小程序会话推导场馆、用户和员工归属，不接受客户端传入归属范围。
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
- `s_sport_type(scope_venue_id, normalized_name)` 唯一，阻止平台/场馆范围内重名。
