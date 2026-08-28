package com.sportsbooking.admin.payment;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.mini.MiniIdempotencyService;
import com.sportsbooking.admin.mini.MiniSessionAuthenticationFilter;
import com.sportsbooking.admin.mini.MiniSessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mini/payments")
public class MockPaymentController {
    private final MockPaymentService mockPaymentService;
    private final MiniIdempotencyService miniIdempotencyService;

    public MockPaymentController(MockPaymentService mockPaymentService, MiniIdempotencyService miniIdempotencyService) {
        this.mockPaymentService = mockPaymentService;
        this.miniIdempotencyService = miniIdempotencyService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody CreatePaymentRequest body) {
        MiniSessionPrincipal principal = requiredUser(request);
        long orderId = body == null || body.getOrderId() == null ? 0 : body.getOrderId();
        return miniIdempotencyService.execute(principal.getUserId(), "POST:/api/mini/payments", request.getHeader("Idempotency-Key"), String.valueOf(orderId),
            () -> mockPaymentService.create(principal.getUserId(), orderId));
    }

    @GetMapping("/{paymentId}")
    public ApiResponse<Map<String, Object>> get(HttpServletRequest request, @PathVariable("paymentId") long paymentId) {
        return ApiResponse.success(mockPaymentService.get(requiredUser(request).getUserId(), paymentId));
    }

    @PostMapping("/{paymentId}/mock-result")
    public ApiResponse<Map<String, Object>> mockResult(HttpServletRequest request, @PathVariable("paymentId") long paymentId,
                                                        @RequestBody MockResultRequest body) {
        MiniSessionPrincipal principal = requiredUser(request);
        String fingerprint = String.valueOf(paymentId) + ':' + (body == null ? null : body.getOutcome()) + ':'
            + (body == null ? null : body.getProviderEventNo());
        return miniIdempotencyService.execute(principal.getUserId(), "POST:/api/mini/payments/{paymentId}/mock-result", request.getHeader("Idempotency-Key"),
            fingerprint, () -> mockPaymentService.applyMockResult(principal.getUserId(), paymentId, body));
    }

    private MiniSessionPrincipal requiredUser(HttpServletRequest request) {
        Object value = request.getAttribute(MiniSessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof MiniSessionPrincipal) || ((MiniSessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "小程序登录状态已失效");
        }
        return (MiniSessionPrincipal) value;
    }

    public static class CreatePaymentRequest {
        private Long orderId;
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }

    public static class MockResultRequest {
        private String outcome;
        private String providerEventNo;
        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
        public String getProviderEventNo() { return providerEventNo; }
        public void setProviderEventNo(String providerEventNo) { this.providerEventNo = providerEventNo; }
    }
}
