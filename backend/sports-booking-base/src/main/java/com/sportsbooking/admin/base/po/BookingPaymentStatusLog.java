package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import java.time.LocalDateTime;

/**
 * 功能描述: 线下预约登记收款状态修改日志。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@TableName("s_booking_payment_status_log")
public class BookingPaymentStatusLog {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 预约 ID。 */
    public Long bookingId;
    /** 修改前状态。 */
    public BookingPaymentStatus beforeStatus;
    /** 修改后状态。 */
    public BookingPaymentStatus afterStatus;
    /** 操作后台账号 ID。 */
    public Long operatorAdminId;
    /** 普通员工操作者 ID。 */
    public Long operatorEmployeeId;
    /** 修改原因。 */
    public String reason;
    /** 创建时间。 */
    public LocalDateTime createdAt;
}
