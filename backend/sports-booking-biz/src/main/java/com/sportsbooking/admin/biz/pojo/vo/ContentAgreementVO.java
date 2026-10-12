package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.ContentAgreementSetting;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前协议及用户接受状态。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentAgreementVO {
    /** 社区规则版本。 */
    @Schema(description = "社区规则版本") @ApiModelProperty("社区规则版本") private String rulesVersion;
    /** 隐私说明版本。 */
    @Schema(description = "隐私说明版本") @ApiModelProperty("隐私说明版本") private String privacyVersion;
    /** 社区规则正文。 */
    @Schema(description = "社区规则正文") @ApiModelProperty("社区规则正文") private String rulesText;
    /** 隐私说明正文。 */
    @Schema(description = "隐私说明正文") @ApiModelProperty("隐私说明正文") private String privacyText;
    /** 是否强制接受当前版本。 */
    @Schema(description = "是否强制接受当前版本") @ApiModelProperty("是否强制接受当前版本") private Boolean forceReaccept;
    /** 当前用户是否已满足接受门禁。 */
    @Schema(description = "当前用户是否已接受") @ApiModelProperty("当前用户是否已接受") private Boolean accepted;
    /** 接受时间。 */
    @Schema(description = "接受时间") @ApiModelProperty("接受时间") private LocalDateTime acceptedAt;

    public static ContentAgreementVO from(ContentAgreementSetting setting, LocalDateTime acceptedAt) {
        return new ContentAgreementVO(setting.rulesVersion, setting.privacyVersion, setting.rulesText,
                setting.privacyText, setting.forceReaccept, acceptedAt != null, acceptedAt);
    }
}
