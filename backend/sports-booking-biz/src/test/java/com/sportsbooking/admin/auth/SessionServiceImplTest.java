package com.sportsbooking.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.po.UserAccount;
import com.sportsbooking.admin.base.model.MiniPrincipal;
import com.sportsbooking.admin.base.enums.EnableStatus;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.sportsbooking.admin.biz.service.PermissionService;
import com.sportsbooking.admin.biz.service.TokenService;
import com.sportsbooking.admin.biz.service.impl.SessionServiceImpl;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class SessionServiceImplTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserAccountMapper users = mock(UserAccountMapper.class);
    private SessionServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(tokenService.generate()).thenReturn("token");
        when(tokenService.hash(any())).thenReturn("hash");
        service = new SessionServiceImpl(redisTemplate, objectMapper, tokenService, permissionService, users, 12);
    }

    @Test
    void storesBindingIdentityAndResolvesPermissionsOnEveryRequest() throws Exception {
        service.createEmployeeAdminSession(1L, 2L, 3L);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(any(), json.capture(), any(Duration.class));
        assertThat(objectMapper.readTree(json.getValue()).size()).isEqualTo(3);
        assertThat(objectMapper.readTree(json.getValue()).get("employeeId").asLong()).isEqualTo(1L);
        assertThat(objectMapper.readTree(json.getValue()).get("venueId").asLong()).isEqualTo(2L);
        assertThat(objectMapper.readTree(json.getValue()).get("userId").asLong()).isEqualTo(3L);

        when(values.get(any())).thenReturn(json.getValue());
        when(permissionService.resolveEmployee(1L, 2L, 3L)).thenReturn(
                new AdminPrincipal(0L, AdminRole.VENUE_EMPLOYEE, 2L, "员工"));
        service.requireAdmin("token");
        service.requireAdmin("token");
        verify(permissionService, times(2)).resolveEmployee(1L, 2L, 3L);
    }

    @Test
    void rejectsLegacyEmployeeSessionWithoutOriginalUser() {
        when(values.get(any())).thenReturn("{\"employeeId\":1,\"venueId\":2}");

        assertThatThrownBy(() -> service.requireAdmin("token"))
                .isInstanceOf(com.sportsbooking.admin.biz.exception.ApiException.class);
    }

    @Test
    void preservesRevocationWhenExistingTokenIsCheckedAgain() {
        when(values.get(any())).thenReturn("{\"employeeId\":1,\"venueId\":2,\"userId\":3}");
        when(permissionService.resolveEmployee(1L, 2L, 3L))
                .thenReturn(new AdminPrincipal(0L, AdminRole.VENUE_EMPLOYEE, 2L, "员工"))
                .thenThrow(new com.sportsbooking.admin.biz.exception.ApiException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        com.sportsbooking.admin.base.enums.ErrorCode.FORBIDDEN));

        assertThat(service.requireAdmin("token").getVenueId()).isEqualTo(2L);
        assertThatThrownBy(() -> service.requireAdmin("token"))
                .isInstanceOf(com.sportsbooking.admin.biz.exception.ApiException.class);
    }

    @Test
    void keepsPasswordAdministratorSessionCompatible() throws Exception {
        AdminPrincipal expected = new AdminPrincipal(3L, AdminRole.VENUE_ADMIN, 4L, "管理员");
        when(values.get(any())).thenReturn(objectMapper.writeValueAsString(expected));

        AdminPrincipal actual = service.requireAdmin("token");

        assertThat(actual.getAccountId()).isEqualTo(3L);
        assertThat(actual.getRole()).isEqualTo(AdminRole.VENUE_ADMIN);
        assertThat(actual.getVenueId()).isEqualTo(4L);
    }

    @Test
    void rejectsExistingMiniTokenAfterAccountClosure() throws Exception {
        when(values.get(any())).thenReturn(objectMapper.writeValueAsString(new MiniPrincipal(8L)));
        UserAccount user = new UserAccount();
        user.status = EnableStatus.ACTIVE;
        when(users.selectById(8L)).thenReturn(user);
        assertThat(service.requireMini("token").getUserId()).isEqualTo(8L);
        user.deletedAt = java.time.LocalDateTime.now();
        assertThatThrownBy(() -> service.requireMini("token"))
                .isInstanceOf(com.sportsbooking.admin.biz.exception.ApiException.class);
    }
}
