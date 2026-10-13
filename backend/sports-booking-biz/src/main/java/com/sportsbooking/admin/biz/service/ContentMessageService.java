package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.NotificationStatus;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.base.mapper.NotificationMapper;
import com.sportsbooking.admin.base.mapper.NotificationDeliveryMapper;
import com.sportsbooking.admin.base.mapper.NotificationDeliveryAttemptMapper;
import com.sportsbooking.admin.base.po.Notification;
import com.sportsbooking.admin.base.po.NotificationDelivery;
import com.sportsbooking.admin.base.po.NotificationDeliveryAttempt;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.ContentNotificationVO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 站内消息在业务事务中落库，Mock 外部投递由任务在事务外执行并幂等记账。 */
@Service
public class ContentMessageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentMessageService.class);
    private final NotificationMapper mapper;
    private final NotificationDeliveryMapper deliveries;
    private final NotificationDeliveryAttemptMapper attempts;
    private final NotificationDeliveryAdapter adapter;

    public ContentMessageService(NotificationMapper mapper, NotificationDeliveryMapper deliveries,
                                  NotificationDeliveryAttemptMapper attempts,
                                  NotificationDeliveryAdapter adapter) {
        this.mapper = mapper;
        this.deliveries = deliveries;
        this.attempts = attempts;
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

    /** 生成前在同一 SQL 语句中复核预约状态和用户身份。 */
    public void recordArrivalIfEligible(long bookingId, String title, String body) {
        mapper.insertArrivalIfEligible(bookingId, "alert:arrival:" + bookingId, title, body);
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
            try {
                deliverOne(notification);
            } catch (DataAccessException exception) {
                // 单条持久化故障保持原领取状态待租期重试，其余消息仍可继续处理。
                LOGGER.warn("消息 {} 的 Mock 投递记录失败，将保留待重试", notification.id, exception);
            }
        }
    }

    /** 每次仅一个调度者取得消息；已落库的成功结果在重启后只补状态。 */
    private void deliverOne(Notification notification) {
        if (mapper.claimDelivery(notification.id) == 0) return;
        NotificationDelivery delivery = deliveries.selectOne(new QueryWrapper<NotificationDelivery>()
                .eq("notification_id", notification.id).eq("channel", "MOCK"));
        if (delivery != null && "SUCCESS".equals(delivery.status)) {
            mapper.markMockSent(notification.id);
            return;
        }
        // 外部调用之前按当前预约、登记、供给和账号状态复核；已失效的历史消息仍保留。
        if (mapper.countDeliveryEligible(notification.id) == 0) {
            mapper.markSkipped(notification.id);
            return;
        }
        if (delivery == null) {
            delivery = new NotificationDelivery();
            delivery.notificationId = notification.id;
            delivery.channel = "MOCK";
            delivery.attemptCount = 0;
            delivery.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        delivery.attemptCount = (delivery.attemptCount == null ? 0 : delivery.attemptCount) + 1;
        delivery.lastAttemptAt = LocalDateTime.now(ZoneOffset.UTC);
        try {
            delivery.providerNo = adapter.deliver(notification);
            delivery.status = "SUCCESS";
            delivery.failureReason = null;
        } catch (RuntimeException failure) {
            delivery.status = "FAILED";
            delivery.failureReason = "Mock 投递失败";
        }
        NotificationDeliveryAttempt attempt = new NotificationDeliveryAttempt();
        attempt.notificationId = notification.id;
        attempt.channel = "MOCK";
        attempt.status = delivery.status;
        attempt.failureReason = delivery.failureReason;
        attempt.createdAt = delivery.lastAttemptAt;
        attempts.insert(attempt);
        if (delivery.id == null) deliveries.insert(delivery); else deliveries.updateById(delivery);
        if ("SUCCESS".equals(delivery.status)) mapper.markMockSent(notification.id);
        else mapper.markDeliveryPending(notification.id);
    }
}
