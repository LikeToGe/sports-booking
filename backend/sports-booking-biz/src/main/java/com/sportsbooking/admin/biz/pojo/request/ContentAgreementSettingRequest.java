package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 平台维护社区协议版本及展示内容。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentAgreementSettingRequest {
    /** 社区规则版本。 */
    @Schema(description = "社区规则版本") @ApiModelProperty("社区规则版本") private String rulesVersion;
    /** 隐私说明版本。 */
    @Schema(description = "隐私说明版本") @ApiModelProperty("隐私说明版本") private String privacyVersion;
    /** 社区规则正文。 */
    @Schema(description = "社区规则正文") @ApiModelProperty("社区规则正文") private String rulesText;
    /** 隐私说明正文。 */
    @Schema(description = "隐私说明正文") @ApiModelProperty("隐私说明正文") private String privacyText;
    /** 版本变化时是否必须重签。 */
    @Schema(description = "版本变化时是否必须重签") @ApiModelProperty("版本变化时是否必须重签") private Boolean forceReaccept;
}
