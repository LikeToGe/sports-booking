package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 周场次保存请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "周场次保存请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySlotSaveRequest {

    /**
     * 场地 ID
     */
    @Schema(description = "场地 ID")
    @ApiModelProperty(value = "场地 ID")
    private Long courtId;

    /**
     * 运动类型 ID
     */
    @Schema(description = "运动类型 ID")
    @ApiModelProperty(value = "运动类型 ID")
    private Long sportTypeId;

    /**
     * 星期，取值 1 至 7
     */
    @Schema(description = "星期，取值 1 至 7")
    @ApiModelProperty(value = "星期，取值 1 至 7")
    private Integer dayOfWeek;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    @ApiModelProperty(value = "开始时间")
    private LocalTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    @ApiModelProperty(value = "结束时间")
    private LocalTime endTime;

    /**
     * 价格，单位为分
     */
    @Schema(description = "价格，单位为分")
    @ApiModelProperty(value = "价格，单位为分")
    private Integer priceFen;
}
