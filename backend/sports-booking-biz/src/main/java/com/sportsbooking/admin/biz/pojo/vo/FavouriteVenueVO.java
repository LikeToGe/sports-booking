package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.FavouriteVenueBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户常约入口中的安全场馆摘要。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class FavouriteVenueVO {
    /** 场馆主键。 */
    @Schema(description = "场馆主键")
    @ApiModelProperty(value = "场馆主键")
    private Long id;
    /** 场馆名称。 */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String name;
    /** 所在城市。 */
    @Schema(description = "所在城市")
    @ApiModelProperty(value = "所在城市")
    private String city;
    /** 所属区县。 */
    @Schema(description = "所属区县")
    @ApiModelProperty(value = "所属区县")
    private String district;
    /** 详细地址。 */
    @Schema(description = "详细地址")
    @ApiModelProperty(value = "详细地址")
    private String address;
    /** 封面访问路径。 */
    @Schema(description = "封面访问路径")
    @ApiModelProperty(value = "封面访问路径")
    private String coverPath;
    /** 场馆状态。ACTIVE 表示营业中，SUSPENDED 表示已暂停。 */
    @Schema(description = "场馆状态")
    @ApiModelProperty(value = "场馆状态")
    private String status;
    public static FavouriteVenueVO from(FavouriteVenueBO row) {
        return new FavouriteVenueVO(row.getId(), row.getName(), row.getCity(), row.getDistrict(), row.getAddress(), row.getCoverPath(), row.getStatus());
    }
}
