package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.Venue;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 平台场馆列表项，包含非公开的服务费配置。
 * 创建时间: 2026/09/08
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformVenueVO {

    /**
     * 场馆资料
     */
    @Schema(description = "场馆资料")
    @ApiModelProperty(value = "场馆资料")
    private VenueVO venue;

    /**
     * 当前服务费率基点
     */
    @Schema(description = "当前服务费率基点，525 表示 5.25%")
    @ApiModelProperty(value = "当前服务费率基点，525 表示 5.25%")
    private Integer serviceFeeRateBasisPoints;

    /**
     * 功能描述: 将场馆实体转换为平台列表项。
     *
     * @param venue 场馆实体
     * @return 平台场馆列表项
     * @author gjm
     */
    public static PlatformVenueVO from(Venue venue) {
        return new PlatformVenueVO(VenueVO.from(venue), venue.serviceFeeRateBasisPoints);
    }
}
