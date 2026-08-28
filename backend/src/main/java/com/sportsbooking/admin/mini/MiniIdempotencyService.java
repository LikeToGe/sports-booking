package com.sportsbooking.admin.mini;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MiniIdempotencyService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;

    public MiniIdempotencyService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
    }

    @Transactional
    public <T> ApiResponse<T> execute(long userId, String scope, String key, String fingerprint, Supplier<T> operation) {
        if (!StringUtils.hasText(key)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "缺少 Idempotency-Key");
        }
        StoredResponse existing = find(userId, scope, key);
        if (existing != null) {
            return replay(existing, fingerprint);
        }
        try {
            jdbcTemplate.update("INSERT INTO mini_idempotency_record (mini_user_id, request_scope, idempotency_key, request_hash, response_body, http_status, expires_at, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, ?, ?, JSON_OBJECT(), 200, ?, 0, 0)", userId, scope, key, tokenService.hash(fingerprint),
                LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        } catch (DuplicateKeyException exception) {
            return replay(requiredRecord(userId, scope, key), fingerprint);
        }
        ApiResponse<T> response = ApiResponse.success(operation.get());
        try {
            jdbcTemplate.update("UPDATE mini_idempotency_record SET response_body = ?, http_status = 200 WHERE mini_user_id = ? AND request_scope = ? AND idempotency_key = ?",
                objectMapper.writeValueAsString(response), userId, scope, key);
        } catch (Exception exception) {
            throw new IllegalStateException("小程序幂等响应序列化失败", exception);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> replay(StoredResponse record, String fingerprint) {
        if (!tokenService.hash(fingerprint).equals(record.requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "幂等键对应的请求内容不一致");
        }
        try {
            JsonNode body = objectMapper.readTree(record.responseBody);
            T data = body.path("data").isNull() ? null : (T) objectMapper.convertValue(body.path("data"), Object.class);
            return ApiResponse.failure(data, body.path("code").asInt(), body.path("msg").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("小程序幂等响应读取失败", exception);
        }
    }

    private StoredResponse requiredRecord(long userId, String scope, String key) {
        StoredResponse record = find(userId, scope, key);
        if (record == null) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "幂等请求处理中，请重试");
        }
        return record;
    }

    private StoredResponse find(long userId, String scope, String key) {
        List<StoredResponse> records = jdbcTemplate.query("SELECT request_hash, response_body FROM mini_idempotency_record WHERE mini_user_id = ? AND request_scope = ? "
                + "AND idempotency_key = ? AND is_deleted = 0", (resultSet, rowNum) -> new StoredResponse(resultSet.getString("request_hash"),
                resultSet.getString("response_body")), userId, scope, key);
        return records.isEmpty() ? null : records.get(0);
    }

    private static final class StoredResponse {
        private final String requestHash;
        private final String responseBody;
        private StoredResponse(String requestHash, String responseBody) {
            this.requestHash = requestHash;
            this.responseBody = responseBody;
        }
    }
}
