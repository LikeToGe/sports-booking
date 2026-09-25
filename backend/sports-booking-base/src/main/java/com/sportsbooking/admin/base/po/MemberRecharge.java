package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sportsbooking.admin.base.enums.RechargeChannel;
import com.sportsbooking.admin.base.enums.RechargeStatus;
import java.time.Instant;
import java.time.LocalDateTime;

/** 会员充值单及档位快照。 */
@TableName("s_member_recharge")
public class MemberRecharge {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String rechargeNo;
    public Long venueCustomerId;
    public Long planId;
    public String planName;
    public Integer paidAmountFen;
    public Integer giftAmountFen;
    public Integer giftValidDays;
    public RechargeChannel channel;
    public Long operatorAdminId;
    /** 普通员工操作者 ID。 */
    public Long operatorEmployeeId;
    public String clientIdempotencyKey;
    public String providerNo;
    public RechargeStatus status;
    public String reverseReason;
    public Boolean offlineFundsHandled;
    public Instant offlineFundsHandledAt;
    public Instant succeededAt;
    public Instant reversedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
