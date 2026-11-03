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
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.mockito.ArgumentCaptor;
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
        when(tokenService.hash(any())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        service = new WebLoginTicketServiceImpl(redisTemplate, new ObjectMapper(), tokenService,
                permissionService, sessionService, mock(CredentialService.class));
    }

    @Test
    void rejectsExpiredTicket() {
        when(values.get(any())).thenReturn(null);
        assertThatThrownBy(() -> service.get("expired", "secret")).isInstanceOf(ApiException.class);
    }

    @Test
    void returnsCreatorSecretOnlyOutsideQrContent() {
        when(tokenService.generate()).thenReturn("ticket", "secret");

        var created = service.create();

        assertThat(created.getClientSecret()).isEqualTo("secret");
        assertThat(created.getQrContent()).doesNotContain("secret");
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(values).set(any(), stored.capture(), any(java.time.Duration.class));
        assertThat(stored.getValue()).contains("\"secretHash\":\"hash:secret\"").doesNotContain("\"secret\"");
    }

    @Test
    void rejectsPollingWithoutCreatorSecret() {
        when(values.get(any())).thenReturn("{\"status\":\"CONFIRMED\",\"secretHash\":\"hash:secret\"}");

        // 只拍到二维码的人拿不到发起端密钥，也不能观察票据是否已被员工确认。
        assertThatThrownBy(() -> service.get("ticket", "guess")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.get("ticket", null)).isInstanceOf(ApiException.class);
        when(redisTemplate.getExpire(any(), any())).thenReturn(1000L);
        assertThat(service.get("ticket", "secret").getStatus())
                .isEqualTo(com.sportsbooking.admin.base.enums.WebLoginTicketStatus.CONFIRMED);
    }

    @Test
    void confirmationKeepsCreatorSecretHash() throws Exception {
        String pending = "{\"status\":\"PENDING\",\"secretHash\":\"hash:secret\"}";
        when(values.get(any())).thenReturn(pending);
        when(permissionService.listStaffVenues(1L)).thenReturn(List.of(
                new StaffVenueVO(2L, 3L, "场馆", "员工", List.of())));
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), eq(pending));

        service.confirm("ticket", 1L, 3L);

        ArgumentCaptor<Object> confirmed = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), confirmed.capture(), eq(pending));
        var data = new ObjectMapper().readTree((String) confirmed.getValue());
        assertThat(data.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(data.get("secretHash").asText()).isEqualTo("hash:secret");
    }

    @Test
    void consumptionSendsCreatorSecretHashToAtomicCheck() {
        doReturn("INVALID:SECRET").when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThatThrownBy(() -> service.consume("ticket", "guess")).isInstanceOf(ApiException.class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), eq("hash:guess"));
        assertThatThrownBy(() -> service.consume("ticket", " ")).isInstanceOf(ApiException.class);
        verify(sessionService, never()).createEmployeeAdminSession(anyLong(), anyLong(), anyLong());
    }

    @Test
    void rejectsRepeatedConfirmation() {
        when(permissionService.listStaffVenues(1L)).thenReturn(List.of(
                new StaffVenueVO(2L, 3L, "场馆", "员工", List.of())));
        when(values.get(any())).thenReturn("{\"status\":\"CONFIRMED\",\"secretHash\":\"hash:secret\"}");

        assertThatThrownBy(() -> service.confirm("ticket", 1L, 3L)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsRepeatedConsumption() {
        doReturn("INVALID:CONSUMED").when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThatThrownBy(() -> service.consume("ticket", "secret")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsConsumptionAfterOriginalConfirmingUserIsUnbound() {
        doReturn("{\"status\":\"CONFIRMED\",\"employeeId\":2,\"venueId\":3,\"userId\":1}")
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        when(permissionService.resolveEmployee(2L, 3L, 1L)).thenThrow(new ApiException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                com.sportsbooking.admin.base.enums.ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.consume("ticket", "secret")).isInstanceOf(ApiException.class);
        verify(sessionService, never()).createEmployeeAdminSession(anyLong(), anyLong(), anyLong());
    }

    @Test
    void rejectsLegacyConfirmedTicketWithoutOriginalUser() {
        doReturn("{\"status\":\"CONFIRMED\",\"employeeId\":2,\"venueId\":3}")
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThatThrownBy(() -> service.consume("ticket", "secret")).isInstanceOf(ApiException.class);
        verify(sessionService, never()).createEmployeeAdminSession(anyLong(), anyLong(), anyLong());
    }

    @Test
    void carriesOriginalConfirmingUserIntoSession() {
        doReturn("{\"status\":\"CONFIRMED\",\"employeeId\":2,\"venueId\":3,\"userId\":1}")
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        when(permissionService.resolveEmployee(2L, 3L, 1L)).thenReturn(
                new AdminPrincipal(0L, AdminRole.VENUE_EMPLOYEE, 3L, "员工"));

        service.consume("ticket", "secret");

        verify(sessionService).createEmployeeAdminSession(2L, 3L, 1L);
    }
}
