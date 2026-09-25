package com.sportsbooking.admin.base.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 经营报表区间复购及当前会员余额聚合。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportOperationExtraBO {
    /** 区间内有有效预约的顾客数。 */
    private Long effectiveCustomerCount;
    /** 区间内至少两笔有效预约的顾客数。 */
    private Long repeatCustomerCount;
    /** 当前会员可用余额，单位为分。 */
    private Long memberAvailableBalanceFen;
    /** 当前会员冻结余额，单位为分。 */
    private Long memberFrozenBalanceFen;
}
