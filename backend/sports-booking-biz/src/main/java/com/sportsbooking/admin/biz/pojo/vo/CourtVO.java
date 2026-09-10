package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.CourtBO;
import com.sportsbooking.admin.base.enums.EnableStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 场馆后台场地列表项。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourtVO {

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
     * 场地图片公开路径
     */
    @Schema(description = "场地图片公开路径")
    @ApiModelProperty(value = "场地图片公开路径")
    private String imagePath;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private EnableStatus status;

    /**
     * 运动名称列表
     */
    @Schema(description = "运动名称列表")
    @ApiModelProperty(value = "运动名称列表")
    private String sportNames;

    /**
     * 运动类型 ID 列表
     */
    @Schema(description = "运动类型 ID 列表")
    @ApiModelProperty(value = "运动类型 ID 列表")
    private String sportIds;

    public static CourtVO from(CourtBO source) {
        return new CourtVO(source.getId(), source.getName(), source.getImagePath(), source.getStatus(), source.getSportNames(),
                source.getSportIds());
    }
}
