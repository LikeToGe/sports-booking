# API 返回结构约定

## 1. 统一外层结构

所有 HTTP 接口，无论成功、分页、参数校验失败还是业务拒绝，都使用以下四个外层字段。不得混用裸数组、裸对象、`message`、`success`、`requestId` 等其他外层结构。

```json
{
  "data": {},
  "code": 1,
  "serviceCode": "10000",
  "msg": "业务处理成功"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data` | object, array 或 `null` | 当前接口的业务数据；列表接口使用第 2 节的分页对象，非列表接口返回其实际业务对象或 `null`。 |
| `code` | integer | 通用处理结果码。首期沿用既有约定：`1` 表示业务处理成功；其他取值在错误码表冻结。 |
| `serviceCode` | string | 统一服务码，固定返回 `10000`。接口类型仅通过请求路径和 `data` 的业务结构区分。 |
| `msg` | string | 面向调用方的处理结果说明。 |

HTTP 状态码表达协议层结果；业务成功或业务拒绝以 `code` 为准，`serviceCode` 始终返回统一值 `10000`。

## 2. 分页返回

你提供的 `data.current`、`data.size`、`data.total`、`data.records` 是**分页接口专用结构**。列表接口请求使用 `page`、`pageSize`；非列表接口不返回这些字段。

为兼容既有调用约定，分页对象中的 `current`、`size`、`total` 输出为字符串，`records` 为数组。

```json
{
  "data": {
    "current": "1",
    "size": "10",
    "total": "51",
    "records": [
      {
        "id": "208",
        "city": "上海市-上海市",
        "guid": "F2D04B04-89B7-412F-9E6B-EB0B8765B39B",
        "authorizeStatus": 2,
        "authorizeTime": null
      }
    ]
  },
  "code": 1,
  "serviceCode": "10000",
  "msg": "业务处理成功"
}
```

- 空分页仍返回完整分页 `data`：`current`、`size`、`total` 和空数组 `records`。
- 写操作无额外载荷时返回 `data: null`。
- 标识符、业务编号和分页计数遵循此约定输出字符串；金额继续使用明确的 `...Fen` 整数值，不使用浮点数。

## 3. 详情查询返回

详情查询不套用分页字段，直接在 `data` 返回实体字段。以下为订单详情结构：

```json
{
  "data": {
    "id": "1132",
    "orderNo": "JD202608051355210056",
    "createTime": "2026-08-05 13:55:22",
    "payTime": null,
    "deliveryTime": null
  },
  "code": 1,
  "serviceCode": "10000",
  "msg": "业务处理成功"
}
```

- `id`、`orderNo` 等标识字段输出字符串。
- 时间字段按现有服务格式输出 `yyyy-MM-dd HH:mm:ss`；尚未发生的业务时间返回 `null`，不返回空字符串。
- 分页、详情、写操作均返回统一的 `serviceCode: "10000"`。

## 4. 写操作与错误返回

写操作和错误响应继续使用第 1 节的外层结构，但不套用分页字段。错误码的具体取值及字段校验明细，在后续错误码表与 `docs/openapi.yaml` 中按现有服务规范冻结。
