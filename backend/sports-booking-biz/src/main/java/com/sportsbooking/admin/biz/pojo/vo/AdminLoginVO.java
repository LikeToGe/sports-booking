package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.model.AdminPrincipal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 后台登录结果。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginVO {

    /**
     * 会话令牌
     */
    @Schema(description = "会话令牌")
    @ApiModelProperty(value = "会话令牌")
    private String token;

    /**
     * 登录身份
     */
    @Schema(description = "登录身份")
    @ApiModelProperty(value = "登录身份")
    private AdminPrincipal principal;
}
