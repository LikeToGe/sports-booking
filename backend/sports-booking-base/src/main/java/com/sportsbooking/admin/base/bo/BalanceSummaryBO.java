package com.sportsbooking.admin.base.bo;

import java.time.Instant;
import lombok.Data;

/** 会员余额聚合查询结果。 */
@Data
public class BalanceSummaryBO {
    private Long paidBalanceFen;
    private Long giftBalanceFen;
    private Long nextGiftExpiresAmountFen;
    private Instant nextGiftExpiresAt;
}
