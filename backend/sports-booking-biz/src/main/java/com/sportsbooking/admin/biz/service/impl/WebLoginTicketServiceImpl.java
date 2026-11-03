package com.sportsbooking.admin.biz.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.WebLoginTicketStatus;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.biz.constants.AuthConstants;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.AdminLoginVO;
import com.sportsbooking.admin.biz.pojo.vo.ScanTicketVO;
import com.sportsbooking.admin.biz.pojo.vo.StaffVenueVO;
import com.sportsbooking.admin.biz.service.PermissionService;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.service.SessionService;
import com.sportsbooking.admin.biz.service.TokenService;
import com.sportsbooking.admin.biz.service.WebLoginTicketService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Redis 短期票据实现，确认和消费均由 Lua 保证一次性。 */
@Service
public class WebLoginTicketServiceImpl implements WebLoginTicketService {
    private static final Duration TTL = Duration.ofMinutes(2);
    // 按读取到的原值比较后写入，避免 Lua 重新编码 JSON 改变发起端密钥摘要或数字精度。
    private static final DefaultRedisScript<Long> CONFIRM_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return -1 end
            if value ~= ARGV[2] then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'KEEPTTL')
            return 1
            """, Long.class);
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return nil end
            local data = cjson.decode(value)
            if data.secretHash ~= ARGV[1] then return 'INVALID:SECRET' end
            if data.status ~= 'CONFIRMED' then return 'INVALID:' .. data.status end
            redis.call('SET', KEYS[1], '{"status":"CONSUMED"}', 'KEEPTTL')
            return value
            """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final PermissionService permissionService;
    private final SessionService sessionService;
    private final CredentialService credentialService;

    public WebLoginTicketServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                     TokenService tokenService, PermissionService permissionService,
                                     SessionService sessionService, CredentialService credentialService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.permissionService = permissionService;
        this.sessionService = sessionService;
        this.credentialService = credentialService;
    }

    @Override
    public ScanTicketVO create() {
        String ticket = tokenService.generate();
        // 二维码只承载票据；发起端密钥只返回给创建页面，拍到二维码的人无法轮询或换取会话。
        String clientSecret = tokenService.generate();
        Instant expiresAt = Instant.now().plus(TTL);
        try {
            redisTemplate.opsForValue().set(key(ticket), json(Map.of("status", "PENDING",
                    "secretHash", tokenService.hash(clientSecret))), TTL);
            String qrContent = qrContent(ticket);
            return new ScanTicketVO(ticket, clientSecret, qrContent, credentialService.createQrDataUrl(qrContent),
                    WebLoginTicketStatus.PENDING, expiresAt);
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    @Override
    public ScanTicketVO get(String ticket, String clientSecret) {
        try {
            String value = redisTemplate.opsForValue().get(key(ticket));
            if (value == null) {
                throw invalid();
            }
            JsonNode data = read(value);
            // 密钥不匹配与票据不存在返回相同结果，不向持有二维码的人暴露确认状态。
            if (!secretHash(clientSecret).equals(data.path("secretHash").asText())) {
                throw invalid();
            }
            long ttlMillis = redisTemplate.getExpire(key(ticket), TimeUnit.MILLISECONDS);
            WebLoginTicketStatus status = WebLoginTicketStatus.valueOf(data.get("status").asText());
            String qrContent = qrContent(ticket);
            return new ScanTicketVO(ticket, null, qrContent, credentialService.createQrDataUrl(qrContent),
                    status, Instant.now().plusMillis(Math.max(ttlMillis, 0)));
        } catch (ApiException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    @Override
    public void confirm(String ticket, long userId, long venueId) {
        StaffVenueVO staffVenue = permissionService.listStaffVenues(userId).stream()
                .filter(item -> item.getVenueId() == venueId).findFirst().orElseThrow(this::forbidden);
        try {
            String pending = redisTemplate.opsForValue().get(key(ticket));
            if (pending == null) {
                throw invalid();
            }
            JsonNode data = read(pending);
            if (!"PENDING".equals(data.path("status").asText()) || !data.hasNonNull("secretHash")) {
                throw invalid();
            }
            // 确认结果保留发起端密钥摘要，消费时仍只认创建票据的 Web 页面。
            Long result = redisTemplate.execute(CONFIRM_SCRIPT, List.of(key(ticket)), json(Map.of(
                    "status", "CONFIRMED", "employeeId", staffVenue.getEmployeeId(), "venueId", venueId,
                    "userId", userId, "secretHash", data.get("secretHash").asText())), pending);
            if (result == null || result != 1) {
                throw invalid();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    @Override
    public AdminLoginVO consume(String ticket, String clientSecret) {
        try {
            String value = redisTemplate.execute(CONSUME_SCRIPT, List.of(key(ticket)), secretHash(clientSecret));
            if (value == null || value.startsWith("INVALID:")) {
                throw invalid();
            }
            JsonNode data = read(value);
            long employeeId = data.get("employeeId").asLong();
            long venueId = data.get("venueId").asLong();
            // 消费时再次复核确认票据的原用户，解绑或换绑后不能用旧确认票据登录。
            if (!data.has("userId")) throw invalid();
            long userId = data.get("userId").asLong();
            AdminPrincipal principal = permissionService.resolveEmployee(employeeId, venueId, userId);
            return new AdminLoginVO(sessionService.createEmployeeAdminSession(employeeId, venueId, userId), principal);
        } catch (ApiException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    private String key(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw invalid();
        }
        return AuthConstants.WEB_LOGIN_TICKET_KEY_PREFIX + tokenService.hash(ticket);
    }

    /**
     * 计算发起端密钥摘要；缺少密钥时按无效票据处理。
     *
     * @param clientSecret 发起端密钥
     * @return 密钥摘要
     * @author gjm
     */
    private String secretHash(String clientSecret) {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw invalid();
        }
        return tokenService.hash(clientSecret);
    }

    private String qrContent(String ticket) {
        return "sports-booking://staff/web-login?ticket=" + ticket;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw unavailable();
        }
    }

    private JsonNode read(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
    }

    private ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SESSION_UNAVAILABLE);
    }
}
