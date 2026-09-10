package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 小程序模拟登录请求；正式接微信后 code 语义保持不变。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "小程序模拟登录请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiniLoginRequest {

    /**
     * 模拟微信登录 code
     */
    @Schema(description = "模拟微信登录 code")
    @ApiModelProperty(value = "模拟微信登录 code")
    private String code;
}
