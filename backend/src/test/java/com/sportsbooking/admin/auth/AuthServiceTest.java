package com.sportsbooking.admin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.sportsbooking.admin.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
    @Test
    void locksPlatformAccountOnFifthFailedCredentialWithoutCreatingSession() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        SessionRepository sessionRepository = org.mockito.Mockito.mock(SessionRepository.class);
        TokenService tokenService = org.mockito.Mockito.mock(TokenService.class);
        RedisAvailabilityService redis = org.mockito.Mockito.mock(RedisAvailabilityService.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthService.Account account = new AuthService.Account(7L, "platform", encoder.encode("correct-password"),
            "平台管理员", "PLATFORM_ADMIN", null, "ACTIVE", false, 4, null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("platform"))).thenReturn(List.of(account));
        AuthService service = new AuthService(jdbcTemplate, encoder, sessionRepository, tokenService, redis, 8, "246810");

        ApiException error = assertThrows(ApiException.class, () -> service.login("platform", "correct-password", "000000"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        verify(jdbcTemplate).update(anyString(), eq(5), eq(5), eq(7L), eq(7L));
        verify(sessionRepository, never()).create(any(Long.class), anyString(), anyString(), any(Integer.class));
    }
}
