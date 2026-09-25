package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.po.Notification;
import com.sportsbooking.admin.biz.service.NotificationDeliveryAdapter;
import org.springframework.stereotype.Component;

/** 返回稳定业务号的本地 Mock 通知投递。 */
@Component
public class MockNotificationDeliveryAdapter implements NotificationDeliveryAdapter {
    @Override
    public String deliver(Notification notification) {
        return "MOCK-NOTIFICATION-" + notification.id;
    }
}
