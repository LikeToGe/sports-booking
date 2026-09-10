package com.sportsbooking.admin.biz.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 服务端签名后的二维码内容及展示信息。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignedCredentialVO {

    /**
     * 二维码 Data URL
     */
    @Schema(description = "二维码 Data URL")
    @ApiModelProperty(value = "二维码 Data URL")
    private String qrDataUrl;

    /**
     * 六位核销短码
     */
    @Schema(description = "六位核销短码")
    @ApiModelProperty(value = "六位核销短码")
    private String shortCode;

    /**
     * 二维码原始内容
     */
    @Schema(description = "二维码原始内容")
    @ApiModelProperty(value = "二维码原始内容")
    private String rawQrContent;
}
