package com.sportsbooking.admin.biz.constants;

/**
 * 功能描述: 认证请求头和 Redis Key 前缀。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public final class AuthConstants {
    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    public static final String MINI_TOKEN_HEADER = "X-Mini-Token";
    public static final String SESSION_KEY_PREFIX = "session:";
    public static final String BOOKING_LIMIT_KEY_PREFIX = "limit:booking:create:";
    public static final String WEB_LOGIN_TICKET_KEY_PREFIX = "web-login-ticket:";

    private AuthConstants() {
    }
}
