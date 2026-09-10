package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.enums.NotificationStatus;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.base.po.Notification;
import com.sportsbooking.admin.base.mapper.NotificationMapper;
import com.sportsbooking.admin.biz.service.NotificationService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 功能描述: 模拟消息记录实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class NotificationServiceImpl implements NotificationService {
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 功能描述: 记录预约消息触发，线下预约没有用户时跳过。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void record(Booking booking, NotificationType type) {
        // 线下预约没有小程序用户，无法发送订阅消息，直接跳过记录。
        if (booking.userId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.userId = booking.userId;
        notification.bookingId = booking.id;
        notification.type = type;
        notification.status = NotificationStatus.MOCK_SENT;
        notification.createdAt = LocalDateTime.now();
        notificationMapper.insert(notification);
    }
}
