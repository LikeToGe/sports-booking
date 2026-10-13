package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 每次 Mock 外部投递的独立审计记录。 */
@TableName("s_notification_delivery_attempt")
public class NotificationDeliveryAttempt {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long notificationId;
    public String channel;
    public String status;
    public String failureReason;
    public LocalDateTime createdAt;
}
