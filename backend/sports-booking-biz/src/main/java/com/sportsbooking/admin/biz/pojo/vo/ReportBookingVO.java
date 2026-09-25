package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 预约经营下钻行。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportBookingVO {
    /** 预约号。 */
    @Schema(description = "预约号")
    @ApiModelProperty(value = "预约号")
    private String bookingNo;
    /** 场次日期。 */
    @Schema(description = "场次日期")
    @ApiModelProperty(value = "场次日期")
    private String sessionDate;
    /** 开始时间。 */
    @Schema(description = "开始时间")
    @ApiModelProperty(value = "开始时间")
    private String startTime;
    /** 结束时间。 */
    @Schema(description = "结束时间")
    @ApiModelProperty(value = "结束时间")
    private String endTime;
    /** 预约来源。 */
    @Schema(description = "预约来源")
    @ApiModelProperty(value = "预约来源")
    private String source;
    /** 联系人，按权限脱敏。 */
    @Schema(description = "联系人，按权限脱敏")
    @ApiModelProperty(value = "联系人，按权限脱敏")
    private String contactName;
    /** 手机号，按权限脱敏。 */
    @Schema(description = "手机号，按权限脱敏")
    @ApiModelProperty(value = "手机号，按权限脱敏")
    private String contactPhone;
    /** 场地名称。 */
    @Schema(description = "场地名称")
    @ApiModelProperty(value = "场地名称")
    private String courtName;
    /** 运动名称。 */
    @Schema(description = "运动名称")
    @ApiModelProperty(value = "运动名称")
    private String sportName;
    /** 预约状态。 */
    @Schema(description = "预约状态")
    @ApiModelProperty(value = "预约状态")
    private String status;
    /** 收款状态。 */
    @Schema(description = "收款状态")
    @ApiModelProperty(value = "收款状态")
    private String paymentStatus;
    /** 预约总金额，单位为分。 */
    @Schema(description = "预约总金额，单位为分")
    @ApiModelProperty(value = "预约总金额，单位为分")
    private Long amountFen;
    /** 线下登记金额，单位为分。 */
    @Schema(description = "线下登记金额，单位为分")
    @ApiModelProperty(value = "线下登记金额，单位为分")
    private Long offlineRegisteredAmountFen;
    /** 余额消费，单位为分。 */
    @Schema(description = "余额消费，单位为分")
    @ApiModelProperty(value = "余额消费，单位为分")
    private Long balanceConsumptionFen;
    /** 外部支付，单位为分。 */
    @Schema(description = "外部支付，单位为分")
    @ApiModelProperty(value = "外部支付，单位为分")
    private Long externalPaymentFen;
    /** 创建时间，Asia/Shanghai。 */
    @Schema(description = "创建时间，Asia/Shanghai")
    @ApiModelProperty(value = "创建时间，Asia/Shanghai")
    private LocalDateTime createdAt;
}
