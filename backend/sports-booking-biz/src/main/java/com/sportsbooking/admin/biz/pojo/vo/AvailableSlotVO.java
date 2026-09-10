package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.AvailableSlotBO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 小程序场馆详情中的可预约场次。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableSlotVO {

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
     * 场地名称
     */
    @Schema(description = "场地名称")
    @ApiModelProperty(value = "场地名称")
    private String courtName;

    /**
     * 运动类型 ID
     */
    @Schema(description = "运动类型 ID")
    @ApiModelProperty(value = "运动类型 ID")
    private Long sportTypeId;

    /**
     * 运动名称
     */
    @Schema(description = "运动名称")
    @ApiModelProperty(value = "运动名称")
    private String sportName;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    @ApiModelProperty(value = "开始时间")
    private String startAt;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    @ApiModelProperty(value = "结束时间")
    private String endAt;

    /**
     * 价格，单位为分
     */
    @Schema(description = "价格，单位为分")
    @ApiModelProperty(value = "价格，单位为分")
    private Integer priceFen;

    public static AvailableSlotVO from(AvailableSlotBO source) {
        return new AvailableSlotVO(source.getId(), source.getCourtId(), source.getCourtName(),
                source.getSportTypeId(), source.getSportName(), source.getStartAt(), source.getEndAt(),
                source.getPriceFen());
    }
}
