package com.sportsbooking.admin.base.po;

import java.time.LocalDateTime;

/** 当前社区规则和隐私说明版本。 */
public class ContentAgreementSetting {
    public Integer id;
    public String rulesVersion;
    public String privacyVersion;
    public String rulesText;
    public String privacyText;
    public Boolean forceReaccept;
    public LocalDateTime updatedAt;
}
