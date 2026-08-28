package com.sportsbooking.admin.auth;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.sportsbooking.admin.api.ApiException;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SessionRepository sessionRepository;
    private final TokenService tokenService;
    private final RedisAvailabilityService redisAvailabilityService;
    private final int sessionTtlHours;
    private final String platformMfaCode;

    public AuthService(JdbcTemplate jdbcTemplate, BCryptPasswordEncoder passwordEncoder, SessionRepository sessionRepository,
                       TokenService tokenService, RedisAvailabilityService redisAvailabilityService,
                       @Value("${app.session.ttl-hours}") int sessionTtlHours,
                       @Value("${app.auth.platform-mfa-code}") String platformMfaCode) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
        this.tokenService = tokenService;
        this.redisAvailabilityService = redisAvailabilityService;
        this.sessionTtlHours = sessionTtlHours;
        this.platformMfaCode = platformMfaCode;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String username, String password, String localMfaCode) {
        redisAvailabilityService.requireAvailable();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "用户名或密码错误");
        }
        List<Account> accounts = jdbcTemplate.query("SELECT id, username, password_hash, display_name, role, store_id, status, force_password_change, failed_login_count, locked_until FROM admin_account WHERE username = ? AND is_deleted = 0",
            (resultSet, rowNum) -> {
                long storeId = resultSet.getLong("store_id");
                Long nullableStoreId = resultSet.wasNull() ? null : storeId;
                return new Account(resultSet.getLong("id"), resultSet.getString("username"),
                    resultSet.getString("password_hash"), resultSet.getString("display_name"), resultSet.getString("role"),
                    nullableStoreId, resultSet.getString("status"), resultSet.getBoolean("force_password_change"),
                    resultSet.getInt("failed_login_count"), resultSet.getObject("locked_until", LocalDateTime.class));
            }, username);
        if (accounts.isEmpty()) {
            audit(null, "ADMIN_LOGIN_REJECTED", "ACCOUNT", null, "REJECTED", "账号不存在");
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "用户名或密码错误");
        }
        Account account = accounts.get(0);
        if (account.lockedUntil != null && account.lockedUntil.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            audit(account.id, "ADMIN_LOGIN_REJECTED", "ACCOUNT", account.id, "REJECTED", "账号临时锁定");
            throw new ApiException(HttpStatus.LOCKED, 0, "账号已临时锁定，请 15 分钟后重试");
        }
        boolean validMfa = !"PLATFORM_ADMIN".equals(account.role) || platformMfaCode.equals(localMfaCode);
        if (!"ACTIVE".equals(account.status) || !passwordEncoder.matches(password, account.passwordHash) || !validMfa) {
            recordLoginFailure(account);
            audit(account.id, "ADMIN_LOGIN_REJECTED", "ACCOUNT", account.id, "REJECTED", "凭据校验失败");
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "用户名、密码或二次验证码错误");
        }
        String sessionToken = tokenService.generate();
        String csrfToken = tokenService.generate();
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(sessionTtlHours);
        sessionRepository.create(account.id, tokenService.hash(sessionToken), tokenService.hash(csrfToken), sessionTtlHours);
        jdbcTemplate.update("UPDATE admin_account SET failed_login_count = 0, locked_until = NULL, last_login_at = UTC_TIMESTAMP(), modify_operator_id = ? WHERE id = ?", account.id, account.id);
        audit(account.id, "ADMIN_LOGIN_SUCCEEDED", "ACCOUNT", account.id, "SUCCEEDED", null);
        return new LoginResult(account, sessionToken, csrfToken, expiresAt);
    }

    private void recordLoginFailure(Account account) {
        int failedCount = account.failedLoginCount + 1;
        jdbcTemplate.update("UPDATE admin_account SET failed_login_count = ?, locked_until = CASE WHEN ? >= 5 THEN DATE_ADD(UTC_TIMESTAMP(), INTERVAL 15 MINUTE) ELSE NULL END, modify_operator_id = ? WHERE id = ?",
            failedCount, failedCount, account.id, account.id);
    }

    @Transactional
    public void changePassword(SessionPrincipal principal, String currentPassword, String newPassword, String confirmPassword) {
        if (!StringUtils.hasText(currentPassword) || !StringUtils.hasText(newPassword) || !newPassword.equals(confirmPassword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "密码参数不正确");
        }
        String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM admin_account WHERE id = ? AND is_deleted = 0", String.class, principal.getAccountId());
        if (!passwordEncoder.matches(currentPassword, passwordHash)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "当前密码错误");
        }
        jdbcTemplate.update("UPDATE admin_account SET password_hash = ?, force_password_change = 0, failed_login_count = 0, locked_until = NULL, modify_operator_id = ? WHERE id = ?",
            passwordEncoder.encode(newPassword), principal.getAccountId(), principal.getAccountId());
        sessionRepository.revokeOtherSessions(principal.getAccountId(), principal.getSessionId());
        audit(principal.getAccountId(), "ADMIN_PASSWORD_CHANGED", "ACCOUNT", principal.getAccountId(), "SUCCEEDED", null);
    }

    @Transactional
    public void logout(SessionPrincipal principal) {
        sessionRepository.revoke(principal.getSessionId(), principal.getAccountId(), "LOGOUT");
        audit(principal.getAccountId(), "ADMIN_LOGOUT", "ACCOUNT", principal.getAccountId(), "SUCCEEDED", null);
    }

    private void audit(Long actorAccountId, String action, String targetType, Long targetId, String result, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, result, reason, create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            actorAccountId, action, targetType, targetId, result, reason, actorAccountId == null ? 0L : actorAccountId, actorAccountId == null ? 0L : actorAccountId);
    }

    public static class LoginResult {
        private final Account account;
        private final String sessionToken;
        private final String csrfToken;
        private final LocalDateTime expiresAt;

        LoginResult(Account account, String sessionToken, String csrfToken, LocalDateTime expiresAt) {
            this.account = account;
            this.sessionToken = sessionToken;
            this.csrfToken = csrfToken;
            this.expiresAt = expiresAt;
        }
        public Account getAccount() { return account; }
        public String getSessionToken() { return sessionToken; }
        public String getCsrfToken() { return csrfToken; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }

    public static class Account {
        private final long id;
        private final String username;
        private final String passwordHash;
        private final String displayName;
        private final String role;
        private final Long storeId;
        private final String status;
        private final boolean forcePasswordChange;
        private final int failedLoginCount;
        private final LocalDateTime lockedUntil;

        Account(long id, String username, String passwordHash, String displayName, String role, Long storeId, String status,
                boolean forcePasswordChange, int failedLoginCount, LocalDateTime lockedUntil) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
            this.displayName = displayName;
            this.role = role;
            this.storeId = storeId;
            this.status = status;
            this.forcePasswordChange = forcePasswordChange;
            this.failedLoginCount = failedLoginCount;
            this.lockedUntil = lockedUntil;
        }
        public long getId() { return id; }
        public String getUsername() { return username; }
        public String getDisplayName() { return displayName; }
        public String getRole() { return role; }
        public Long getStoreId() { return storeId; }
        public boolean isForcePasswordChange() { return forcePasswordChange; }
    }
}
