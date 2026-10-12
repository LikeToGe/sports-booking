package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户确认已阅读的协议版本。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentAgreementAcceptRequest {
    /** 社区规则版本。 */
    @Schema(description = "社区规则版本") @ApiModelProperty("社区规则版本") private String rulesVersion;
    /** 隐私说明版本。 */
    @Schema(description = "隐私说明版本") @ApiModelProperty("隐私说明版本") private String privacyVersion;
}
