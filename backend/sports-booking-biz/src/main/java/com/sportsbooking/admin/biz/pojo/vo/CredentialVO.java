package com.sportsbooking.admin.biz.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 用户展示的核销二维码和短码。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CredentialVO {

    /**
     * 二维码图片
     */
    @Schema(description = "二维码图片")
    @ApiModelProperty(value = "二维码图片")
    private String qr;

    /**
     * 六位核销短码
     */
    @Schema(description = "六位核销短码")
    @ApiModelProperty(value = "六位核销短码")
    private String shortCode;
}
