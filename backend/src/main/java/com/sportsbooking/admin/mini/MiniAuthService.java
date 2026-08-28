package com.sportsbooking.admin.mini;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.auth.RedisAvailabilityService;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MiniAuthService {
    private static final Pattern MOCK_CODE = Pattern.compile("mock:[A-Za-z0-9_-]{3,64}");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final RedisAvailabilityService redisAvailabilityService;

    public MiniAuthService(JdbcTemplate jdbcTemplate, TokenService tokenService, RedisAvailabilityService redisAvailabilityService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
        this.redisAvailabilityService = redisAvailabilityService;
    }

    @Transactional
    public Map<String, Object> login(String code) {
        redisAvailabilityService.requireAvailable();
        String providerSubjectHash = exchangeMockWechatCode(code);
        List<MiniUser> users = jdbcTemplate.query("SELECT id, status FROM mini_user WHERE identity_provider = 'MOCK_WECHAT' AND provider_subject_hash = ? AND is_deleted = 0 FOR UPDATE",
            (resultSet, rowNum) -> new MiniUser(resultSet.getLong("id"), resultSet.getString("status")), providerSubjectHash);
        long userId;
        if (users.isEmpty()) {
            jdbcTemplate.update("INSERT INTO mini_user (identity_provider, provider_subject_hash, status, create_operator_id, modify_operator_id) "
                    + "VALUES ('MOCK_WECHAT', ?, 'ACTIVE', 0, 0)", providerSubjectHash);
            userId = lastInsertId();
        } else {
            if (!"ACTIVE".equals(users.get(0).status)) {
                throw new ApiException(HttpStatus.FORBIDDEN, 0, "用户账号已停用");
            }
            userId = users.get(0).id;
        }
        return issueSession(userId);
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken) {
        redisAvailabilityService.requireAvailable();
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "刷新令牌参数不正确");
        }
        List<Long> userIds = jdbcTemplate.query("SELECT s.mini_user_id FROM mini_session s JOIN mini_user u ON u.id = s.mini_user_id "
                + "WHERE s.refresh_token_hash = ? AND s.revoked_at IS NULL AND s.refresh_expires_at > UTC_TIMESTAMP() AND s.is_deleted = 0 "
                + "AND u.status = 'ACTIVE' AND u.is_deleted = 0 FOR UPDATE",
            (resultSet, rowNum) -> resultSet.getLong("mini_user_id"), tokenService.hash(refreshToken));
        if (userIds.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "刷新令牌已失效");
        }
        jdbcTemplate.update("UPDATE mini_session SET revoked_at = UTC_TIMESTAMP(), revoke_reason = 'REFRESHED', modify_operator_id = 0 "
                + "WHERE refresh_token_hash = ? AND revoked_at IS NULL AND is_deleted = 0", tokenService.hash(refreshToken));
        return issueSession(userIds.get(0));
    }

    @Transactional
    public void logout(long sessionId) {
        jdbcTemplate.update("UPDATE mini_session SET revoked_at = COALESCE(revoked_at, UTC_TIMESTAMP()), revoke_reason = 'LOGOUT', modify_operator_id = 0 WHERE id = ? AND is_deleted = 0", sessionId);
    }

    private Map<String, Object> issueSession(long userId) {
        String accessToken = tokenService.generate();
        String refreshToken = tokenService.generate();
        LocalDateTime accessExpiresAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(2);
        LocalDateTime refreshExpiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
        jdbcTemplate.update("INSERT INTO mini_session (mini_user_id, access_token_hash, refresh_token_hash, access_expires_at, refresh_expires_at, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, ?, ?, 0, 0)", userId, tokenService.hash(accessToken), tokenService.hash(refreshToken), accessExpiresAt, refreshExpiresAt);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("userId", String.valueOf(userId));
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("accessExpiresAt", DATETIME_FORMATTER.format(accessExpiresAt));
        return data;
    }

    private String exchangeMockWechatCode(String code) {
        if (!StringUtils.hasText(code) || !MOCK_CODE.matcher(code).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "开发环境仅接受 mock: 前缀的小程序授权码");
        }
        return tokenService.hash(code.substring("mock:".length()));
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("无法读取新增小程序用户ID");
        }
        return id;
    }

    private static final class MiniUser {
        private final long id;
        private final String status;
        private MiniUser(long id, String status) {
            this.id = id;
            this.status = status;
        }
    }
}
