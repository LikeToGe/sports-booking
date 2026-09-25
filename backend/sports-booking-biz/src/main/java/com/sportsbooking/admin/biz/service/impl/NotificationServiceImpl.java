package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.enums.NotificationStatus;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.base.po.Notification;
import com.sportsbooking.admin.base.mapper.NotificationMapper;
import com.sportsbooking.admin.base.mapper.NotificationDeliveryMapper;
import com.sportsbooking.admin.base.po.NotificationDelivery;
import com.sportsbooking.admin.biz.service.NotificationService;
import com.sportsbooking.admin.biz.service.NotificationDeliveryAdapter;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import java.time.Instant;

/**
 * 功能描述: 模拟消息记录实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class NotificationServiceImpl implements NotificationService {
    private final NotificationMapper notificationMapper;
    private final NotificationDeliveryMapper deliveryMapper;
    private final NotificationDeliveryAdapter deliveryAdapter;

    public NotificationServiceImpl(NotificationMapper notificationMapper, NotificationDeliveryMapper deliveryMapper,
                                   NotificationDeliveryAdapter deliveryAdapter) {
        this.notificationMapper = notificationMapper;
        this.deliveryMapper = deliveryMapper;
        this.deliveryAdapter = deliveryAdapter;
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
        notification.businessType = "BOOKING";
        notification.businessId = booking.id;
        notification.title = type.getDescription();
        notification.content = type.getDescription();
        notification.createdAt = LocalDateTime.now();
        notificationMapper.insert(notification);
    }

    @Override
    public void recordBalanceExpiry(long userId, long lotId, int amountFen, Instant expiresAt) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.businessType = "BALANCE_LOT";
        notification.businessId = lotId;
        notification.type = NotificationType.BALANCE_EXPIRY_REMINDER;
        notification.status = NotificationStatus.MOCK_SENT;
        notification.title = "赠送余额即将到期";
        notification.content = amountFen + " 分赠送余额将于 " + expiresAt + " 到期";
        notification.idempotencyKey = "balance-lot:" + lotId + ":expiry-reminder";
        notification.createdAt = LocalDateTime.now();
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException exception) {
            return;
        }
        // 站内消息先独立落库，外部投递不占用资金事务或余额锁。
        String providerNo = deliveryAdapter.deliver(notification);
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.notificationId = notification.id;
        delivery.channel = "MOCK";
        delivery.status = "SUCCESS";
        delivery.providerNo = providerNo;
        delivery.createdAt = LocalDateTime.now();
        deliveryMapper.insert(delivery);
    }
}
