package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 跨会员余额流水报表行。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportMemberFlowBO {
    /** 流水 ID。 */
    private Long flowId;
    /** 场馆会员码。 */
    private String memberCode;
    /** 余额业务类型。 */
    private String businessType;
    /** 资金方向。 */
    private String direction;
    /** 金额，单位为分。 */
    private Long amountFen;
    /** 关联预约号。 */
    private String bookingNo;
    /** 关联充值单号。 */
    private String rechargeNo;
    /** 操作人显示名称。 */
    private String operatorName;
    /** 发生时间，Asia/Shanghai。 */
    private LocalDateTime occurredAt;
}
