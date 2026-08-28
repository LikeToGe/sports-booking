package com.sportsbooking.admin.auth;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;

@RestController
@RequestMapping("/admin/auth")
public class AuthController {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final AuthService authService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;
    private final boolean secureCookie;
    private final int sessionTtlHours;

    public AuthController(AuthService authService, IdempotencyService idempotencyService, TokenService tokenService,
                          @Value("${app.session.secure-cookie}") boolean secureCookie,
                          @Value("${app.session.ttl-hours}") int sessionTtlHours) {
        this.authService = authService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
        this.secureCookie = secureCookie;
        this.sessionTtlHours = sessionTtlHours;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.getUsername(), request.getPassword(), request.getLocalMfaCode());
        ResponseCookie cookie = ResponseCookie.from("ADMIN_SESSION", result.getSessionToken())
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Strict")
            .path("/api")
            .maxAge(sessionTtlHours * 60L * 60L)
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .header("X-CSRF-Token", result.getCsrfToken())
            .body(ApiResponse.success(loginData(result)));
    }

    @GetMapping("/session")
    public ApiResponse<Map<String, Object>> session(HttpServletRequest request) {
        SessionPrincipal principal = requiredPrincipal(request);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("accountId", String.valueOf(principal.getAccountId()));
        data.put("username", principal.getUsername());
        data.put("displayName", principal.getDisplayName());
        data.put("role", principal.getRole());
        data.put("organizationId", stringify(principal.getOrganizationId()));
        data.put("storeId", stringify(principal.getStoreId()));
        data.put("forcePasswordChange", principal.isForcePasswordChange());
        data.put("expiresAt", DATETIME_FORMATTER.format(principal.getExpiresAt()));
        return ApiResponse.success(data);
    }

    @PostMapping("/password/change")
    public ApiResponse<Void> changePassword(HttpServletRequest request, @RequestBody ChangePasswordRequest body) {
        SessionPrincipal principal = requiredPrincipal(request);
        requireCsrf(request, principal);
        String requestFingerprint = part(body.getCurrentPassword()) + part(body.getNewPassword()) + part(body.getConfirmPassword());
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/auth/password/change",
            request.getHeader("Idempotency-Key"), requestFingerprint, true,
            () -> {
                authService.changePassword(principal, body.getCurrentPassword(), body.getNewPassword(), body.getConfirmPassword());
                return null;
            });
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        SessionPrincipal principal = principalForLogout(request);
        requireCsrf(request, principal);
        ApiResponse<Void> response = idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/auth/logout",
            request.getHeader("Idempotency-Key"), "", !principal.isRevoked(),
            () -> {
                authService.logout(principal);
                return null;
            });
        ResponseCookie cookie = ResponseCookie.from("ADMIN_SESSION", "")
            .httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api").maxAge(0).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(response);
    }

    private Map<String, Object> loginData(AuthService.LoginResult result) {
        AuthService.Account account = result.getAccount();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("accountId", String.valueOf(account.getId()));
        data.put("username", account.getUsername());
        data.put("displayName", account.getDisplayName());
        data.put("role", account.getRole());
        data.put("organizationId", null);
        data.put("storeId", stringify(account.getStoreId()));
        data.put("forcePasswordChange", account.isForcePasswordChange());
        return data;
    }

    private SessionPrincipal requiredPrincipal(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (principal.isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        return principal;
    }

    private SessionPrincipal principalForLogout(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal)) {
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

    private String stringify(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static class LoginRequest {
        private String username;
        private String password;
        private String localMfaCode;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getLocalMfaCode() { return localMfaCode; }
        public void setLocalMfaCode(String localMfaCode) { this.localMfaCode = localMfaCode; }
    }

    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
        private String confirmPassword;
        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }
}
