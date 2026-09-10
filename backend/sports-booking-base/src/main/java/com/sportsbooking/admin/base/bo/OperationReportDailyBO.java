package com.sportsbooking.admin.base.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 经营报表按场次日期聚合结果。
 * 创建时间: 2026/09/03 10:35
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationReportDailyBO {

    /** 经营日期。 */
    private String reportDate;

    /** 预约总数。 */
    private Long bookingCount;

    /** 排除待支付和已取消后的有效预约数。 */
    private Long effectiveBookingCount;

    /** 已完成预约数。 */
    private Long completedCount;

    /** 已取消预约数。 */
    private Long cancelledCount;

    /** 未到场预约数。 */
    private Long noShowCount;

    /** 有效预约金额，单位为分。 */
    private Long amountFen;
}
