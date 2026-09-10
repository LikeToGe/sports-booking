package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.EnableStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 当前用户附加的场馆员工身份。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeIdentityVO {

    /**
     * 员工 ID
     */
    @Schema(description = "员工 ID")
    @ApiModelProperty(value = "员工 ID")
    private Long employeeId;

    /**
     * 场馆 ID
     */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;

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
}
