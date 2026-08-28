package com.sportsbooking.admin.staff;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.mini.MiniIdempotencyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/staff/verifications")
public class StaffVerificationController {
    private final VerificationService verificationService;

    public StaffVerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/qr/precheck")
    public ApiResponse<Map<String, Object>> precheckQr(HttpServletRequest request, @RequestBody QrPrecheckRequest body) {
        return ApiResponse.success(verificationService.precheckQr(requiredStaff(request), body == null ? null : body.getQrToken()));
    }

    @PostMapping("/short-code/precheck")
    public ApiResponse<Map<String, Object>> precheckShortCode(HttpServletRequest request, @RequestBody ShortCodePrecheckRequest body) {
        return ApiResponse.success(verificationService.precheckShortCode(requiredStaff(request), body == null ? null : body.getShortCode()));
    }

    @PostMapping("/{credentialId}/confirm")
    public ApiResponse<Map<String, Object>> confirm(HttpServletRequest request, @PathVariable long credentialId, @RequestBody ConfirmVerificationRequest body) {
        return ApiResponse.success(verificationService.confirm(requiredStaff(request), credentialId, body == null ? null : body.getVerificationMethod()));
    }

    @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> listMine(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(verificationService.listMine(requiredStaff(request), page, pageSize));
    }

    private StaffSessionPrincipal requiredStaff(HttpServletRequest request) {
        Object value = request.getAttribute(StaffSessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof StaffSessionPrincipal) || ((StaffSessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "店员登录状态已失效");
        }
        return (StaffSessionPrincipal) value;
    }

    public static class QrPrecheckRequest {
        private String qrToken;
        public String getQrToken() { return qrToken; }
        public void setQrToken(String qrToken) { this.qrToken = qrToken; }
    }

    public static class ShortCodePrecheckRequest {
        private String shortCode;
        public String getShortCode() { return shortCode; }
        public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    }

    public static class ConfirmVerificationRequest {
        private String verificationMethod;
        public String getVerificationMethod() { return verificationMethod; }
        public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }
    }
}
