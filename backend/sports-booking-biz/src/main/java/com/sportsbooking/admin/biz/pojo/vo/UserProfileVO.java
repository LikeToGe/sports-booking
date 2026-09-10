package com.sportsbooking.admin.biz.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 小程序当前用户资料。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 联系人姓名
     */
    @Schema(description = "联系人姓名")
    @ApiModelProperty(value = "联系人姓名")
    private String contactName;

    /**
     * 联系电话
     */
    @Schema(description = "联系电话")
    @ApiModelProperty(value = "联系电话")
    private String phone;

    /**
     * 员工身份
     */
    @Schema(description = "员工身份")
    @ApiModelProperty(value = "员工身份")
    private EmployeeIdentityVO staff;
}
