package com.sportsbooking.admin.catalog;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/venue/catalog")
public class VenueCatalogController {
    private final CatalogService catalogService;

    public VenueCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/sport-categories")
    public ApiResponse<Map<String, Object>> listActiveSportCategories(HttpServletRequest request,
                                                                        @RequestParam(defaultValue = "1") int page,
                                                                        @RequestParam(defaultValue = "20") int pageSize) {
        requiredVenueAdmin(request);
        return ApiResponse.success(catalogService.listSportCategories(page, pageSize, "ACTIVE"));
    }

    @GetMapping("/rental-types")
    public ApiResponse<Map<String, Object>> listActiveRentalTypes(HttpServletRequest request,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                                    @RequestParam(required = false) Long sportCategoryId) {
        requiredVenueAdmin(request);
        return ApiResponse.success(catalogService.listRentalTypes(page, pageSize, sportCategoryId, "ACTIVE"));
    }

    private void requiredVenueAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"VENUE_ADMIN".equals(principal.getRole()) || principal.getStoreId() == null || principal.getOrganizationId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场馆字典访问权限");
        }
    }
}
