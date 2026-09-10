package com.sportsbooking.admin.server.interceptor;

import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.model.MiniPrincipal;
import com.sportsbooking.admin.biz.constants.AuthConstants;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.SessionService;
import com.sportsbooking.admin.server.context.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthInterceptorTest {

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void writesValidatedMiniSessionToContextAndClearsItAfterRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mini/bookings/list");
        request.setContextPath("/api");
        request.addHeader(AuthConstants.MINI_TOKEN_HEADER, "mini-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthInterceptor interceptor = new AuthInterceptor(new MiniSessionService());

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(AuthContext.getMiniUserId()).isEqualTo(42L);

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThatThrownBy(AuthContext::getMiniUserId).isInstanceOf(ApiException.class);
    }

    private static final class MiniSessionService implements SessionService {
        @Override
        public MiniPrincipal requireMini(String token) {
            assertThat(token).isEqualTo("mini-token");
            return new MiniPrincipal(42L);
        }

        @Override
        public String createAdminSession(AdminPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String createMiniSession(MiniPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AdminPrincipal requireAdmin(String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAdminSession(String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteMiniSession(String token) {
            throw new UnsupportedOperationException();
        }
    }
}
