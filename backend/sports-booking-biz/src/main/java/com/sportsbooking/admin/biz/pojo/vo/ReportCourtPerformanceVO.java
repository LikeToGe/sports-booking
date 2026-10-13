package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场地利用率对比行。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportCourtPerformanceVO {
    /** 场地 ID。 */
    @Schema(description = "场地 ID")
    @ApiModelProperty(value = "场地 ID")
    private long courtId;
    /** 场地名称。 */
    @Schema(description = "场地名称")
    @ApiModelProperty(value = "场地名称")
    private String courtName;
    /** 可售分钟。 */
    @Schema(description = "可售分钟")
    @ApiModelProperty(value = "可售分钟")
    private long availableMinutes;
    /** 已预订分钟。 */
    @Schema(description = "已预订分钟")
    @ApiModelProperty(value = "已预订分钟")
    private long bookedMinutes;
    /** 利用率，万分比。 */
    @Schema(description = "利用率，万分比")
    @ApiModelProperty(value = "利用率，万分比")
    private int utilizationRateBasisPoints;
    /** 取消预约数。 */
    @Schema(description = "取消预约数")
    @ApiModelProperty(value = "取消预约数")
    private long cancelledCount;
    /** 预约总数。 */
    @Schema(description = "预约总数")
    @ApiModelProperty(value = "预约总数")
    private long bookingCount;
    /** KNOWN、UNAVAILABLE 或 DATA_INSUFFICIENT；缺失供给时不推断零利用率。 */
    @Schema(description = "KNOWN、UNAVAILABLE 或 DATA_INSUFFICIENT；缺失供给时不推断零利用率")
    @ApiModelProperty(value = "KNOWN、UNAVAILABLE 或 DATA_INSUFFICIENT；缺失供给时不推断零利用率")
    private String dataStatus;
}
