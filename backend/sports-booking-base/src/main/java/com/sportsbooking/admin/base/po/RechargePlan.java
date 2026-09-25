package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sportsbooking.admin.base.enums.RechargePlanStatus;
import java.time.LocalDateTime;

/** 场馆充值档位持久化实体。 */
@TableName("s_recharge_plan")
public class RechargePlan {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long venueId;
    public String name;
    public Integer paidAmountFen;
    public Integer giftAmountFen;
    public Integer giftValidDays;
    public RechargePlanStatus status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
