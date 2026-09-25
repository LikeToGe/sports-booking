package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sportsbooking.admin.base.enums.BalanceBusinessType;
import com.sportsbooking.admin.base.enums.BalanceFlowDirection;
import java.time.LocalDateTime;

/** 不可变会员余额流水。 */
@TableName("s_member_balance_flow")
public class MemberBalanceFlow {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long venueCustomerId;
    public Long lotId;
    public BalanceFlowDirection direction;
    public Integer amountFen;
    public BalanceBusinessType businessType;
    public Long bookingId;
    public Long rechargeId;
    public Long operatorAdminId;
    /** 普通员工操作者 ID。 */
    public Long operatorEmployeeId;
    public String idempotencyKey;
    public LocalDateTime createdAt;
}
