package com.sportsbooking.admin.refund;

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
@RequestMapping("/mini/refunds")
public class MiniRefundController {
    private final RefundService refundService;
    private final MiniIdempotencyService miniIdempotencyService;

    public MiniRefundController(RefundService refundService, MiniIdempotencyService miniIdempotencyService) {
        this.refundService = refundService;
        this.miniIdempotencyService = miniIdempotencyService;
    }

    @GetMapping("/{refundId}")
    public ApiResponse<Map<String, Object>> get(HttpServletRequest request, @PathVariable("refundId") long refundId) {
        return ApiResponse.success(refundService.getForUser(requiredUser(request).getUserId(), refundId));
    }

    @PostMapping("/{refundId}/mock-result")
    public ApiResponse<Map<String, Object>> mockResult(HttpServletRequest request, @PathVariable("refundId") long refundId,
                                                        @RequestBody MockRefundResultRequest body) {
        MiniSessionPrincipal principal = requiredUser(request);
        String outcome = body == null ? null : body.getOutcome();
        return miniIdempotencyService.execute(principal.getUserId(), "POST:/api/mini/refunds/{refundId}/mock-result", request.getHeader("Idempotency-Key"),
            refundId + ":" + outcome, () -> refundService.applyMockResult(principal.getUserId(), refundId, outcome));
    }

    private MiniSessionPrincipal requiredUser(HttpServletRequest request) {
        Object value = request.getAttribute(MiniSessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof MiniSessionPrincipal) || ((MiniSessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "小程序登录状态已失效");
        }
        return (MiniSessionPrincipal) value;
    }

    public static class MockRefundResultRequest {
        private String outcome;
        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
    }
}
