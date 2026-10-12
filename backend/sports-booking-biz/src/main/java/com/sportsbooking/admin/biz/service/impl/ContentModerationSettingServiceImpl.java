package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.mapper.ContentModerationSettingMapper;
import com.sportsbooking.admin.base.po.ContentModerationSetting;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.ContentModerationSettingRequest;
import com.sportsbooking.admin.biz.pojo.vo.ContentModerationSettingVO;
import com.sportsbooking.admin.biz.service.ContentModerationSettingService;
import com.sportsbooking.admin.base.enums.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 内容审核开关的单行配置实现；关闭只影响后续新版本。 */
@Service
public class ContentModerationSettingServiceImpl implements ContentModerationSettingService {
    private final ContentModerationSettingMapper mapper;

    public ContentModerationSettingServiceImpl(ContentModerationSettingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ContentModerationSettingVO get() {
        return ContentModerationSettingVO.from(requireSetting());
    }

    @Override
    @Transactional
    public ContentModerationSettingVO update(ContentModerationSettingRequest request) {
        if (request == null || request.getPostEnabled() == null || request.getCommentEnabled() == null
                || request.getReviewEnabled() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        ContentModerationSetting setting = requireSetting();
        setting.postEnabled = request.getPostEnabled();
        setting.commentEnabled = request.getCommentEnabled();
        setting.reviewEnabled = request.getReviewEnabled();
        if (mapper.update(setting) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        return ContentModerationSettingVO.from(setting);
    }

    @Override
    public boolean postEnabled() {
        return Boolean.TRUE.equals(requireSetting().postEnabled);
    }

    @Override
    public boolean reviewEnabled() {
        return Boolean.TRUE.equals(requireSetting().reviewEnabled);
    }

    @Override
    public boolean commentEnabled() {
        return Boolean.TRUE.equals(requireSetting().commentEnabled);
    }

    private ContentModerationSetting requireSetting() {
        ContentModerationSetting setting = mapper.selectSingleton();
        if (setting == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
        }
        return setting;
    }
}
