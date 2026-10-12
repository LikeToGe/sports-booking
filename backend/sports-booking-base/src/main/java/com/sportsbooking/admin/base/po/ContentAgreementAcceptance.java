package com.sportsbooking.admin.base.po;

import java.time.LocalDateTime;

/** 用户对一组不可覆盖的协议版本的接受事实。 */
public class ContentAgreementAcceptance {
    public Long userId;
    public String rulesVersion;
    public String privacyVersion;
    public LocalDateTime acceptedAt;
}
