package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 服务端业务错误定义。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum ErrorCode {
    AGREEMENT_REQUIRED("AGREEMENT_REQUIRED", "请先接受当前社区规则与隐私说明"),
    ALREADY_BOUND("ALREADY_BOUND", "已绑定其他场馆"),
    BATCH_SLOT_CONFLICT("BATCH_SLOT_CONFLICT", "部分日期的场次不可预约"),
    CANCEL_CUTOFF("CANCEL_CUTOFF", "已超过可取消时间"),
    CREATE_LIMIT("CREATE_LIMIT", "预约操作过于频繁"),
    CONTENT_RATE_LIMIT("CONTENT_RATE_LIMIT", "内容操作过于频繁"),
    DAY_HAS_BOOKINGS("DAY_HAS_BOOKINGS", "当天已有预约"),
    DUPLICATE("DUPLICATE", "数据已存在"),
    FORBIDDEN("FORBIDDEN", "无权执行该操作"),
    HOLD_LOST("HOLD_LOST", "场次占位已失效"),
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", "会员余额不足"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务暂时不可用"),
    INVALID_ARGUMENT("INVALID_ARGUMENT", "参数不正确"),
    INVALID_CREDENTIAL("INVALID_CREDENTIAL", "核销凭证无效"),
    INVALID_METHOD("INVALID_METHOD", "核销方式不正确"),
    LOGIN_FAILED("LOGIN_FAILED", "用户名或密码错误"),
    NOT_CANCELLABLE("NOT_CANCELLABLE", "当前预约不可取消"),
    NOT_FOUND("NOT_FOUND", "数据不存在"),
    NOT_VERIFIABLE("NOT_VERIFIABLE", "预约不可核销"),
    OPERATION_IN_PROGRESS("OPERATION_IN_PROGRESS", "操作处理中"),
    OUTSIDE_VERIFY_WINDOW("OUTSIDE_VERIFY_WINDOW", "当前不在核销时间内"),
    PAYMENT_EXPIRED("PAYMENT_EXPIRED", "支付时间已结束"),
    PAYMENT_MISSING("PAYMENT_MISSING", "支付记录不存在"),
    RECHARGE_NOT_REVERSIBLE("RECHARGE_NOT_REVERSIBLE", "该笔充值不可撤销"),
    REVIEW_NOT_ELIGIBLE("REVIEW_NOT_ELIGIBLE", "当前预约不可评价"),
    REVIEW_WINDOW_CLOSED("REVIEW_WINDOW_CLOSED", "评价时间已结束"),
    PENDING_LIMIT("PENDING_LIMIT", "待支付预约数量已达上限"),
    SESSION_UNAVAILABLE("SESSION_UNAVAILABLE", "登录服务暂时不可用"),
    SLOT_BUSY("SLOT_BUSY", "场次正在使用中"),
    SLOT_CONFLICT("SLOT_CONFLICT", "场次时间冲突"),
    SLOT_UNAVAILABLE("SLOT_UNAVAILABLE", "场次不可预约"),
    SPORT_EXISTS("SPORT_EXISTS", "运动类型已存在"),
    STAFF_REQUIRED("STAFF_REQUIRED", "当前用户不是有效场馆员工"),
    UNAUTHORIZED("UNAUTHORIZED", "请重新登录"),
    UPLOAD_FAILED("UPLOAD_FAILED", "封面图保存失败"),
    UGC_DISABLED("UGC_DISABLED", "内容功能暂未开放"),
    UGC_UPLOAD_FAILED("UGC_UPLOAD_FAILED", "媒体保存失败"),
    USER_DISABLED("1001", "用户不可用");

    private final String key;
    private final String description;

    ErrorCode(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }
}
