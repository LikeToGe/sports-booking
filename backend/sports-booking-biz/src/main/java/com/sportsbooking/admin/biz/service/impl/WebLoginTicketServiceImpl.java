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
    private static final DefaultRedisScript<Long> CONFIRM_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return -1 end
            local data = cjson.decode(value)
            if data.status ~= 'PENDING' then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'KEEPTTL')
            return 1
            """, Long.class);
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return nil end
            local data = cjson.decode(value)
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
        Instant expiresAt = Instant.now().plus(TTL);
        try {
            redisTemplate.opsForValue().set(key(ticket), json(Map.of("status", "PENDING")), TTL);
            String qrContent = qrContent(ticket);
            return new ScanTicketVO(ticket, qrContent, credentialService.createQrDataUrl(qrContent),
                    WebLoginTicketStatus.PENDING, expiresAt);
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    @Override
    public ScanTicketVO get(String ticket) {
        try {
            String value = redisTemplate.opsForValue().get(key(ticket));
            if (value == null) {
                throw invalid();
            }
            long ttlMillis = redisTemplate.getExpire(key(ticket), TimeUnit.MILLISECONDS);
            WebLoginTicketStatus status = WebLoginTicketStatus.valueOf(read(value).get("status").asText());
            String qrContent = qrContent(ticket);
            return new ScanTicketVO(ticket, qrContent, credentialService.createQrDataUrl(qrContent),
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
            Long result = redisTemplate.execute(CONFIRM_SCRIPT, List.of(key(ticket)), json(Map.of(
                    "status", "CONFIRMED", "employeeId", staffVenue.getEmployeeId(), "venueId", venueId)));
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
    public AdminLoginVO consume(String ticket) {
        try {
            String value = redisTemplate.execute(CONSUME_SCRIPT, List.of(key(ticket)));
            if (value == null || value.startsWith("INVALID:")) {
                throw invalid();
            }
            JsonNode data = read(value);
            long employeeId = data.get("employeeId").asLong();
            long venueId = data.get("venueId").asLong();
            AdminPrincipal principal = permissionService.resolveEmployee(employeeId, venueId);
            return new AdminLoginVO(sessionService.createEmployeeAdminSession(employeeId, venueId), principal);
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
