package com.sportsbooking.admin.biz.pojo.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 小程序场馆详情聚合结果。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicVenueDetailVO {

    /**
     * 场馆资料
     */
    @Schema(description = "场馆资料")
    @ApiModelProperty(value = "场馆资料")
    private VenueVO venue;

    /**
     * 运动类型列表
     */
    @Schema(description = "运动类型列表")
    @ApiModelProperty(value = "运动类型列表")
    private List<SportTypeVO> sports;

    /**
     * 场地列表
     */
    @Schema(description = "场地列表")
    @ApiModelProperty(value = "场地列表")
    private List<CourtVO> courts;

    /**
     * 可预约场次列表
     */
    @Schema(description = "可预约场次列表")
    @ApiModelProperty(value = "可预约场次列表")
    private List<AvailableSlotVO> slots;
}
