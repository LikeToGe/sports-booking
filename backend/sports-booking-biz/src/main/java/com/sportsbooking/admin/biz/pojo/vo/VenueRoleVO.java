package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.VenuePermission;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场馆角色及授权汇总。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueRoleVO {
    /** 角色 ID。 */
    @Schema(description = "角色 ID")
    @ApiModelProperty(value = "角色 ID")
    private Long id;
    /** 场馆内角色名称。 */
    @Schema(description = "角色名称")
    @ApiModelProperty(value = "角色名称")
    private String name;
    /** 角色启停状态。 */
    @Schema(description = "启停状态")
    @ApiModelProperty(value = "启停状态")
    private EnableStatus status;
    /** 角色拥有的固定权限编码。 */
    @Schema(description = "权限编码")
    @ApiModelProperty(value = "权限编码")
    private List<VenuePermission> permissions;
    /** 当前绑定该角色的员工数量。 */
    @Schema(description = "绑定员工数")
    @ApiModelProperty(value = "绑定员工数")
    private Long employeeCount;
}
