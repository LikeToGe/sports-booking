package com.sportsbooking.admin.auth;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public IdempotencyService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, BCryptPasswordEncoder passwordEncoder,
                              TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public <T> ApiResponse<T> execute(long accountId, String scope, String key, String requestFingerprint, boolean allowNew, Supplier<T> operation) {
        if (key == null || key.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "缺少 Idempotency-Key");
        }
        StoredResponse existing = find(accountId, scope, key);
        if (existing != null) {
            return replay(existing, requestFingerprint);
        }
        if (!allowNew) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        try {
            jdbcTemplate.update("INSERT INTO idempotency_record (account_id, request_scope, idempotency_key, request_hash, response_body, http_status, expires_at, create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, JSON_OBJECT(), 200, ?, ?, ?)",
                accountId, scope, key, protectedFingerprint(requestFingerprint), LocalDateTime.now(ZoneOffset.UTC).plusHours(24), accountId, accountId);
        } catch (DuplicateKeyException exception) {
            return replay(requiredRecord(accountId, scope, key), requestFingerprint);
        }
        ApiResponse<T> response = ApiResponse.success(operation.get());
        try {
            jdbcTemplate.update("UPDATE idempotency_record SET response_body = ?, http_status = 200, modify_operator_id = ? WHERE account_id = ? AND request_scope = ? AND idempotency_key = ?",
                objectMapper.writeValueAsString(response), accountId, accountId, scope, key);
        } catch (Exception exception) {
            throw new IllegalStateException("幂等响应序列化失败", exception);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> replay(StoredResponse record, String requestFingerprint) {
        if (!passwordEncoder.matches(tokenService.hash(requestFingerprint), record.requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "幂等键对应的请求内容不一致");
        }
        try {
            JsonNode body = objectMapper.readTree(record.responseBody);
            T data = body.path("data").isNull() ? null : (T) objectMapper.convertValue(body.path("data"), Object.class);
            return ApiResponse.failure(data, body.path("code").asInt(), body.path("msg").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("幂等响应读取失败", exception);
        }
    }

    private StoredResponse requiredRecord(long accountId, String scope, String key) {
        StoredResponse record = find(accountId, scope, key);
        if (record == null) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "幂等请求处理中，请重试");
        }
        return record;
    }

    private StoredResponse find(long accountId, String scope, String key) {
        List<StoredResponse> records = jdbcTemplate.query("SELECT request_hash, response_body FROM idempotency_record WHERE account_id = ? AND request_scope = ? AND idempotency_key = ? AND is_deleted = 0",
            (resultSet, rowNum) -> new StoredResponse(resultSet.getString("request_hash"), resultSet.getString("response_body")), accountId, scope, key);
        return records.isEmpty() ? null : records.get(0);
    }

    private String protectedFingerprint(String requestFingerprint) {
        return passwordEncoder.encode(tokenService.hash(requestFingerprint));
    }

    private static class StoredResponse {
        private final String requestHash;
        private final String responseBody;
        private StoredResponse(String requestHash, String responseBody) {
            this.requestHash = requestHash;
            this.responseBody = responseBody;
        }
    }
}
