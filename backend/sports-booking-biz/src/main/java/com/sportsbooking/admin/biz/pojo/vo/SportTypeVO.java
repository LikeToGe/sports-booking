package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.po.SportType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 运动类型接口返回模型。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SportTypeVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 所属场馆 ID
     */
    @Schema(description = "所属场馆 ID")
    @ApiModelProperty(value = "所属场馆 ID")
    private Long ownerVenueId;

    /**
     * 名称
     */
    @Schema(description = "名称")
    @ApiModelProperty(value = "名称")
    private String name;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private EnableStatus status;

    /**
     * 功能描述: 将运动类型实体转换为接口模型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param sportType 运动类型实体
     * @return 运动类型接口模型
     * @author gjm
     */
    public static SportTypeVO from(SportType sportType) {
        return new SportTypeVO(sportType.id, sportType.ownerVenueId, sportType.name, sportType.status);
    }
}
