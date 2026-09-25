package com.sportsbooking.admin.base.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 平台可见的场馆级会员经营聚合。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportVenueMemberOperationBO {
    /** 场馆 ID。 */
    private Long venueId;
    /** 场馆名称。 */
    private String venueName;
    /** 顾客总数。 */
    private Long customerCount;
    /** 会员总数。 */
    private Long memberCount;
    /** 区间新增会员数。 */
    private Long newMemberCount;
    /** 区间内有有效预约的顾客数。 */
    private Long effectiveCustomerCount;
    /** 区间内至少两笔有效预约的顾客数。 */
    private Long repeatCustomerCount;
    /** 成功充值实付流入，单位为分。 */
    private Long rechargeInflowFen;
    /** 余额消费金额，单位为分。 */
    private Long balanceConsumptionFen;
    /** 当前会员可用余额，单位为分。 */
    private Long memberAvailableBalanceFen;
    /** 当前会员冻结余额，单位为分。 */
    private Long memberFrozenBalanceFen;
}
