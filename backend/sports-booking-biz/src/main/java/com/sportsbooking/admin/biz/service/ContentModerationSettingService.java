package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.request.ContentModerationSettingRequest;
import com.sportsbooking.admin.biz.pojo.vo.ContentModerationSettingVO;

/** 内容审核配置读写和新版本门禁判定。 */
public interface ContentModerationSettingService {
    ContentModerationSettingVO get();

    ContentModerationSettingVO update(ContentModerationSettingRequest request);

    boolean postEnabled();

    boolean reviewEnabled();

    boolean commentEnabled();
}
