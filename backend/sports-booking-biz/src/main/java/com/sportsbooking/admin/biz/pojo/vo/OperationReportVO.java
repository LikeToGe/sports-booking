package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 平台或场馆经营报表汇总。
 * 创建时间: 2026/09/03 10:35
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationReportVO {

    /** 预约总数。 */
    @Schema(description = "预约总数")
    @ApiModelProperty(value = "预约总数")
    private Long bookingCount;

    /** 有效预约数。 */
    @Schema(description = "有效预约数")
    @ApiModelProperty(value = "有效预约数")
    private Long effectiveBookingCount;

    /** 已完成预约数。 */
    @Schema(description = "已完成预约数")
    @ApiModelProperty(value = "已完成预约数")
    private Long completedCount;

    /** 已取消预约数。 */
    @Schema(description = "已取消预约数")
    @ApiModelProperty(value = "已取消预约数")
    private Long cancelledCount;

    /** 未到场预约数。 */
    @Schema(description = "未到场预约数")
    @ApiModelProperty(value = "未到场预约数")
    private Long noShowCount;

    /** 有效预约金额，单位为分。 */
    @Schema(description = "有效预约金额，单位为分")
    @ApiModelProperty(value = "有效预约金额，单位为分")
    private Long amountFen;

    /** 每日经营数据。 */
    @Schema(description = "每日经营数据")
    @ApiModelProperty(value = "每日经营数据")
    private List<OperationReportDailyVO> daily;
}
