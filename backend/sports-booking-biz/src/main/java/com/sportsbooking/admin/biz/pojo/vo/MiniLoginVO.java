package com.sportsbooking.admin.biz.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 小程序登录结果。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiniLoginVO {

    /**
     * 会话令牌
     */
    @Schema(description = "会话令牌")
    @ApiModelProperty(value = "会话令牌")
    private String token;

    /**
     * 用户资料
     */
    @Schema(description = "用户资料")
    @ApiModelProperty(value = "用户资料")
    private UserProfileVO profile;
}
