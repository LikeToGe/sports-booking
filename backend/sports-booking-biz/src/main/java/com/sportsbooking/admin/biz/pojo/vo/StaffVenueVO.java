package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.VenuePermission;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前用户的有效员工场馆身份。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffVenueVO {
    /** 当前用户在该场馆的员工 ID。 */
    @Schema(description = "员工 ID")
    @ApiModelProperty(value = "员工 ID")
    private Long employeeId;
    /** 员工所属场馆 ID。 */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;
    /** 场馆名称。 */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String venueName;
    /** 本场馆员工显示名称。 */
    @Schema(description = "员工姓名")
    @ApiModelProperty(value = "员工姓名")
    private String employeeName;
    /** 当前有效角色实时计算的权限并集。 */
    @Schema(description = "实时权限")
    @ApiModelProperty(value = "实时权限")
    private List<VenuePermission> permissions;
}
