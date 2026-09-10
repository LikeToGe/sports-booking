package com.sportsbooking.admin.base.model;

import com.sportsbooking.admin.base.enums.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 后台会话中的最小可信身份。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPrincipal {

    /**
     * 账号 ID
     */
    private long accountId;

    /**
     * 管理员角色
     */
    private AdminRole role;

    /**
     * 场馆 ID
     */
    private Long venueId;

    /**
     * 显示名称
     */
    private String displayName;
}
