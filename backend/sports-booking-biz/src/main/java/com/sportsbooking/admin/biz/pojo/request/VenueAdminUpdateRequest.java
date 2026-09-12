package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 平台维护场馆管理员账号的请求。
 * 创建时间: 2026/09/12
 *
 * @author gjm
 */
@Schema(description = "场馆管理员账号更新请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueAdminUpdateRequest {

    /** 管理员登录用户名。 */
    @Schema(description = "管理员登录用户名")
    @ApiModelProperty(value = "管理员登录用户名")
    private String username;

    /** 新登录密码，留空时不修改。 */
    @Schema(description = "新登录密码，留空时不修改")
    @ApiModelProperty(value = "新登录密码，留空时不修改")
    private String password;
}
