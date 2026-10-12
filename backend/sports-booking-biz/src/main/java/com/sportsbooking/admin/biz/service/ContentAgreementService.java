package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.request.ContentAgreementAcceptRequest;
import com.sportsbooking.admin.biz.pojo.request.ContentAgreementSettingRequest;
import com.sportsbooking.admin.biz.pojo.vo.ContentAgreementVO;

/** 社区规则、隐私说明及首次发布门禁。 */
public interface ContentAgreementService {
    ContentAgreementVO getForUser(long userId);

    ContentAgreementVO getForPlatform();

    ContentAgreementVO update(ContentAgreementSettingRequest request);

    ContentAgreementVO accept(long userId, ContentAgreementAcceptRequest request);

    void requireAccepted(long userId);
}
