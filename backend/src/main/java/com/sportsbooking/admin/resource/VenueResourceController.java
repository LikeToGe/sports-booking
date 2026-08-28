package com.sportsbooking.admin.resource;

import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/venue/resources")
public class VenueResourceController {
    private final VenueResourceService venueResourceService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public VenueResourceController(VenueResourceService venueResourceService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.venueResourceService = venueResourceService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @GetMapping("/courts")
    public ApiResponse<Map<String, Object>> listCourts(HttpServletRequest request,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       @RequestParam(required = false) String status) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(venueResourceService.listCourts(principal.getStoreId(), page, pageSize, status));
    }

    @PostMapping("/courts")
    public ApiResponse<Map<String, Object>> createCourt(HttpServletRequest request, @RequestBody CreatePhysicalCourtRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/resources/courts",
            request.getHeader("Idempotency-Key"), createCourtFingerprint(body), true,
            () -> venueResourceService.createCourt(principal.getAccountId(), principal.getStoreId(), body));
    }

    @GetMapping("/sellable-units")
    public ApiResponse<Map<String, Object>> listSellableUnits(HttpServletRequest request,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int pageSize,
                                                               @RequestParam(required = false) String status) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(venueResourceService.listSellableUnits(principal.getStoreId(), page, pageSize, status));
    }

    @GetMapping("/courts/{courtId}")
    public ApiResponse<Map<String, Object>> getCourt(HttpServletRequest request, @PathVariable("courtId") long courtId) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(venueResourceService.getCourt(principal.getStoreId(), courtId));
    }

    @PatchMapping("/courts/{courtId}/status")
    public ApiResponse<Void> updateCourtStatus(HttpServletRequest request, @PathVariable("courtId") long courtId,
                                               @RequestBody ResourceStatusUpdateRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "PATCH:/api/venue/resources/courts/{courtId}/status",
            request.getHeader("Idempotency-Key"), part(String.valueOf(courtId)) + part(body.getStatus()), true,
            () -> {
                venueResourceService.updateCourtStatus(principal.getAccountId(), principal.getStoreId(), courtId, body.getStatus());
                return null;
            });
    }

    @PatchMapping("/sellable-units/{sellableUnitId}/status")
    public ApiResponse<Void> updateSellableUnitStatus(HttpServletRequest request,
                                                       @PathVariable("sellableUnitId") long sellableUnitId,
                                                       @RequestBody ResourceStatusUpdateRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "PATCH:/api/venue/resources/sellable-units/{sellableUnitId}/status",
            request.getHeader("Idempotency-Key"), part(String.valueOf(sellableUnitId)) + part(body.getStatus()), true,
            () -> {
                venueResourceService.updateSellableUnitStatus(principal.getAccountId(), principal.getStoreId(), sellableUnitId, body.getStatus());
                return null;
            });
    }

    @GetMapping("/sellable-units/{sellableUnitId}/versions")
    public ApiResponse<Map<String, Object>> listVersions(HttpServletRequest request,
                                                          @PathVariable("sellableUnitId") long sellableUnitId,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int pageSize) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(venueResourceService.listVersions(principal.getStoreId(), sellableUnitId, page, pageSize));
    }

    @PostMapping("/sellable-units/{sellableUnitId}/versions")
    public ApiResponse<Map<String, Object>> createVersion(HttpServletRequest request,
                                                           @PathVariable("sellableUnitId") long sellableUnitId,
                                                           @RequestBody CreateSellableUnitVersionRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/resources/sellable-units/{sellableUnitId}/versions",
            request.getHeader("Idempotency-Key"), part(String.valueOf(sellableUnitId)) + resourceIdsFingerprint(body.getBaseResourceIds()), true,
            () -> venueResourceService.createVersion(principal.getAccountId(), principal.getStoreId(), sellableUnitId, body.getBaseResourceIds()));
    }

    private SessionPrincipal requiredVenueAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"VENUE_ADMIN".equals(principal.getRole()) || principal.getStoreId() == null || principal.getOrganizationId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场地资源访问权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    private String createCourtFingerprint(CreatePhysicalCourtRequest body) {
        return part(stringify(body.getSportCategoryId())) + part(stringify(body.getFullRentalTypeId())) + part(stringify(body.getHalfRentalTypeId()))
            + part(body.getName()) + part(stringify(body.getSortOrder()));
    }

    private String resourceIdsFingerprint(List<Long> resourceIds) {
        if (resourceIds == null) {
            return "-1:";
        }
        List<String> values = new ArrayList<String>();
        for (Long resourceId : resourceIds) {
            values.add(resourceId == null ? "null" : String.valueOf(resourceId));
        }
        Collections.sort(values);
        StringBuilder result = new StringBuilder();
        result.append(values.size()).append(':');
        for (String value : values) {
            result.append(part(value));
        }
        return result.toString();
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static class CreatePhysicalCourtRequest {
        private Long sportCategoryId;
        private Long fullRentalTypeId;
        private Long halfRentalTypeId;
        private String name;
        private Integer sortOrder;
        public Long getSportCategoryId() { return sportCategoryId; }
        public void setSportCategoryId(Long sportCategoryId) { this.sportCategoryId = sportCategoryId; }
        public Long getFullRentalTypeId() { return fullRentalTypeId; }
        public void setFullRentalTypeId(Long fullRentalTypeId) { this.fullRentalTypeId = fullRentalTypeId; }
        public Long getHalfRentalTypeId() { return halfRentalTypeId; }
        public void setHalfRentalTypeId(Long halfRentalTypeId) { this.halfRentalTypeId = halfRentalTypeId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class CreateSellableUnitVersionRequest {
        private List<Long> baseResourceIds;
        public List<Long> getBaseResourceIds() { return baseResourceIds; }
        public void setBaseResourceIds(List<Long> baseResourceIds) { this.baseResourceIds = baseResourceIds; }
    }

    public static class ResourceStatusUpdateRequest {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
