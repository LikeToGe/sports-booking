package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.po.Notification;

/** 外部通知投递边界。 */
public interface NotificationDeliveryAdapter {
    String deliver(Notification notification);
}
