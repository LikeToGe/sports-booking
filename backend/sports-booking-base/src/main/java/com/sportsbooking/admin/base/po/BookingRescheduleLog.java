package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 功能描述: 线下预约同价改期日志。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@TableName("s_booking_reschedule_log")
public class BookingRescheduleLog {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 预约 ID。 */
    public Long bookingId;
    /** 原具体场次 ID。 */
    public Long originalSessionSlotId;
    /** 新具体场次 ID。 */
    public Long newSessionSlotId;
    /** 操作后台账号 ID。 */
    public Long operatorAdminId;
    /** 普通员工操作者 ID。 */
    public Long operatorEmployeeId;
    /** 改期原因。 */
    public String reason;
    /** 创建时间。 */
    public LocalDateTime createdAt;
}
