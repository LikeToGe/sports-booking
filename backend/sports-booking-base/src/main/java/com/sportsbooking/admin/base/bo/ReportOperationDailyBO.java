package com.sportsbooking.admin.base.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 经营报表按业务日期聚合结果。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportOperationDailyBO {
    /** 经营日期。 */
    private String reportDate;
    /** 线上已支付且未取消预约金额，单位为分。 */
    private Long onlineBookingAmountFen;
    /** 线下已收登记金额，单位为分。 */
    private Long offlineRegisteredAmountFen;
    /** 未关闭的可经营场次数。 */
    private Long availableSessionCount;
    /** 已预约场次数。 */
    private Long bookedSessionCount;
    /** 预约总数。 */
    private Long bookingCount;
    /** 已取消预约数。 */
    private Long cancelledCount;
    /** 待核销线上预约数。 */
    private Long pendingVerificationCount;
    /** 线下未记录收款数。 */
    private Long offlineUnrecordedCount;
    /** 线下未收款数。 */
    private Long offlineUnpaidCount;
    /** 新增会员数。 */
    private Long newMemberCount;
    /** 成功充值实付流入，单位为分。 */
    private Long rechargeInflowFen;
    /** 余额消费金额，单位为分。 */
    private Long balanceConsumptionFen;
}
