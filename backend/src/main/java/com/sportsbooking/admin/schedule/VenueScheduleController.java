package com.sportsbooking.admin.schedule;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/venue")
public class VenueScheduleController {
    private final VenueScheduleService venueScheduleService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public VenueScheduleController(VenueScheduleService venueScheduleService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.venueScheduleService = venueScheduleService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @GetMapping("/schedules/versions")
    public ApiResponse<Map<String, Object>> listScheduleVersions(HttpServletRequest request,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                                   @RequestParam(required = false) String status) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(venueScheduleService.listScheduleVersions(principal.getStoreId(), page, pageSize, status));
    }

    @PostMapping("/schedules/versions")
    public ApiResponse<Map<String, Object>> createScheduleDraft(HttpServletRequest request, @RequestBody ScheduleVersionRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/schedules/versions",
            request.getHeader("Idempotency-Key"), scheduleFingerprint(body), true,
            () -> venueScheduleService.createDraft(principal.getAccountId(), principal.getStoreId(), body));
    }

    @GetMapping("/schedules/versions/{scheduleVersionId}")
    public ApiResponse<Map<String, Object>> getScheduleVersion(HttpServletRequest request,
                                                                 @PathVariable("scheduleVersionId") long scheduleVersionId) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(venueScheduleService.getScheduleVersion(principal.getStoreId(), scheduleVersionId));
    }

    @PutMapping("/schedules/versions/{scheduleVersionId}")
    public ApiResponse<Map<String, Object>> updateScheduleDraft(HttpServletRequest request,
                                                                 @PathVariable("scheduleVersionId") long scheduleVersionId,
                                                                 @RequestBody ScheduleVersionRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "PUT:/api/venue/schedules/versions/{scheduleVersionId}",
            request.getHeader("Idempotency-Key"), part(String.valueOf(scheduleVersionId)) + scheduleFingerprint(body), true,
            () -> venueScheduleService.updateDraft(principal.getAccountId(), principal.getStoreId(), scheduleVersionId, body));
    }

    @PostMapping("/schedules/versions/{scheduleVersionId}/publish")
    public ApiResponse<Map<String, Object>> publishScheduleVersion(HttpServletRequest request,
                                                                     @PathVariable("scheduleVersionId") long scheduleVersionId) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/schedules/versions/{scheduleVersionId}/publish",
            request.getHeader("Idempotency-Key"), part(String.valueOf(scheduleVersionId)), true,
            () -> venueScheduleService.publishDraft(principal.getAccountId(), principal.getStoreId(), scheduleVersionId));
    }

    @GetMapping("/schedules/inventory")
    public ApiResponse<Map<String, Object>> listInventory(HttpServletRequest request, @RequestParam String date,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "50") int pageSize,
                                                           @RequestParam(required = false) String status) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(venueScheduleService.listInventory(principal.getStoreId(), date, page, pageSize, status));
    }

    @GetMapping("/blocks")
    public ApiResponse<Map<String, Object>> listBlocks(HttpServletRequest request,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(required = false) String date,
                                                        @RequestParam(required = false) String status) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(venueScheduleService.listBlocks(principal.getStoreId(), page, pageSize, date, status));
    }

    @PostMapping("/blocks")
    public ApiResponse<Map<String, Object>> createBlock(HttpServletRequest request, @RequestBody CreateManualBlockRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/blocks",
            request.getHeader("Idempotency-Key"), blockFingerprint(body), true,
            () -> venueScheduleService.createManualBlock(principal.getAccountId(), principal.getStoreId(), body));
    }

    @PostMapping("/blocks/{blockId}/cancel")
    public ApiResponse<Void> cancelBlock(HttpServletRequest request, @PathVariable("blockId") long blockId) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "POST:/api/venue/blocks/{blockId}/cancel",
            request.getHeader("Idempotency-Key"), part(String.valueOf(blockId)), true,
            () -> {
                venueScheduleService.cancelManualBlock(principal.getAccountId(), principal.getStoreId(), blockId);
                return null;
            });
    }

    private SessionPrincipal requiredVenueAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"VENUE_ADMIN".equals(principal.getRole()) || principal.getStoreId() == null || principal.getOrganizationId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无排期与库存访问权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    private String scheduleFingerprint(ScheduleVersionRequest body) {
        if (body == null) {
            return "null";
        }
        StringBuilder result = new StringBuilder();
        result.append(part(body.getEffectiveFrom()));
        appendSlots(result, body.getWeeklySlots());
        if (body.getSpecialDates() == null) {
            return result.append("-1:").toString();
        }
        result.append(body.getSpecialDates().size()).append(':');
        for (SpecialDateRequest specialDate : body.getSpecialDates()) {
            if (specialDate == null) {
                result.append("null");
            } else {
                result.append(part(specialDate.getDate())).append(part(specialDate.getSpecialType()));
                appendSlots(result, specialDate.getSlots());
            }
        }
        return result.toString();
    }

    private void appendSlots(StringBuilder result, List<ScheduleSlotRequest> slots) {
        if (slots == null) {
            result.append("-1:");
            return;
        }
        result.append(slots.size()).append(':');
        for (ScheduleSlotRequest slot : slots) {
            if (slot == null) {
                result.append("null");
            } else {
                result.append(part(stringify(slot.getWeekDay()))).append(part(stringify(slot.getSellableUnitVersionId())))
                    .append(part(slot.getStartTime())).append(part(slot.getEndTime())).append(part(stringify(slot.getPriceFen())));
            }
        }
    }

    private String blockFingerprint(CreateManualBlockRequest body) {
        if (body == null) {
            return "null";
        }
        return part(stringify(body.getInventorySlotId())) + part(body.getBlockType()) + part(body.getContactName())
            + part(body.getContactPhone()) + part(body.getRemark());
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static class ScheduleVersionRequest {
        private String effectiveFrom;
        private List<ScheduleSlotRequest> weeklySlots;
        private List<SpecialDateRequest> specialDates;
        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public List<ScheduleSlotRequest> getWeeklySlots() { return weeklySlots; }
        public void setWeeklySlots(List<ScheduleSlotRequest> weeklySlots) { this.weeklySlots = weeklySlots; }
        public List<SpecialDateRequest> getSpecialDates() { return specialDates; }
        public void setSpecialDates(List<SpecialDateRequest> specialDates) { this.specialDates = specialDates; }
    }

    public static class SpecialDateRequest {
        private String date;
        private String specialType;
        private List<ScheduleSlotRequest> slots;
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getSpecialType() { return specialType; }
        public void setSpecialType(String specialType) { this.specialType = specialType; }
        public List<ScheduleSlotRequest> getSlots() { return slots; }
        public void setSlots(List<ScheduleSlotRequest> slots) { this.slots = slots; }
    }

    public static class ScheduleSlotRequest {
        private Integer weekDay;
        private Long sellableUnitVersionId;
        private String startTime;
        private String endTime;
        private Integer priceFen;
        public Integer getWeekDay() { return weekDay; }
        public void setWeekDay(Integer weekDay) { this.weekDay = weekDay; }
        public Long getSellableUnitVersionId() { return sellableUnitVersionId; }
        public void setSellableUnitVersionId(Long sellableUnitVersionId) { this.sellableUnitVersionId = sellableUnitVersionId; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public Integer getPriceFen() { return priceFen; }
        public void setPriceFen(Integer priceFen) { this.priceFen = priceFen; }
    }

    public static class CreateManualBlockRequest {
        private Long inventorySlotId;
        private String blockType;
        private String contactName;
        private String contactPhone;
        private String remark;
        public Long getInventorySlotId() { return inventorySlotId; }
        public void setInventorySlotId(Long inventorySlotId) { this.inventorySlotId = inventorySlotId; }
        public String getBlockType() { return blockType; }
        public void setBlockType(String blockType) { this.blockType = blockType; }
        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }
        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
