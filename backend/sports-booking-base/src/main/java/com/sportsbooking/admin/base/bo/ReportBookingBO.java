package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 经营报表预约下钻行。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportBookingBO {
    /** 预约号。 */
    private String bookingNo;
    /** 场次日期。 */
    private String sessionDate;
    /** 场次开始时间。 */
    private String startTime;
    /** 场次结束时间。 */
    private String endTime;
    /** 预约来源。 */
    private String source;
    /** 联系人。 */
    private String contactName;
    /** 联系手机号。 */
    private String contactPhone;
    /** 场地名称。 */
    private String courtName;
    /** 运动名称。 */
    private String sportName;
    /** 预约状态。 */
    private String status;
    /** 收款状态。 */
    private String paymentStatus;
    /** 预约总金额，单位为分。 */
    private Long amountFen;
    /** 线下已收登记金额，单位为分。 */
    private Long offlineRegisteredAmountFen;
    /** 余额消费金额，单位为分。 */
    private Long balanceConsumptionFen;
    /** 成功外部支付金额，单位为分。 */
    private Long externalPaymentFen;
    /** 创建时间，Asia/Shanghai。 */
    private LocalDateTime createdAt;
}
