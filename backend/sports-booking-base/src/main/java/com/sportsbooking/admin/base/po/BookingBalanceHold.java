package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sportsbooking.admin.base.enums.BalanceHoldStatus;
import java.time.Instant;
import java.time.LocalDateTime;

/** 预约对单个余额批次的冻结明细。 */
@TableName("s_booking_balance_hold")
public class BookingBalanceHold {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long bookingId;
    public Long lotId;
    public Integer amountFen;
    public BalanceHoldStatus status;
    public Instant frozenAt;
    public Instant consumedAt;
    public Instant releasedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
