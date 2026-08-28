package com.sportsbooking.admin.catalog;

import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/catalog")
public class CatalogController {
    private final CatalogService catalogService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public CatalogController(CatalogService catalogService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.catalogService = catalogService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @GetMapping("/sport-categories")
    public ApiResponse<Map<String, Object>> listSportCategories(HttpServletRequest request,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                                  @RequestParam(required = false) String status) {
        requiredPlatformAdmin(request);
        return ApiResponse.success(catalogService.listSportCategories(page, pageSize, status));
    }

    @GetMapping("/sport-categories/tree")
    public ApiResponse<List<Map<String, Object>>> sportCategoryTree(HttpServletRequest request) {
        requiredPlatformAdmin(request);
        return ApiResponse.success(catalogService.sportCategoryTree());
    }

    @PostMapping("/sport-categories")
    public ApiResponse<Map<String, Object>> createSportCategory(HttpServletRequest request, @RequestBody SportCategoryUpsertRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/catalog/sport-categories",
            request.getHeader("Idempotency-Key"), categoryFingerprint(body), true,
            () -> catalogService.createSportCategory(principal.getAccountId(), body.getParentId(), body.getCode(), body.getName(),
                body.getIcon(), body.getSortOrder()));
    }

    @PutMapping("/sport-categories/{categoryId}")
    public ApiResponse<Map<String, Object>> updateSportCategory(HttpServletRequest request, @PathVariable("categoryId") long categoryId,
                                                                  @RequestBody SportCategoryUpsertRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "PUT:/api/admin/catalog/sport-categories/{categoryId}",
            request.getHeader("Idempotency-Key"), part(String.valueOf(categoryId)) + categoryFingerprint(body), true,
            () -> catalogService.updateSportCategory(principal.getAccountId(), categoryId, body.getParentId(), body.getCode(), body.getName(),
                body.getIcon(), body.getSortOrder()));
    }

    @PatchMapping("/sport-categories/{categoryId}/status")
    public ApiResponse<Void> updateSportCategoryStatus(HttpServletRequest request, @PathVariable("categoryId") long categoryId,
                                                         @RequestBody StatusUpdateRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "PATCH:/api/admin/catalog/sport-categories/{categoryId}/status",
            request.getHeader("Idempotency-Key"), part(String.valueOf(categoryId)) + part(body.getStatus()), true,
            () -> {
                catalogService.updateSportCategoryStatus(principal.getAccountId(), categoryId, body.getStatus());
                return null;
            });
    }

    @GetMapping("/rental-types")
    public ApiResponse<Map<String, Object>> listRentalTypes(HttpServletRequest request,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              @RequestParam(required = false) Long sportCategoryId,
                                                              @RequestParam(required = false) String status) {
        requiredPlatformAdmin(request);
        return ApiResponse.success(catalogService.listRentalTypes(page, pageSize, sportCategoryId, status));
    }

    @PostMapping("/rental-types")
    public ApiResponse<Map<String, Object>> createRentalType(HttpServletRequest request, @RequestBody RentalTypeUpsertRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/admin/catalog/rental-types",
            request.getHeader("Idempotency-Key"), rentalTypeFingerprint(body), true,
            () -> catalogService.createRentalType(principal.getAccountId(), body.getSportCategoryId(), body.getName()));
    }

    @PutMapping("/rental-types/{rentalTypeId}")
    public ApiResponse<Map<String, Object>> updateRentalType(HttpServletRequest request, @PathVariable("rentalTypeId") long rentalTypeId,
                                                               @RequestBody RentalTypeUpsertRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "PUT:/api/admin/catalog/rental-types/{rentalTypeId}",
            request.getHeader("Idempotency-Key"), part(String.valueOf(rentalTypeId)) + rentalTypeFingerprint(body), true,
            () -> catalogService.updateRentalType(principal.getAccountId(), rentalTypeId, body.getSportCategoryId(), body.getName()));
    }

    @PatchMapping("/rental-types/{rentalTypeId}/status")
    public ApiResponse<Void> updateRentalTypeStatus(HttpServletRequest request, @PathVariable("rentalTypeId") long rentalTypeId,
                                                     @RequestBody StatusUpdateRequest body) {
        SessionPrincipal principal = requiredPlatformAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "PATCH:/api/admin/catalog/rental-types/{rentalTypeId}/status",
            request.getHeader("Idempotency-Key"), part(String.valueOf(rentalTypeId)) + part(body.getStatus()), true,
            () -> {
                catalogService.updateRentalTypeStatus(principal.getAccountId(), rentalTypeId, body.getStatus());
                return null;
            });
    }

    private SessionPrincipal requiredPlatformAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"PLATFORM_ADMIN".equals(principal.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无平台字典维护权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    private String categoryFingerprint(SportCategoryUpsertRequest body) {
        return part(stringify(body.getParentId())) + part(body.getCode()) + part(body.getName()) + part(body.getIcon())
            + part(stringify(body.getSortOrder()));
    }

    private String rentalTypeFingerprint(RentalTypeUpsertRequest body) {
        return part(stringify(body.getSportCategoryId())) + part(body.getName());
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static class SportCategoryUpsertRequest {
        private Long parentId;
        private String code;
        private String name;
        private String icon;
        private Integer sortOrder;
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class RentalTypeUpsertRequest {
        private Long sportCategoryId;
        private String name;
        public Long getSportCategoryId() { return sportCategoryId; }
        public void setSportCategoryId(Long sportCategoryId) { this.sportCategoryId = sportCategoryId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class StatusUpdateRequest {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
