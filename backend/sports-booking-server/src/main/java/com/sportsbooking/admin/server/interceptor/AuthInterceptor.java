package com.sportsbooking.admin.server.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.sportsbooking.admin.biz.service.SessionService;
import com.sportsbooking.admin.biz.constants.AuthConstants;
import com.sportsbooking.admin.server.context.AuthContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 功能描述: 按路由前缀加载后台或小程序会话，并把可信身份写入认证上下文。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final SessionService sessionService;

    /**
     * 功能描述: 创建基于 Redis 会话的认证拦截器。
     * 创建时间: 2026/08/28 16:29
     *
     * @param sessionService 会话业务服务
     * @author gjm
     */
    public AuthInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 功能描述: 按请求路径加载会话并写入可信登录身份。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthContext.clear();
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/platform/") || path.startsWith("/venue-admin/")
                || path.equals("/auth/admin/logout")) {
            AuthContext.setAdminPrincipal(
                    sessionService.requireAdmin(request.getHeader(AuthConstants.ADMIN_TOKEN_HEADER)));
        } else if (path.startsWith("/mini/") || path.startsWith("/staff/")
                || path.startsWith("/auth/mini/profile/") || path.equals("/auth/mini/logout")) {
            AuthContext.setMiniPrincipal(
                    sessionService.requireMini(request.getHeader(AuthConstants.MINI_TOKEN_HEADER)));
        }
        return true;
    }

    /**
     * 功能描述: 请求完成后清理线程身份，避免容器复用线程时身份泄漏。
     *
     * @author gjm
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        AuthContext.clear();
    }
}
