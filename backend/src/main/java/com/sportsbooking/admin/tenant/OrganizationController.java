package com.sportsbooking.admin.tenant;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.auth.IdempotencyService;
import com.sportsbooking.admin.auth.SessionAuthenticationFilter;
import com.sportsbooking.admin.auth.SessionPrincipal;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public OrganizationController(OrganizationService organizationService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.organizationService = organizationService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword) {
        requiredPlatformAdmin(request);
        return ApiResponse.success(organizationService.list(page, pageSize, status, keyword));
    }

    @GetMapping("/{organizationId}")
    public ApiResponse<Map<String, Object>> get(HttpServletRequest request, @PathVariable long organizationId) {
        requiredPlatformAdmin(request);
        return ApiResponse.success(organizationService.get(organizationId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody CreateOrganizationRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/organizations",
            request.getHeader("Idempotency-Key"), fingerprint(body), true,
            () -> organizationService.create(principal.getAccountId(), body.getOrganizationName(), body.getStoreName(),
                body.getAdminDisplayName(), body.getAdminUsername(), body.getInitialPassword(), body.getServiceRateBasisPoints()));
    }

    @PatchMapping("/{organizationId}/status")
    public ApiResponse<Void> updateStatus(HttpServletRequest request, @PathVariable long organizationId, @RequestBody StatusUpdateRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        String status = body == null ? null : body.getStatus();
        String reason = body == null ? null : body.getReason();
        return idempotencyService.execute(principal.getAccountId(), "PATCH:/api/admin/organizations/{organizationId}/status",
            request.getHeader("Idempotency-Key"), part(String.valueOf(organizationId)) + part(status) + part(reason), true,
            () -> {
                organizationService.updateOrganizationStatus(principal.getAccountId(), organizationId, status, reason);
                return null;
            });
    }

    @PostMapping("/{organizationId}/admin-password/reset")
    public ApiResponse<Void> resetVenueAdminPassword(HttpServletRequest request, @PathVariable long organizationId,
                                                      @RequestBody ResetVenueAdminPasswordRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        String newPassword = body == null ? null : body.getNewPassword();
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/organizations/{organizationId}/admin-password/reset",
            request.getHeader("Idempotency-Key"), part(String.valueOf(organizationId)) + part(newPassword), true,
            () -> {
                organizationService.resetVenueAdminPassword(principal.getAccountId(), organizationId, newPassword);
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
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无创建场馆权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    private String fingerprint(CreateOrganizationRequest body) {
        return part(body.getOrganizationName()) + part(body.getStoreName()) + part(body.getAdminDisplayName())
            + part(body.getAdminUsername()) + part(body.getInitialPassword()) + part(String.valueOf(body.getServiceRateBasisPoints()));
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static class CreateOrganizationRequest {
        private String organizationName;
        private String storeName;
        private String adminDisplayName;
        private String adminUsername;
        private String initialPassword;
        private Integer serviceRateBasisPoints;
        public String getOrganizationName() { return organizationName; }
        public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
        public String getStoreName() { return storeName; }
        public void setStoreName(String storeName) { this.storeName = storeName; }
        public String getAdminDisplayName() { return adminDisplayName; }
        public void setAdminDisplayName(String adminDisplayName) { this.adminDisplayName = adminDisplayName; }
        public String getAdminUsername() { return adminUsername; }
        public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
        public String getInitialPassword() { return initialPassword; }
        public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
        public Integer getServiceRateBasisPoints() { return serviceRateBasisPoints; }
        public void setServiceRateBasisPoints(Integer serviceRateBasisPoints) { this.serviceRateBasisPoints = serviceRateBasisPoints; }
    }

    public static class StatusUpdateRequest {
        private String status;
        private String reason;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ResetVenueAdminPasswordRequest {
        private String newPassword;
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
