package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.model.MiniPrincipal;

/**
 * 功能描述: 管理后台与小程序的随机令牌会话。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface SessionService {
    /**
     * 功能描述: 创建后台会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    String createAdminSession(AdminPrincipal principal);
    /** 创建只保存员工和场馆标识的后台会话。 */
    default String createEmployeeAdminSession(long employeeId, long venueId) {
        throw new UnsupportedOperationException();
    }
    /**
     * 功能描述: 创建小程序会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    String createMiniSession(MiniPrincipal principal);
    /**
     * 功能描述: 读取有效后台会话，否则抛出未登录。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    AdminPrincipal requireAdmin(String token);
    /**
     * 功能描述: 读取有效小程序会话，否则抛出未登录。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    MiniPrincipal requireMini(String token);
    /**
     * 功能描述: 删除后台会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    void deleteAdminSession(String token);
    /**
     * 功能描述: 删除小程序会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    void deleteMiniSession(String token);
}
