package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.PublicVenueBO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 小程序首页场馆列表项。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicVenueVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 名称
     */
    @Schema(description = "名称")
    @ApiModelProperty(value = "名称")
    private String name;

    /**
     * 封面访问路径
     */
    @Schema(description = "封面访问路径")
    @ApiModelProperty(value = "封面访问路径")
    private String coverPath;

    /**
     * 所在城市
     */
    @Schema(description = "所在城市")
    @ApiModelProperty(value = "所在城市")
    private String city;

    /**
     * 详细地址
     */
    @Schema(description = "详细地址")
    @ApiModelProperty(value = "详细地址")
    private String address;

    /**
     * 运动名称列表
     */
    @Schema(description = "运动名称列表")
    @ApiModelProperty(value = "运动名称列表")
    private String sportNames;

    /**
     * 起步价格，单位为分
     */
    @Schema(description = "起步价格，单位为分")
    @ApiModelProperty(value = "起步价格，单位为分")
    private Integer startingPriceFen;

    /**
     * 下一个可预约时间
     */
    @Schema(description = "下一个可预约时间")
    @ApiModelProperty(value = "下一个可预约时间")
    private String nextAvailableAt;

    public static PublicVenueVO from(PublicVenueBO source) {
        return new PublicVenueVO(source.getId(), source.getName(), source.getCoverPath(), source.getCity(),
                source.getAddress(), source.getSportNames(), source.getStartingPriceFen(),
                source.getNextAvailableAt());
    }
}
