package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 每日经营指标。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportOperationDailyVO {
    /** 经营日期。 */
    @Schema(description = "经营日期")
    @ApiModelProperty(value = "经营日期")
    private String reportDate;
    /** 线上预约经营金额，单位为分。 */
    @Schema(description = "线上预约经营金额，单位为分")
    @ApiModelProperty(value = "线上预约经营金额，单位为分")
    private Long onlineBookingAmountFen;
    /** 线下登记经营金额，单位为分。 */
    @Schema(description = "线下登记经营金额，单位为分")
    @ApiModelProperty(value = "线下登记经营金额，单位为分")
    private Long offlineRegisteredAmountFen;
    /** 未关闭的可经营场次数。 */
    @Schema(description = "未关闭的可经营场次数")
    @ApiModelProperty(value = "未关闭的可经营场次数")
    private Long availableSessionCount;
    /** 已预约场次数。 */
    @Schema(description = "已预约场次数")
    @ApiModelProperty(value = "已预约场次数")
    private Long bookedSessionCount;
    /** 场地利用率，万分比。 */
    @Schema(description = "场地利用率，万分比")
    @ApiModelProperty(value = "场地利用率，万分比")
    private Integer utilizationRateBasisPoints;
    /** 预约总数。 */
    @Schema(description = "预约总数")
    @ApiModelProperty(value = "预约总数")
    private Long bookingCount;
    /** 已取消预约数。 */
    @Schema(description = "已取消预约数")
    @ApiModelProperty(value = "已取消预约数")
    private Long cancelledCount;
    /** 取消率，万分比。 */
    @Schema(description = "取消率，万分比")
    @ApiModelProperty(value = "取消率，万分比")
    private Integer cancellationRateBasisPoints;
    /** 待核销预约数。 */
    @Schema(description = "待核销预约数")
    @ApiModelProperty(value = "待核销预约数")
    private Long pendingVerificationCount;
    /** 线下未记录收款数。 */
    @Schema(description = "线下未记录收款数")
    @ApiModelProperty(value = "线下未记录收款数")
    private Long offlineUnrecordedCount;
    /** 线下未收款数。 */
    @Schema(description = "线下未收款数")
    @ApiModelProperty(value = "线下未收款数")
    private Long offlineUnpaidCount;
    /** 新增会员数。 */
    @Schema(description = "新增会员数")
    @ApiModelProperty(value = "新增会员数")
    private Long newMemberCount;
    /** 成功充值实付流入，单位为分。 */
    @Schema(description = "成功充值实付流入，单位为分")
    @ApiModelProperty(value = "成功充值实付流入，单位为分")
    private Long rechargeInflowFen;
    /** 余额消费金额，单位为分。 */
    @Schema(description = "余额消费金额，单位为分")
    @ApiModelProperty(value = "余额消费金额，单位为分")
    private Long balanceConsumptionFen;
}
