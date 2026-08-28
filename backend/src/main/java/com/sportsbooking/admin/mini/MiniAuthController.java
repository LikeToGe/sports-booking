package com.sportsbooking.admin.mini;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mini/auth")
public class MiniAuthController {
    private final MiniAuthService miniAuthService;

    public MiniAuthController(MiniAuthService miniAuthService) {
        this.miniAuthService = miniAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest body) {
        return ApiResponse.success(miniAuthService.login(body == null ? null : body.getCode()));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody RefreshRequest body) {
        return ApiResponse.success(miniAuthService.refresh(body == null ? null : body.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        MiniSessionPrincipal principal = requiredUser(request);
        miniAuthService.logout(principal.getSessionId());
        return ApiResponse.success(null);
    }

    private MiniSessionPrincipal requiredUser(HttpServletRequest request) {
        Object value = request.getAttribute(MiniSessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof MiniSessionPrincipal) || ((MiniSessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "小程序登录状态已失效");
        }
        return (MiniSessionPrincipal) value;
    }

    public static class LoginRequest {
        private String code;
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class RefreshRequest {
        private String refreshToken;
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }
}
