package com.sportsbooking.admin.mini;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.booking.BookingCancellationService;
import com.sportsbooking.admin.staff.VerificationService;
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
@RequestMapping("/mini/bookings")
public class MiniBookingController {
    private final MiniBookingService miniBookingService;
    private final MiniIdempotencyService miniIdempotencyService;
    private final BookingCancellationService bookingCancellationService;
    private final VerificationService verificationService;

    public MiniBookingController(MiniBookingService miniBookingService, MiniIdempotencyService miniIdempotencyService,
                                 BookingCancellationService bookingCancellationService, VerificationService verificationService) {
        this.miniBookingService = miniBookingService;
        this.miniIdempotencyService = miniIdempotencyService;
        this.bookingCancellationService = bookingCancellationService;
        this.verificationService = verificationService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody CreateBookingRequest body) {
        MiniSessionPrincipal principal = requiredUser(request);
        String deviceId = request.getHeader("X-Device-Id");
        return miniIdempotencyService.execute(principal.getUserId(), "POST:/api/mini/bookings", request.getHeader("Idempotency-Key"),
            fingerprint(body, deviceId), () -> miniBookingService.create(principal.getUserId(), body, deviceId, request.getRemoteAddr()));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String status) {
        MiniSessionPrincipal principal = requiredUser(request);
        return ApiResponse.success(miniBookingService.listMine(principal.getUserId(), page, pageSize, status));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<Map<String, Object>> detail(HttpServletRequest request, @PathVariable("orderId") long orderId) {
        MiniSessionPrincipal principal = requiredUser(request);
        return ApiResponse.success(miniBookingService.getMine(principal.getUserId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(HttpServletRequest request, @PathVariable("orderId") long orderId) {
        MiniSessionPrincipal principal = requiredUser(request);
        return miniIdempotencyService.execute(principal.getUserId(), "POST:/api/mini/bookings/{orderId}/cancel", request.getHeader("Idempotency-Key"),
            String.valueOf(orderId), () -> bookingCancellationService.cancelByUser(principal.getUserId(), orderId));
    }

    @PostMapping("/{orderId}/verification-credential")
    public ApiResponse<Map<String, Object>> issueVerificationCredential(HttpServletRequest request, @PathVariable("orderId") long orderId) {
        MiniSessionPrincipal principal = requiredUser(request);
        return miniIdempotencyService.execute(principal.getUserId(), "POST:/api/mini/bookings/{orderId}/verification-credential",
            request.getHeader("Idempotency-Key"), String.valueOf(orderId), () -> verificationService.issueCredentials(principal.getUserId(), orderId));
    }

    private MiniSessionPrincipal requiredUser(HttpServletRequest request) {
        Object value = request.getAttribute(MiniSessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof MiniSessionPrincipal) || ((MiniSessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "小程序登录状态已失效");
        }
        return (MiniSessionPrincipal) value;
    }

    private String fingerprint(CreateBookingRequest body, String deviceId) {
        if (body == null) {
            return "-1:";
        }
        return part(String.valueOf(body.getInventorySlotId())) + part(body.getContactName()) + part(body.getContactPhone())
            + part(body.getPrivacyPolicyVersion()) + part(body.getPrivacyConsentScene()) + part(deviceId);
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static class CreateBookingRequest {
        private Long inventorySlotId;
        private String contactName;
        private String contactPhone;
        private String privacyPolicyVersion;
        private String privacyConsentScene;
        public Long getInventorySlotId() { return inventorySlotId; }
        public void setInventorySlotId(Long inventorySlotId) { this.inventorySlotId = inventorySlotId; }
        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }
        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
        public String getPrivacyPolicyVersion() { return privacyPolicyVersion; }
        public void setPrivacyPolicyVersion(String privacyPolicyVersion) { this.privacyPolicyVersion = privacyPolicyVersion; }
        public String getPrivacyConsentScene() { return privacyConsentScene; }
        public void setPrivacyConsentScene(String privacyConsentScene) { this.privacyConsentScene = privacyConsentScene; }
    }
}
