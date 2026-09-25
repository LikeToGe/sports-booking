package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.VenuePermission;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场馆角色保存请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleSaveRequest {
    /** 场馆内唯一的角色名称。 */
    @Schema(description = "角色名称")
    @ApiModelProperty(value = "角色名称")
    private String name;
    /** 角色启停状态，停用后不再参与权限并集。 */
    @Schema(description = "启停状态")
    @ApiModelProperty(value = "启停状态")
    private EnableStatus status;
    /** 后端固定权限编码列表。 */
    @Schema(description = "固定权限编码")
    @ApiModelProperty(value = "固定权限编码")
    private List<VenuePermission> permissions;
}
