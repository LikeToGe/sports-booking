package com.sportsbooking.admin.server.support;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.server.context.AuthContext;
import org.springframework.http.HttpStatus;

/**
 * 功能描述: 从认证上下文读取并校验后台身份角色范围。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public final class Principals {
    private Principals() {
    }

    /**
     * 功能描述: 获取并校验平台管理员身份。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 平台管理员身份
     * @author gjm
     */
    public static AdminPrincipal platform() {
        AdminPrincipal principal = AuthContext.getAdminPrincipal();
        if (principal.getRole() != AdminRole.PLATFORM_ADMIN) {
            throw forbidden();
        }
        return principal;
    }

    /**
     * 功能描述: 获取并校验场馆管理员身份。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 场馆管理员身份
     * @author gjm
     */
    public static AdminPrincipal venue() {
        AdminPrincipal principal = AuthContext.getAdminPrincipal();
        if (principal.getRole() != AdminRole.VENUE_ADMIN || principal.getVenueId() == null) {
            throw forbidden();
        }
        return principal;
    }

    /**
     * 功能描述: 构造统一无权限错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 无权限异常
     * @author gjm
     */
    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }
}
