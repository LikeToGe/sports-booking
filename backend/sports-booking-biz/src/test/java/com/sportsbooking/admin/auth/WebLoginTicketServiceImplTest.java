package com.sportsbooking.admin.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.StaffVenueVO;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.service.PermissionService;
import com.sportsbooking.admin.biz.service.SessionService;
import com.sportsbooking.admin.biz.service.TokenService;
import com.sportsbooking.admin.biz.service.impl.WebLoginTicketServiceImpl;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class WebLoginTicketServiceImplTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final TokenService tokenService = mock(TokenService.class);
    private WebLoginTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(tokenService.hash(any())).thenReturn("hash");
        service = new WebLoginTicketServiceImpl(redisTemplate, new ObjectMapper(), tokenService,
                permissionService, sessionService, mock(CredentialService.class));
    }

    @Test
    void rejectsExpiredTicket() {
        when(values.get(any())).thenReturn(null);
        assertThatThrownBy(() -> service.get("expired")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsRepeatedConfirmation() {
        when(permissionService.listStaffVenues(1L)).thenReturn(List.of(
                new StaffVenueVO(2L, 3L, "场馆", "员工", List.of())));
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThatThrownBy(() -> service.confirm("ticket", 1L, 3L)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsRepeatedConsumption() {
        doReturn("INVALID:CONSUMED").when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThatThrownBy(() -> service.consume("ticket")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsConsumptionAfterOriginalConfirmingUserIsUnbound() {
        doReturn("{\"status\":\"CONFIRMED\",\"employeeId\":2,\"venueId\":3,\"userId\":1}")
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        when(permissionService.resolveEmployee(2L, 3L, 1L)).thenThrow(new ApiException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                com.sportsbooking.admin.base.enums.ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.consume("ticket")).isInstanceOf(ApiException.class);
        verify(sessionService, never()).createEmployeeAdminSession(anyLong(), anyLong(), anyLong());
    }

    @Test
    void rejectsLegacyConfirmedTicketWithoutOriginalUser() {
        doReturn("{\"status\":\"CONFIRMED\",\"employeeId\":2,\"venueId\":3}")
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThatThrownBy(() -> service.consume("ticket")).isInstanceOf(ApiException.class);
        verify(sessionService, never()).createEmployeeAdminSession(anyLong(), anyLong(), anyLong());
    }

    @Test
    void carriesOriginalConfirmingUserIntoSession() {
        doReturn("{\"status\":\"CONFIRMED\",\"employeeId\":2,\"venueId\":3,\"userId\":1}")
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        when(permissionService.resolveEmployee(2L, 3L, 1L)).thenReturn(
                new AdminPrincipal(0L, AdminRole.VENUE_EMPLOYEE, 3L, "员工"));

        service.consume("ticket");

        verify(sessionService).createEmployeeAdminSession(2L, 3L, 1L);
    }
}
