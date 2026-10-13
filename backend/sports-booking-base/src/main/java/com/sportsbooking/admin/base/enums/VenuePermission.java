package com.sportsbooking.admin.base.enums;

/** 场馆后台固定权限目录。 */
public enum VenuePermission {
    DASHBOARD_VIEW("查看工作台", "工作台"),
    BOOKING_VIEW("查看预约", "预约"),
    BOOKING_EDIT("编辑预约", "预约"),
    BOOKING_VERIFY("核销预约", "预约"),
    ONLINE_BOOKING_CANCEL("取消线上预约", "预约"),
    OFFLINE_PAYMENT_STATUS_EDIT("编辑线下收款状态", "预约"),
    CUSTOMER_VIEW("查看顾客", "会员"),
    CUSTOMER_OPERATIONS_EDIT("编辑顾客经营", "会员"),
    MEMBER_FUNDS_VIEW("查看会员资金", "会员"),
    MEMBER_RECHARGE("会员充值", "会员"),
    MEMBER_RECHARGE_REVERSE("撤销充值", "会员"),
    RECHARGE_PLAN_EDIT("编辑充值档位", "会员"),
    REFUND_RETRY("重试退款", "财务"),
    COURT_VIEW("查看场地", "场馆"),
    COURT_EDIT("编辑场地", "场馆"),
    SCHEDULE_VIEW("查看排期", "场馆"),
    SCHEDULE_EDIT("编辑排期", "场馆"),
    SESSION_VIEW("查看场次", "场馆"),
    SESSION_EDIT("编辑场次", "场馆"),
    EMPLOYEE_VIEW("查看员工", "权限"),
    EMPLOYEE_EDIT("编辑员工", "权限"),
    ROLE_MANAGE("管理角色", "权限"),
    VENUE_PROFILE_VIEW("查看场馆资料", "场馆"),
    VENUE_PROFILE_EDIT("编辑场馆资料", "场馆"),
    REVIEW_MANAGE("管理评价回复", "内容运营"),
    POST_PUBLISH("发布场馆动态", "内容运营"),
    REPORT_VIEW("查看报表", "报表"),
    REPORT_EXPORT("导出报表", "报表");

    private final String displayName;
    private final String group;

    VenuePermission(String displayName, String group) {
        this.displayName = displayName;
        this.group = group;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGroup() {
        return group;
    }
}
