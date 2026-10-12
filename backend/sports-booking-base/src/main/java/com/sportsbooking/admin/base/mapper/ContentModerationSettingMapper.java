package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.po.ContentModerationSetting;
import org.apache.ibatis.annotations.Param;

/** 内容审核开关持久化。 */
public interface ContentModerationSettingMapper {
    ContentModerationSetting selectSingleton();
    ContentModerationSetting selectSingletonForUpdate();

    int update(@Param("setting") ContentModerationSetting setting);
}
