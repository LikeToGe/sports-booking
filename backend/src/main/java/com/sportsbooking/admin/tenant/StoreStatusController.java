package com.sportsbooking.admin.tenant;

import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.auth.IdempotencyService;
import com.sportsbooking.admin.auth.SessionAuthenticationFilter;
import com.sportsbooking.admin.auth.SessionPrincipal;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/stores")
public class StoreStatusController {
    private final OrganizationService organizationService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public StoreStatusController(OrganizationService organizationService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.organizationService = organizationService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @PatchMapping("/{storeId}/status")
    public ApiResponse<Void> updateStatus(HttpServletRequest request, @PathVariable("storeId") long storeId,
                                          @RequestBody StatusUpdateRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "PATCH:/api/admin/stores/{storeId}/status",
            request.getHeader("Idempotency-Key"), part(String.valueOf(storeId)) + part(body.getStatus()) + part(body.getReason()), true,
            () -> {
                organizationService.updateStoreStatus(principal.getAccountId(), storeId, body.getStatus(), body.getReason());
                return null;
            });
    }

    private SessionPrincipal requiredPlatformAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"PLATFORM_ADMIN".equals(principal.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场馆状态维护权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static class StatusUpdateRequest {
        private String status;
        private String reason;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
