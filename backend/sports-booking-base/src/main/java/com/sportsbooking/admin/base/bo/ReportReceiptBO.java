package com.sportsbooking.admin.base.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 每日真实收款聚合行。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportReceiptBO {
    /** 收款日期。 */
    private String receiptDate;
    /** 操作人显示名称。 */
    private String operatorName;
    /** 真实收款渠道。 */
    private String channel;
    /** 收款业务类型。 */
    private String businessType;
    /** 收款笔数。 */
    private Long count;
    /** 实收金额，单位为分。 */
    private Long amountFen;
}
