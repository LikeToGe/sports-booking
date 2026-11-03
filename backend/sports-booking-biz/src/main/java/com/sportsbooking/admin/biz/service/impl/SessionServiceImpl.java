package com.sportsbooking.admin.biz.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.model.MiniPrincipal;
import com.sportsbooking.admin.biz.service.SessionService;
import com.sportsbooking.admin.biz.service.PermissionService;
import com.sportsbooking.admin.biz.service.TokenService;
import com.sportsbooking.admin.biz.constants.AuthConstants;
import com.sportsbooking.admin.base.mapper.AdminAccountMapper;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.po.AdminAccount;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 功能描述: Redis 随机会话实现；Redis 不可用时拒绝认证请求，避免降级为无状态裸令牌。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class SessionServiceImpl implements SessionService {
    private static final String ADMIN_SESSION_KIND = "admin";
    private static final String MINI_SESSION_KIND = "mini";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final PermissionService permissionService;
    private final Duration ttl;
    private final UserAccountMapper userAccountMapper;
    private final AdminAccountMapper adminAccountMapper;

    public SessionServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                              TokenService tokenService, PermissionService permissionService,
                              UserAccountMapper userAccountMapper, AdminAccountMapper adminAccountMapper,
                              @Value("${app.session.ttl-hours}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.permissionService = permissionService;
        this.userAccountMapper = userAccountMapper;
        this.adminAccountMapper = adminAccountMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * 功能描述: 创建后台管理员随机令牌会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public String createAdminSession(AdminAccount account) {
        // 会话只保存账号标识和凭据戳，角色、场馆和名称每次请求都按账号现状重建。
        return create(ADMIN_SESSION_KIND, Map.of("accountId", account.id,
                "credentialStamp", credentialStamp(account)));
    }

    /** 员工会话保存原绑定身份，避免员工档案换绑后旧用户沿用令牌。 */
    @Override
    public String createEmployeeAdminSession(long employeeId, long venueId, long userId) {
        return create(ADMIN_SESSION_KIND, Map.of("employeeId", employeeId, "venueId", venueId, "userId", userId));
    }

    /**
     * 功能描述: 创建小程序用户随机令牌会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public String createMiniSession(MiniPrincipal principal) {
        return create(MINI_SESSION_KIND, principal);
    }

    /**
     * 功能描述: 读取并续期有效后台会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public AdminPrincipal requireAdmin(String token) {
        String sessionJson = readJson(ADMIN_SESSION_KIND, token);
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(sessionJson);
            if (root.has("employeeId") && !root.has("role")) {
                // 旧格式员工会话没有原绑定用户，重新扫码后才可继续，避免重绑后旧会话复活。
                if (!root.has("userId")) throw unauthorized();
                return permissionService.resolveEmployee(root.get("employeeId").asLong(),
                        root.get("venueId").asLong(), root.get("userId").asLong());
            }
            // 旧格式密码管理员会话缺少凭据戳，无法判断密码是否已重置，统一要求重新登录。
            if (!root.has("accountId") || !root.has("credentialStamp")) throw unauthorized();
            return resolvePasswordAdmin(root.get("accountId").asLong(), root.get("credentialStamp").asText());
        } catch (ApiException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw unauthorized();
        }
    }

    /**
     * 功能描述: 读取并续期有效小程序会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public MiniPrincipal requireMini(String token) {
        MiniPrincipal principal = read(MINI_SESSION_KIND, token, MiniPrincipal.class);
        var user = userAccountMapper.selectById(principal.getUserId());
        if (user == null || user.status != EnableStatus.ACTIVE || user.deletedAt != null) throw unauthorized();
        return principal;
    }

    /**
     * 功能描述: 删除当前后台登录会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void deleteAdminSession(String token) {
        delete(ADMIN_SESSION_KIND, token);
    }

    /**
     * 功能描述: 删除当前小程序登录会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void deleteMiniSession(String token) {
        delete(MINI_SESSION_KIND, token);
    }

    /**
     * 功能描述: 创建随机令牌，仅把摘要写入 Redis，避免 Redis 泄露后直接复用令牌。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    /**
     * 按账号现状校验密码管理员会话；停用账号或重置密码后旧令牌在下一次请求失效。
     *
     * @param accountId 后台账号 ID
     * @param stamp 创建会话时的凭据戳
     * @return 按当前账号资料重建的后台身份
     * @author gjm
     */
    private AdminPrincipal resolvePasswordAdmin(long accountId, String stamp) {
        AdminAccount account = adminAccountMapper.selectById(accountId);
        if (account == null || account.status != EnableStatus.ACTIVE
                || !credentialStamp(account).equals(stamp)) {
            throw unauthorized();
        }
        return new AdminPrincipal(account.id, account.role, account.venueId, account.displayName);
    }

    /**
     * 由当前密码摘要派生凭据戳；BCrypt 每次重置都会生成新摘要，即使设置相同密码也会让旧会话失效。
     *
     * @param account 后台账号
     * @return 凭据戳
     * @author gjm
     */
    private String credentialStamp(AdminAccount account) {
        return tokenService.hash(account.passwordHash);
    }

    private String create(String kind, Object principal) {
        String token = tokenService.generate();
        try {
            redisTemplate.opsForValue().set(key(kind, token), objectMapper.writeValueAsString(principal), ttl);
            return token;
        } catch (DataAccessException | JsonProcessingException exception) {
            throw sessionUnavailable();
        }
    }

    /**
     * 功能描述: 读取并续期会话；无效、过期和反序列化失败均按未登录处理。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private <T> T read(String kind, String token, Class<T> principalType) {
        String sessionJson = readJson(kind, token);
        try {
            return objectMapper.readValue(sessionJson, principalType);
        } catch (JsonProcessingException exception) {
            throw unauthorized();
        }
    }

    /** 读取并续期原始会话 JSON。 */
    private String readJson(String kind, String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized();
        }
        try {
            String sessionJson = redisTemplate.opsForValue().get(key(kind, token));
            if (sessionJson == null) {
                throw unauthorized();
            }
            redisTemplate.expire(key(kind, token), ttl);
            return sessionJson;
        } catch (ApiException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw sessionUnavailable();
        }
    }

    /**
     * 功能描述: 删除指定类型会话；空令牌视为已退出。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private void delete(String kind, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(key(kind, token));
        } catch (DataAccessException exception) {
            throw sessionUnavailable();
        }
    }

    /**
     * 功能描述: 组合会话 Key，业务令牌只以摘要形式进入 Redis。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private String key(String kind, String token) {
        return AuthConstants.SESSION_KEY_PREFIX + kind + ":" + tokenService.hash(token);
    }

    /**
     * 功能描述: 构造统一未登录错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    /**
     * 功能描述: 构造 Redis 会话不可用错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private ApiException sessionUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SESSION_UNAVAILABLE);
    }
}
