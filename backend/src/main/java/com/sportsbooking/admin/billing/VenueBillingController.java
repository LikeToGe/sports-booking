package com.sportsbooking.admin.billing;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.auth.SessionAuthenticationFilter;
import com.sportsbooking.admin.auth.SessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/venue/billing")
public class VenueBillingController {
    private final BillingService billingService;

    public VenueBillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/fee-ledgers")
    public ApiResponse<Map<String, Object>> listLedgers(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize,
                                                         @RequestParam(required = false) String billingPeriod) {
        return ApiResponse.success(billingService.listLedgers(requiredVenueAdmin(request).getStoreId(), page, pageSize, billingPeriod));
    }

    @GetMapping("/monthly-bills")
    public ApiResponse<Map<String, Object>> listBills(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       @RequestParam(required = false) String billingPeriod,
                                                       @RequestParam(required = false) String billStatus) {
        return ApiResponse.success(billingService.listBills(requiredVenueAdmin(request).getStoreId(), page, pageSize, billingPeriod, billStatus));
    }

    @GetMapping("/monthly-bills/{billId}")
    public ApiResponse<Map<String, Object>> getBill(HttpServletRequest request, @PathVariable long billId) {
        return ApiResponse.success(billingService.getBill(requiredVenueAdmin(request).getStoreId(), billId));
    }

    private SessionPrincipal requiredVenueAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"VENUE_ADMIN".equals(principal.getRole()) || principal.getStoreId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场馆账单查看权限");
        }
        return principal;
    }
}
