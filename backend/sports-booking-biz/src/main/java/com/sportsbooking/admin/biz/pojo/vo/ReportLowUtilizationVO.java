package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 低利用供给时段及其下钻条件。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportLowUtilizationVO {
    /** 场地 ID。 */
    @Schema(description = "场地 ID")
    @ApiModelProperty(value = "场地 ID")
    private long courtId;
    /** 场地名称。 */
    @Schema(description = "场地名称")
    @ApiModelProperty(value = "场地名称")
    private String courtName;
    /** 统计日期，Asia/Shanghai。 */
    @Schema(description = "统计日期，Asia/Shanghai")
    @ApiModelProperty(value = "统计日期，Asia/Shanghai")
    private String slotDate;
    /** 开始日期时间，Asia/Shanghai。 */
    @Schema(description = "开始日期时间，Asia/Shanghai")
    @ApiModelProperty(value = "开始日期时间，Asia/Shanghai")
    private String startTime;
    /** 结束日期时间，Asia/Shanghai，可为次日零点。 */
    @Schema(description = "结束日期时间，Asia/Shanghai，可为次日零点")
    @ApiModelProperty(value = "结束日期时间，Asia/Shanghai，可为次日零点")
    private String endTime;
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
}
