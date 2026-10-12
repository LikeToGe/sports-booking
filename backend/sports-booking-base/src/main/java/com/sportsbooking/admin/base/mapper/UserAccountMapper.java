package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.UserAccount;

/**
 * 功能描述: 小程序用户数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    UserAccount selectForUpdate(@org.apache.ibatis.annotations.Param("userId") long userId);
    Integer hasUnsettledFunds(@org.apache.ibatis.annotations.Param("userId") long userId);
    Integer hasUnfinishedBookings(@org.apache.ibatis.annotations.Param("userId") long userId);
    Integer hasIdentityTombstone(@org.apache.ibatis.annotations.Param("identityHash") String identityHash);
    int insertIdentityTombstone(@org.apache.ibatis.annotations.Param("userId") long userId,
            @org.apache.ibatis.annotations.Param("identityHash") String identityHash);
    int closeAccount(@org.apache.ibatis.annotations.Param("userId") long userId);
    int detachEmployees(@org.apache.ibatis.annotations.Param("userId") long userId);
    int hidePosts(@org.apache.ibatis.annotations.Param("userId") long userId);
    int hideComments(@org.apache.ibatis.annotations.Param("userId") long userId);
    int hideReviews(@org.apache.ibatis.annotations.Param("userId") long userId);
}
