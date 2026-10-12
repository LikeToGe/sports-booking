package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.po.ContentAgreementAcceptance;
import com.sportsbooking.admin.base.po.ContentAgreementSetting;
import org.apache.ibatis.annotations.Param;

/** 社区协议当前配置与不可变接受记录。 */
public interface ContentAgreementMapper {
    ContentAgreementSetting selectSingleton();

    ContentAgreementSetting selectSingletonForUpdate();

    int update(@Param("setting") ContentAgreementSetting setting);

    ContentAgreementAcceptance selectCurrentAcceptance(@Param("userId") long userId,
            @Param("rulesVersion") String rulesVersion, @Param("privacyVersion") String privacyVersion);

    ContentAgreementAcceptance selectLatestAcceptance(@Param("userId") long userId);

    int insertAcceptance(@Param("userId") long userId, @Param("rulesVersion") String rulesVersion,
                         @Param("privacyVersion") String privacyVersion);
}
