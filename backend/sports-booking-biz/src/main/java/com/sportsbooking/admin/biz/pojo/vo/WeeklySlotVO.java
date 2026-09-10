package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.po.WeeklySlot;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 周场次模板接口返回模型。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySlotVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

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

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private EnableStatus status;

    /**
     * 功能描述: 将周场次实体转换为接口模型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param weeklySlot 周场次实体
     * @return 周场次接口模型
     * @author gjm
     */
    public static WeeklySlotVO from(WeeklySlot weeklySlot) {
        return new WeeklySlotVO(weeklySlot.id, weeklySlot.courtId, weeklySlot.sportTypeId,
                weeklySlot.dayOfWeek, weeklySlot.startTime, weeklySlot.endTime,
                weeklySlot.priceFen, weeklySlot.status);
    }
}
