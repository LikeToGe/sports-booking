package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 自然月平台服务费只读结算数据。
 * 创建时间: 2026/09/08
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceFeeSettlementVO {
    /** 账期。 */
    @Schema(description = "自然月账期，格式 yyyy-MM")
    @ApiModelProperty(value = "自然月账期，格式 yyyy-MM")
    private String billingMonth;
    /** 当前场馆服务费率，平台全量查询时为空。 */
    @Schema(description = "当前场馆服务费率基点，平台全量查询时为空")
    @ApiModelProperty(value = "当前场馆服务费率基点，平台全量查询时为空")
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
    /** 平台按场馆汇总。 */
    @Schema(description = "平台按场馆汇总")
    @ApiModelProperty(value = "平台按场馆汇总")
    private List<ServiceFeeVenueSummaryVO> venues;
    /** 可计费预约逐笔明细。 */
    @Schema(description = "可计费预约逐笔明细")
    @ApiModelProperty(value = "可计费预约逐笔明细")
    private List<ServiceFeeBookingVO> bookings;
}
