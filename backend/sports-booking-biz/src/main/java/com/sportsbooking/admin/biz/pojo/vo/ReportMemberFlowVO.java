package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 跨会员余额流水下钻行。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportMemberFlowVO {
    /** 流水 ID。 */
    @Schema(description = "流水 ID")
    @ApiModelProperty(value = "流水 ID")
    private Long flowId;
    /** 会员码。 */
    @Schema(description = "会员码")
    @ApiModelProperty(value = "会员码")
    private String memberCode;
    /** 业务类型。 */
    @Schema(description = "业务类型")
    @ApiModelProperty(value = "业务类型")
    private String businessType;
    /** 资金方向。 */
    @Schema(description = "资金方向")
    @ApiModelProperty(value = "资金方向")
    private String direction;
    /** 金额，单位为分。 */
    @Schema(description = "金额，单位为分")
    @ApiModelProperty(value = "金额，单位为分")
    private Long amountFen;
    /** 预约号。 */
    @Schema(description = "预约号")
    @ApiModelProperty(value = "预约号")
    private String bookingNo;
    /** 充值单号。 */
    @Schema(description = "充值单号")
    @ApiModelProperty(value = "充值单号")
    private String rechargeNo;
    /** 操作人。 */
    @Schema(description = "操作人")
    @ApiModelProperty(value = "操作人")
    private String operatorName;
    /** 发生时间，Asia/Shanghai。 */
    @Schema(description = "发生时间，Asia/Shanghai")
    @ApiModelProperty(value = "发生时间，Asia/Shanghai")
    private LocalDateTime occurredAt;
}
