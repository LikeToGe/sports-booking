package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.enums.NotificationType;

/**
 * 功能描述: 记录预约相关消息触发；真实订阅消息接入前写入模拟发送状态。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface NotificationService {
    /**
     * 功能描述: 记录一条预约消息触发；线下预约没有用户时跳过。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    void record(Booking booking, NotificationType type);
}
