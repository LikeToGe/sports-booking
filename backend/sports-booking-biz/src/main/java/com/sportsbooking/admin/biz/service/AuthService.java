package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.AdminLoginVO;
import com.sportsbooking.admin.biz.pojo.vo.MiniLoginVO;
import com.sportsbooking.admin.biz.pojo.vo.UserProfileVO;

/**
 * 功能描述: 负责后台账号和小程序用户的认证及资料维护。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface AuthService {

    /**
     * 功能描述: 校验后台账号并创建 Redis 会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    AdminLoginVO loginAdmin(String username, String password);

    /**
     * 功能描述: 使用模拟微信 code 查找或创建用户，并建立小程序会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    MiniLoginVO loginMini(String code);

    /**
     * 功能描述: 查询当前小程序用户及附加员工身份。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    UserProfileVO getMiniProfile(long userId);

    /**
     * 功能描述: 更新预约联系人资料。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    UserProfileVO updateMiniProfile(long userId, String contactName, String phone);
}
