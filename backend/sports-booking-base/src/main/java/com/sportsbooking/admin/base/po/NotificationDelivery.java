package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 站内消息的外部渠道投递结果。 */
@TableName("s_notification_delivery")
public class NotificationDelivery {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long notificationId;
    public String channel;
    public String status;
    public String providerNo;
    public String failureReason;
    public Integer attemptCount;
    public LocalDateTime lastAttemptAt;
    public LocalDateTime createdAt;
}
