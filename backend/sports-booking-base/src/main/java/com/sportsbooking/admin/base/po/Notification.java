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
    /** 消息类型。 */
    public NotificationType type;
    /** 消息发送状态。 */
    public NotificationStatus status;
    /** 创建时间。 */
    public LocalDateTime createdAt;
}
