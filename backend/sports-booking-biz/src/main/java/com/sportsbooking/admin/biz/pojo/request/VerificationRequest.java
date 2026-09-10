package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.VerificationMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 员工核销请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "员工核销请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    /**
     * 二维码内容或六位短码
     */
    @Schema(description = "二维码内容或六位短码")
    @ApiModelProperty(value = "二维码内容或六位短码")
    private String credential;

    /**
     * 核销方式
     */
    @Schema(description = "核销方式")
    @ApiModelProperty(value = "核销方式")
    private VerificationMethod method;
}
