package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 后台登录请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "后台登录请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginRequest {

    /**
     * 登录用户名
     */
    @Schema(description = "登录用户名")
    @ApiModelProperty(value = "登录用户名")
    private String username;

    /**
     * 登录密码
     */
    @Schema(description = "登录密码")
    @ApiModelProperty(value = "登录密码")
    private String password;
}
