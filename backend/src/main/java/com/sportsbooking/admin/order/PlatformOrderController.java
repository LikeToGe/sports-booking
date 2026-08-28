package com.sportsbooking.admin.order;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/orders")
public class PlatformOrderController {
    private final OrderQueryService orderQueryService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public PlatformOrderController(OrderQueryService orderQueryService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.orderQueryService = orderQueryService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String orderStatus,
                                                  @RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String paymentStatus,
                                                  @RequestParam(required = false) String dateFrom,
                                                  @RequestParam(required = false) String dateTo,
                                                  @RequestParam(required = false) String keyword) {
        requiredPlatform(request);
        return ApiResponse.success(orderQueryService.list(null, page, pageSize, orderStatus, orderNo, paymentStatus, dateFrom, dateTo, keyword));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<Map<String, Object>> get(HttpServletRequest request, @PathVariable long orderId) {
        requiredPlatform(request);
        return ApiResponse.success(orderQueryService.get(null, orderId));
    }

    @PostMapping("/{orderId}/contact-phone/reveal")
    public ApiResponse<Map<String, Object>> revealPhone(HttpServletRequest request, @PathVariable long orderId, @RequestBody RevealPhoneRequest body) {
        SessionPrincipal principal = requiredPlatform(request);
        requireCsrf(request, principal);
        String reason = body == null ? null : body.getReason();
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/orders/{orderId}/contact-phone/reveal", request.getHeader("Idempotency-Key"),
            orderId + ":" + reason, true, () -> orderQueryService.revealPhone(principal.getAccountId(), null, orderId, reason));
    }

    private SessionPrincipal requiredPlatform(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"PLATFORM_ADMIN".equals(principal.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无全局订单权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    public static class RevealPhoneRequest {
        private String reason;
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
