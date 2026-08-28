package com.sportsbooking.admin.staff;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/staff/binding")
public class StaffBindingController {
    private final StaffBindingService staffBindingService;

    public StaffBindingController(StaffBindingService staffBindingService) {
        this.staffBindingService = staffBindingService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> bind(@RequestBody BindStaffRequest body) {
        return ApiResponse.success(staffBindingService.bind(body == null ? null : body.getBindingCode(), body == null ? null : body.getCode()));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody RefreshStaffSessionRequest body) {
        return ApiResponse.success(staffBindingService.refresh(body == null ? null : body.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        staffBindingService.logout(requiredStaff(request).getSessionId());
        return ApiResponse.success(null);
    }

    @GetMapping("/session")
    public ApiResponse<Map<String, Object>> session(HttpServletRequest request) {
        return ApiResponse.success(staffBindingService.profile(requiredStaff(request)));
    }

    private StaffSessionPrincipal requiredStaff(HttpServletRequest request) {
        Object value = request.getAttribute(StaffSessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof StaffSessionPrincipal) || ((StaffSessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "店员登录状态已失效");
        }
        return (StaffSessionPrincipal) value;
    }

    public static class BindStaffRequest {
        private String bindingCode;
        private String code;
        public String getBindingCode() { return bindingCode; }
        public void setBindingCode(String bindingCode) { this.bindingCode = bindingCode; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class RefreshStaffSessionRequest {
        private String refreshToken;
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }
}
