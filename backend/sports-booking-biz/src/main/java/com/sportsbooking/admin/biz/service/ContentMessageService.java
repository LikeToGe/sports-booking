package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.NotificationStatus;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.base.mapper.NotificationMapper;
import com.sportsbooking.admin.base.mapper.NotificationDeliveryMapper;
import com.sportsbooking.admin.base.po.Notification;
import com.sportsbooking.admin.base.po.NotificationDelivery;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.ContentNotificationVO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 站内消息在业务事务中落库，Mock 外部投递由任务在事务外执行并幂等记账。 */
@Service
public class ContentMessageService {
    private final NotificationMapper mapper;
    private final NotificationDeliveryMapper deliveries;
    private final NotificationDeliveryAdapter adapter;

    public ContentMessageService(NotificationMapper mapper, NotificationDeliveryMapper deliveries,
                                  NotificationDeliveryAdapter adapter) {
        this.mapper = mapper;
        this.deliveries = deliveries;
        this.adapter = adapter;
    }

    public void record(long userId, String businessType, long businessId, String key, String title, String body) {
        Notification row = new Notification();
        row.userId = userId;
        row.businessType = businessType;
        row.businessId = businessId;
        row.type = NotificationType.CONTENT_ACTIVITY;
        row.status = NotificationStatus.PENDING_DELIVERY;
        row.title = title;
        row.content = body;
        row.idempotencyKey = "content:" + key;
        row.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        try { mapper.insert(row); } catch (DuplicateKeyException duplicate) { }
    }

    public List<ContentNotificationVO> list(long userId) {
        return mapper.selectUserNotifications(userId).stream().map(ContentNotificationVO::from).toList();
    }

    public void markRead(long userId, long id) {
        Notification row = mapper.selectById(id);
        if (row == null || row.userId != userId) throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        mapper.markRead(id, userId);
    }

    @Scheduled(fixedDelay = 10000)
    public void deliverPending() {
        for (Notification notification : mapper.selectUndeliveredContent()) {
            String providerNo = adapter.deliver(notification);
            NotificationDelivery delivery = new NotificationDelivery();
            delivery.notificationId = notification.id;
            delivery.channel = "MOCK";
            delivery.status = "SUCCESS";
            delivery.providerNo = providerNo;
            delivery.createdAt = LocalDateTime.now(ZoneOffset.UTC);
            try { deliveries.insert(delivery); } catch (DuplicateKeyException duplicate) { }
            mapper.markMockSent(notification.id);
        }
    }
}
