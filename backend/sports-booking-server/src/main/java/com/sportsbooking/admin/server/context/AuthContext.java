package com.sportsbooking.admin.server.context;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.model.MiniPrincipal;
import com.sportsbooking.admin.biz.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 功能描述: 保存当前请求已通过 Token 校验的后台或小程序身份。
 *
 * @author gjm
 */
public final class AuthContext {
    private static final ThreadLocal<AdminPrincipal> ADMIN_PRINCIPAL = new ThreadLocal<>();
    private static final ThreadLocal<MiniPrincipal> MINI_PRINCIPAL = new ThreadLocal<>();

    private AuthContext() {
    }

    /**
     * 写入当前后台登录身份。
     *
     * @param principal 后台登录身份
     */
    public static void setAdminPrincipal(AdminPrincipal principal) {
        ADMIN_PRINCIPAL.set(principal);
    }

    /**
     * 获取当前后台登录身份。
     *
     * @return 后台登录身份
     */
    public static AdminPrincipal getAdminPrincipal() {
        AdminPrincipal principal = ADMIN_PRINCIPAL.get();
        if (principal == null) {
            throw unauthorized();
        }
        return principal;
    }

    /**
     * 写入当前小程序登录身份。
     *
     * @param principal 小程序登录身份
     */
    public static void setMiniPrincipal(MiniPrincipal principal) {
        MINI_PRINCIPAL.set(principal);
    }

    /**
     * 获取当前小程序用户 ID。
     *
     * @return 当前用户 ID
     */
    public static long getMiniUserId() {
        MiniPrincipal principal = MINI_PRINCIPAL.get();
        if (principal == null) {
            throw unauthorized();
        }
        return principal.getUserId();
    }

    /**
     * 清理当前线程身份，防止 Web 线程复用导致用户串用。
     */
    public static void clear() {
        ADMIN_PRINCIPAL.remove();
        MINI_PRINCIPAL.remove();
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }
}
