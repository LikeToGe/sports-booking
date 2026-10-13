package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import com.sportsbooking.admin.base.enums.NotificationStatus;
import com.sportsbooking.admin.base.enums.NotificationType;

/**
 * 功能描述: 预约消息触发记录持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_notification")
public class Notification {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 接收消息的用户 ID。 */
    public Long userId;
    /** 关联预约 ID。 */
    public Long bookingId;
    /** 业务类型。 */
    public String businessType;
    /** 业务主键。 */
    public Long businessId;
    /** 提醒登记 ID；用于撤回、过期和消息落地。 */
    public Long subscriptionId;
    /** 消息类型。 */
    public NotificationType type;
    /** 站内消息标题。 */
    public String title;
    /** 站内消息正文。 */
    public String content;
    /** 阅读时间。 */
    public LocalDateTime readAt;
    /** 防止重复生成消息的幂等键。 */
    public String idempotencyKey;
    /** 消息发送状态。 */
    public NotificationStatus status;
    /** 外部投递临时领取时间。 */
    public LocalDateTime deliveryClaimedAt;
    /** 失败后的下次 Mock 重试时间。 */
    public LocalDateTime deliveryNextAttemptAt;
    /** 创建时间。 */
    public LocalDateTime createdAt;
}
