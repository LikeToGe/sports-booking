package com.sportsbooking.admin.transaction;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class TransactionGateController {
    private final TransactionGateService transactionGateService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public TransactionGateController(TransactionGateService transactionGateService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.transactionGateService = transactionGateService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @GetMapping("/admin/organizations/{organizationId}/transaction-gate")
    public ApiResponse<Map<String, Object>> organizationGate(HttpServletRequest request, @PathVariable("organizationId") long organizationId) {
        requiredPlatformAdmin(request);
        return ApiResponse.success(transactionGateService.transactionStatusForScope(TransactionGateService.ORGANIZATION, organizationId));
    }

    @PostMapping("/admin/organizations/{organizationId}/transaction-blockers")
    public ApiResponse<Map<String, Object>> createOrganizationBlocker(HttpServletRequest request, @PathVariable("organizationId") long organizationId,
                                                                        @RequestBody ManualBlockerRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/organizations/{organizationId}/transaction-blockers",
            request.getHeader("Idempotency-Key"), part(String.valueOf(organizationId)) + part(body == null ? null : body.getReason()), true,
            () -> transactionGateService.createManualBlocker(principal.getAccountId(), TransactionGateService.ORGANIZATION, organizationId, reason(body)));
    }

    @PostMapping("/admin/organizations/{organizationId}/transaction-blockers/{blockerId}/release")
    public ApiResponse<Map<String, Object>> releaseOrganizationBlocker(HttpServletRequest request, @PathVariable("organizationId") long organizationId,
                                                                         @PathVariable("blockerId") long blockerId) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/organizations/{organizationId}/transaction-blockers/{blockerId}/release",
            request.getHeader("Idempotency-Key"), part(String.valueOf(organizationId)) + part(String.valueOf(blockerId)), true,
            () -> transactionGateService.releaseBlocker(principal.getAccountId(), TransactionGateService.ORGANIZATION, organizationId, blockerId));
    }

    @GetMapping("/admin/stores/{storeId}/transaction-gate")
    public ApiResponse<Map<String, Object>> storeGate(HttpServletRequest request, @PathVariable("storeId") long storeId) {
        requiredPlatformAdmin(request);
        return ApiResponse.success(transactionGateService.transactionStatusForStore(storeId));
    }

    @PostMapping("/admin/stores/{storeId}/transaction-blockers")
    public ApiResponse<Map<String, Object>> createStoreBlocker(HttpServletRequest request, @PathVariable("storeId") long storeId,
                                                                 @RequestBody ManualBlockerRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/stores/{storeId}/transaction-blockers",
            request.getHeader("Idempotency-Key"), part(String.valueOf(storeId)) + part(body == null ? null : body.getReason()), true,
            () -> transactionGateService.createManualBlocker(principal.getAccountId(), TransactionGateService.STORE, storeId, reason(body)));
    }

    @PostMapping("/admin/stores/{storeId}/transaction-blockers/{blockerId}/release")
    public ApiResponse<Map<String, Object>> releaseStoreBlocker(HttpServletRequest request, @PathVariable("storeId") long storeId,
                                                                  @PathVariable("blockerId") long blockerId) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/stores/{storeId}/transaction-blockers/{blockerId}/release",
            request.getHeader("Idempotency-Key"), part(String.valueOf(storeId)) + part(String.valueOf(blockerId)), true,
            () -> transactionGateService.releaseBlocker(principal.getAccountId(), TransactionGateService.STORE, storeId, blockerId));
    }

    @GetMapping("/venue/transaction-gate")
    public ApiResponse<Map<String, Object>> venueGate(HttpServletRequest request) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(transactionGateService.transactionStatusForStore(principal.getStoreId()));
    }

    @PostMapping("/venue/transaction-blockers")
    public ApiResponse<Map<String, Object>> createVenueBlocker(HttpServletRequest request, @RequestBody ManualBlockerRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/transaction-blockers",
            request.getHeader("Idempotency-Key"), part(body == null ? null : body.getReason()), true,
            () -> transactionGateService.createManualBlocker(principal.getAccountId(), TransactionGateService.STORE, principal.getStoreId(), reason(body)));
    }

    @PostMapping("/venue/transaction-blockers/{blockerId}/release")
    public ApiResponse<Map<String, Object>> releaseVenueBlocker(HttpServletRequest request, @PathVariable("blockerId") long blockerId) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/transaction-blockers/{blockerId}/release",
            request.getHeader("Idempotency-Key"), part(String.valueOf(blockerId)), true,
            () -> transactionGateService.releaseBlocker(principal.getAccountId(), TransactionGateService.STORE, principal.getStoreId(), blockerId));
    }

    private SessionPrincipal requiredPlatformAdmin(HttpServletRequest request) {
        SessionPrincipal principal = requiredPrincipal(request);
        if (!"PLATFORM_ADMIN".equals(principal.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无交易门禁维护权限");
        }
        return principal;
    }

    private SessionPrincipal requiredVenueAdmin(HttpServletRequest request) {
        SessionPrincipal principal = requiredPrincipal(request);
        if (!"VENUE_ADMIN".equals(principal.getRole()) || principal.getStoreId() == null || principal.getOrganizationId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场馆交易门禁维护权限");
        }
        return principal;
    }

    private SessionPrincipal requiredPrincipal(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        return (SessionPrincipal) value;
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

    private String reason(ManualBlockerRequest body) {
        return body == null ? null : body.getReason();
    }

    public static class ManualBlockerRequest {
        private String reason;
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
