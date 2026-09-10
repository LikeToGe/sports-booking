package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 月度平台服务费场馆汇总。
 * 创建时间: 2026/09/08
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceFeeVenueSummaryVO {
    /** 场馆 ID。 */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;
    /** 场馆名称。 */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String venueName;
    /** 场馆当前服务费率，单位为基点。 */
    @Schema(description = "场馆当前服务费率基点")
    @ApiModelProperty(value = "场馆当前服务费率基点")
    private Integer currentServiceFeeRateBasisPoints;
    /** 可计费预约数。 */
    @Schema(description = "可计费预约数")
    @ApiModelProperty(value = "可计费预约数")
    private Long billableBookingCount;
    /** 可计费预约总金额，单位为分。 */
    @Schema(description = "可计费预约总金额，单位为分")
    @ApiModelProperty(value = "可计费预约总金额，单位为分")
    private Long bookingAmountFen;
    /** 精确平台服务费总额，单位为元。 */
    @Schema(description = "精确平台服务费总额，单位为元，最多六位小数")
    @ApiModelProperty(value = "精确平台服务费总额，单位为元，最多六位小数")
    private String serviceFeeYuan;
}
