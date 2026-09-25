package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
public class ReportReceiptVO {
    /** 收款日期。 */
    @Schema(description = "收款日期")
    @ApiModelProperty(value = "收款日期")
    private String receiptDate;
    /** 操作人。 */
    @Schema(description = "操作人")
    @ApiModelProperty(value = "操作人")
    private String operatorName;
    /** 真实收款渠道。 */
    @Schema(description = "真实收款渠道")
    @ApiModelProperty(value = "真实收款渠道")
    private String channel;
    /** 收款业务类型。 */
    @Schema(description = "收款业务类型")
    @ApiModelProperty(value = "收款业务类型")
    private String businessType;
    /** 笔数。 */
    @Schema(description = "笔数")
    @ApiModelProperty(value = "笔数")
    private Long count;
    /** 实收金额，单位为分。 */
    @Schema(description = "实收金额，单位为分")
    @ApiModelProperty(value = "实收金额，单位为分")
    private Long amountFen;
}
