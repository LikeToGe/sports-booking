package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 平台重置场馆管理员密码的请求。
 * 创建时间: 2026/09/11
 *
 * @author gjm
 */
@Schema(description = "场馆管理员密码重置请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {

    /**
     * 新登录密码
     */
    @Schema(description = "新登录密码")
    @ApiModelProperty(value = "新登录密码")
    private String password;
}
