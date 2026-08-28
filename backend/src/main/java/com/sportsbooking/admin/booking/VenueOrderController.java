package com.sportsbooking.admin.booking;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/venue/orders")
public class VenueOrderController {
    private final BookingCancellationService bookingCancellationService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public VenueOrderController(BookingCancellationService bookingCancellationService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.bookingCancellationService = bookingCancellationService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(HttpServletRequest request, @PathVariable("orderId") long orderId,
                                                    @RequestBody VenueCancelRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        String reason = body == null ? null : body.getReason();
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/orders/{orderId}/cancel", request.getHeader("Idempotency-Key"),
            orderId + ":" + reason, true, () -> bookingCancellationService.cancelByVenue(principal.getAccountId(), principal.getStoreId(), orderId, reason));
    }

    private SessionPrincipal requiredVenueAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"VENUE_ADMIN".equals(principal.getRole()) || principal.getStoreId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场馆订单取消权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    public static class VenueCancelRequest {
        private String reason;
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
