package com.sportsbooking.admin.employee;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.auth.IdempotencyService;
import com.sportsbooking.admin.auth.SessionAuthenticationFilter;
import com.sportsbooking.admin.auth.SessionPrincipal;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/venue/employees")
public class VenueEmployeeController {
    private final VenueEmployeeService venueEmployeeService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public VenueEmployeeController(VenueEmployeeService venueEmployeeService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.venueEmployeeService = venueEmployeeService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(venueEmployeeService.list(requiredVenueAdmin(request).getStoreId(), page, pageSize));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody CreateEmployeeRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        String name = body == null ? null : body.getEmployeeName();
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/employees", request.getHeader("Idempotency-Key"), part(name), true,
            () -> venueEmployeeService.create(principal.getAccountId(), principal.getOrganizationId(), principal.getStoreId(), name));
    }

    @GetMapping("/verifications")
    public ApiResponse<Map<String, Object>> listVerifications(HttpServletRequest request,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int pageSize,
                                                               @RequestParam(required = false) String result,
                                                               @RequestParam(required = false) Long employeeId,
                                                               @RequestParam(required = false) String date) {
        return ApiResponse.success(venueEmployeeService.listVerifications(requiredVenueAdmin(request).getStoreId(), page, pageSize, result, employeeId, date));
    }

    @PatchMapping("/{employeeId}/status")
    public ApiResponse<Map<String, Object>> updateStatus(HttpServletRequest request, @PathVariable long employeeId, @RequestBody EmployeeStatusRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        String status = body == null ? null : body.getEmployeeStatus();
        return idempotencyService.execute(principal.getAccountId(), "PATCH:/api/venue/employees/{employeeId}/status", request.getHeader("Idempotency-Key"),
            part(String.valueOf(employeeId)) + part(status), true,
            () -> venueEmployeeService.updateStatus(principal.getAccountId(), principal.getStoreId(), employeeId, status));
    }

    @PostMapping("/{employeeId}/binding-code")
    public ApiResponse<Map<String, Object>> issueBindingCode(HttpServletRequest request, @PathVariable long employeeId) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/employees/{employeeId}/binding-code", request.getHeader("Idempotency-Key"),
            String.valueOf(employeeId), true, () -> venueEmployeeService.issueBindingCode(principal.getAccountId(), principal.getStoreId(), employeeId));
    }

    private SessionPrincipal requiredVenueAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"VENUE_ADMIN".equals(principal.getRole()) || principal.getStoreId() == null || principal.getOrganizationId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无店员管理权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static class CreateEmployeeRequest {
        private String employeeName;
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    }

    public static class EmployeeStatusRequest {
        private String employeeStatus;
        public String getEmployeeStatus() { return employeeStatus; }
        public void setEmployeeStatus(String employeeStatus) { this.employeeStatus = employeeStatus; }
    }
}
