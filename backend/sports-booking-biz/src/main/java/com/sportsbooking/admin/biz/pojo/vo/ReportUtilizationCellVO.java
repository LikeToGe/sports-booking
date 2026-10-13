package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 星期小时热力图单元格。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportUtilizationCellVO {
    /** 星期，1-7。 */
    @Schema(description = "星期，1-7")
    @ApiModelProperty(value = "星期，1-7")
    private Integer dayOfWeek;
    /** 小时，0-23。 */
    @Schema(description = "小时，0-23")
    @ApiModelProperty(value = "小时，0-23")
    private Integer hourOfDay;
    /** 可售分钟。无供给时为 0。 */
    @Schema(description = "可售分钟")
    @ApiModelProperty(value = "可售分钟")
    private long availableMinutes;
    /** 已预订分钟。 */
    @Schema(description = "已预订分钟")
    @ApiModelProperty(value = "已预订分钟")
    private long bookedMinutes;
    /** 利用率，万分比；无供给时为 0。 */
    @Schema(description = "利用率，万分比")
    @ApiModelProperty(value = "利用率，万分比")
    private int utilizationRateBasisPoints;
    /** 取消预约数。 */
    @Schema(description = "取消预约数")
    @ApiModelProperty(value = "取消预约数")
    private long cancelledCount;
    /** KNOWN、UNAVAILABLE 或 DATA_INSUFFICIENT；未知时不计算利用率。 */
    @Schema(description = "KNOWN、UNAVAILABLE 或 DATA_INSUFFICIENT；未知时不计算利用率")
    @ApiModelProperty(value = "KNOWN、UNAVAILABLE 或 DATA_INSUFFICIENT；未知时不计算利用率")
    private String dataStatus;
}
