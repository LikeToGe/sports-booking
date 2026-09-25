package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.VenuePermission;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 固定权限目录项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCatalogVO {
    /** 后端固定权限编码。 */
    @Schema(description = "权限编码")
    @ApiModelProperty(value = "权限编码")
    private VenuePermission code;
    /** 权限中文名称。 */
    @Schema(description = "权限名称")
    @ApiModelProperty(value = "权限名称")
    private String name;
    /** 后台展示分组。 */
    @Schema(description = "权限分组")
    @ApiModelProperty(value = "权限分组")
    private String group;
}
