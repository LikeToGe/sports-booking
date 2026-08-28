package com.sportsbooking.admin.billing;

import java.time.LocalDate;
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
@RequestMapping("/admin/billing")
public class PlatformBillingController {
    private final BillingService billingService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public PlatformBillingController(BillingService billingService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.billingService = billingService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @GetMapping("/fee-ledgers")
    public ApiResponse<Map<String, Object>> listLedgers(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize,
                                                         @RequestParam(required = false) String billingPeriod) {
        requiredPlatform(request);
        return ApiResponse.success(billingService.listLedgers(null, page, pageSize, billingPeriod));
    }

    @GetMapping("/monthly-bills")
    public ApiResponse<Map<String, Object>> listBills(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       @RequestParam(required = false) String billingPeriod,
                                                       @RequestParam(required = false) String billStatus) {
        requiredPlatform(request);
        return ApiResponse.success(billingService.listBills(null, page, pageSize, billingPeriod, billStatus));
    }

    @GetMapping("/monthly-bills/{billId}")
    public ApiResponse<Map<String, Object>> getBill(HttpServletRequest request, @PathVariable long billId) {
        requiredPlatform(request);
        return ApiResponse.success(billingService.getBill(null, billId));
    }

    @PostMapping("/monthly-bills/generate")
    public ApiResponse<Map<String, Object>> generate(HttpServletRequest request, @RequestBody GenerateBillRequest body) {
        SessionPrincipal principal = requiredPlatform(request);
        requireCsrf(request, principal);
        String period = body == null ? null : body.getBillingPeriod();
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/billing/monthly-bills/generate", request.getHeader("Idempotency-Key"),
            part(period), true, () -> billingService.generate(principal.getAccountId(), parsePeriod(period)));
    }

    @PostMapping("/monthly-bills/{billId}/issue")
    public ApiResponse<Map<String, Object>> issue(HttpServletRequest request, @PathVariable long billId, @RequestBody IssueBillRequest body) {
        SessionPrincipal principal = requiredPlatform(request);
        requireCsrf(request, principal);
        String dueDate = body == null ? null : body.getDueDate();
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/billing/monthly-bills/{billId}/issue", request.getHeader("Idempotency-Key"),
            part(String.valueOf(billId)) + part(dueDate), true, () -> billingService.issue(principal.getAccountId(), billId, parseDate(dueDate, "账单到期日")));
    }

    @PostMapping("/monthly-bills/{billId}/payments")
    public ApiResponse<Map<String, Object>> payment(HttpServletRequest request, @PathVariable long billId, @RequestBody RecordPaymentRequest body) {
        SessionPrincipal principal = requiredPlatform(request);
        requireCsrf(request, principal);
        int amountFen = body == null || body.getAmountFen() == null ? 0 : body.getAmountFen();
        String remark = body == null ? null : body.getRemark();
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/billing/monthly-bills/{billId}/payments", request.getHeader("Idempotency-Key"),
            part(String.valueOf(billId)) + part(String.valueOf(amountFen)) + part(remark), true,
            () -> billingService.recordOfflinePayment(principal.getAccountId(), billId, amountFen, remark));
    }

    private SessionPrincipal requiredPlatform(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"PLATFORM_ADMIN".equals(principal.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无平台账单管理权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    private LocalDate parsePeriod(String value) {
        if (!StringUtils.hasText(value) || !value.matches("\\d{4}-\\d{2}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "账期参数必须为yyyy-MM");
        }
        try {
            return LocalDate.parse(value + "-01");
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "账期参数必须为yyyy-MM");
        }
    }

    private LocalDate parseDate(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, field + "参数不正确");
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, field + "参数不正确");
        }
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static class GenerateBillRequest {
        private String billingPeriod;
        public String getBillingPeriod() { return billingPeriod; }
        public void setBillingPeriod(String billingPeriod) { this.billingPeriod = billingPeriod; }
    }

    public static class IssueBillRequest {
        private String dueDate;
        public String getDueDate() { return dueDate; }
        public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    }

    public static class RecordPaymentRequest {
        private Integer amountFen;
        private String remark;
        public Integer getAmountFen() { return amountFen; }
        public void setAmountFen(Integer amountFen) { this.amountFen = amountFen; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
