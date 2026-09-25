package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sportsbooking.admin.base.enums.BalanceLotStatus;
import com.sportsbooking.admin.base.enums.BalanceType;
import java.time.Instant;
import java.time.LocalDateTime;

/** 可追溯到充值来源的余额批次。 */
@TableName("s_member_balance_lot")
public class MemberBalanceLot {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long venueCustomerId;
    public Long sourceRechargeId;
    public BalanceType type;
    public Integer originalAmountFen;
    public Integer availableAmountFen;
    public Integer frozenAmountFen;
    public BalanceLotStatus status;
    public Instant expiresAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
