package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.ContentAgreementMapper;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.po.ContentAgreementAcceptance;
import com.sportsbooking.admin.base.po.ContentAgreementSetting;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.ContentAgreementAcceptRequest;
import com.sportsbooking.admin.biz.pojo.request.ContentAgreementSettingRequest;
import com.sportsbooking.admin.biz.pojo.vo.ContentAgreementVO;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 协议版本切换不覆盖历史接受事实；发布门禁在内容事务中读取当前配置。 */
@Service
public class ContentAgreementServiceImpl implements ContentAgreementService {
    private final ContentAgreementMapper mapper;
    private final UserAccountMapper users;

    public ContentAgreementServiceImpl(ContentAgreementMapper mapper, UserAccountMapper users) {
        this.mapper = mapper;
        this.users = users;
    }

    @Override
    public ContentAgreementVO getForUser(long userId) {
        ContentAgreementSetting setting = requireSetting(false);
        ContentAgreementAcceptance accepted = accepted(userId, setting);
        return ContentAgreementVO.from(setting, accepted == null ? null : accepted.acceptedAt);
    }

    @Override
    public ContentAgreementVO getForPlatform() {
        return ContentAgreementVO.from(requireSetting(false), null);
    }

    @Override
    @Transactional
    public ContentAgreementVO update(ContentAgreementSettingRequest request) {
        if (request == null || !valid(request.getRulesVersion(), 40) || !valid(request.getPrivacyVersion(), 40)
                || !valid(request.getRulesText(), 3000) || !valid(request.getPrivacyText(), 3000)
                || request.getForceReaccept() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        ContentAgreementSetting setting = requireSetting(true);
        setting.rulesVersion = request.getRulesVersion().trim();
        setting.privacyVersion = request.getPrivacyVersion().trim();
        setting.rulesText = request.getRulesText().trim();
        setting.privacyText = request.getPrivacyText().trim();
        setting.forceReaccept = request.getForceReaccept();
        if (mapper.update(setting) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        return ContentAgreementVO.from(setting, null);
    }

    @Override
    @Transactional
    public ContentAgreementVO accept(long userId, ContentAgreementAcceptRequest request) {
        ContentAgreementSetting setting = requireSetting(true);
        if (request == null || !setting.rulesVersion.equals(request.getRulesVersion())
                || !setting.privacyVersion.equals(request.getPrivacyVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        mapper.insertAcceptance(userId, setting.rulesVersion, setting.privacyVersion);
        ContentAgreementAcceptance acceptance = mapper.selectCurrentAcceptance(userId,
                setting.rulesVersion, setting.privacyVersion);
        return ContentAgreementVO.from(setting, acceptance.acceptedAt);
    }

    /** 发布事务锁定当前版本，配置切换后旧接受记录不能越过重签门禁。 */
    @Override
    @Transactional
    public void requireAccepted(long userId) {
        var user = users.selectForUpdate(userId);
        if (user == null || user.status != EnableStatus.ACTIVE || user.deletedAt != null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,ErrorCode.USER_DISABLED);
        }
        ContentAgreementSetting setting = requireSetting(true);
        if (accepted(userId, setting) == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.AGREEMENT_REQUIRED);
        }
    }

    private ContentAgreementAcceptance accepted(long userId, ContentAgreementSetting setting) {
        return Boolean.TRUE.equals(setting.forceReaccept)
                ? mapper.selectCurrentAcceptance(userId, setting.rulesVersion, setting.privacyVersion)
                : mapper.selectLatestAcceptance(userId);
    }

    private ContentAgreementSetting requireSetting(boolean lock) {
        ContentAgreementSetting setting = lock ? mapper.selectSingletonForUpdate() : mapper.selectSingleton();
        if (setting == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
        }
        return setting;
    }

    private boolean valid(String value, int maxLength) {
        return value != null && !value.isBlank() && value.trim().length() <= maxLength;
    }
}
