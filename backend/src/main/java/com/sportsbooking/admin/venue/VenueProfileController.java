package com.sportsbooking.admin.venue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.auth.IdempotencyService;
import com.sportsbooking.admin.auth.SessionAuthenticationFilter;
import com.sportsbooking.admin.auth.SessionPrincipal;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/venue")
public class VenueProfileController {
    private final VenueProfileService venueProfileService;
    private final IdempotencyService idempotencyService;
    private final TokenService tokenService;

    public VenueProfileController(VenueProfileService venueProfileService, IdempotencyService idempotencyService, TokenService tokenService) {
        this.venueProfileService = venueProfileService;
        this.idempotencyService = idempotencyService;
        this.tokenService = tokenService;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(HttpServletRequest request) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        return ApiResponse.success(venueProfileService.profile(principal.getStoreId()));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(HttpServletRequest request, @RequestBody VenueProfileUpdateRequest body) {
        SessionPrincipal principal = requiredVenueAdmin(request);
        requireCsrf(request, principal);
        return idempotencyService.execute(principal.getAccountId(), "PUT:/api/venue/profile",
            request.getHeader("Idempotency-Key"), fingerprint(body), true,
            () -> venueProfileService.update(principal.getAccountId(), principal.getOrganizationId(), principal.getStoreId(), body));
    }

    private SessionPrincipal requiredVenueAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!"VENUE_ADMIN".equals(principal.getRole()) || principal.getStoreId() == null || principal.getOrganizationId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场馆资料访问权限");
        }
        return principal;
    }

    private void requireCsrf(HttpServletRequest request, SessionPrincipal principal) {
        String token = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(token) || !tokenService.hash(token).equals(principal.getCsrfTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "CSRF 校验失败");
        }
    }

    private String fingerprint(VenueProfileUpdateRequest body) {
        return part(body.getStoreName()) + part(body.getIntroduction()) + part(body.getAddress()) + part(body.getContactPhone())
            + part(decimalText(body.getLongitude())) + part(decimalText(body.getLatitude())) + listPart(body.getImageUrls())
            + listPart(body.getFacilities()) + part(body.getTrafficNote()) + part(body.getEntryNotice()) + part(body.getEquipmentNote())
            + part(body.getParkingNote()) + part(body.getLiabilityNote());
    }

    private String listPart(List<String> values) {
        if (values == null) {
            return "-1:";
        }
        StringBuilder result = new StringBuilder();
        result.append(values.size()).append(':');
        for (String value : values) {
            result.append(part(value));
        }
        return result.toString();
    }

    private String decimalText(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String part(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static class VenueProfileUpdateRequest {
        private String storeName;
        private String introduction;
        private String address;
        private String contactPhone;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private List<String> imageUrls;
        private List<String> facilities;
        private String trafficNote;
        private String entryNotice;
        private String equipmentNote;
        private String parkingNote;
        private String liabilityNote;
        public String getStoreName() { return storeName; }
        public void setStoreName(String storeName) { this.storeName = storeName; }
        public String getIntroduction() { return introduction; }
        public void setIntroduction(String introduction) { this.introduction = introduction; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
        public BigDecimal getLongitude() { return longitude; }
        public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
        public BigDecimal getLatitude() { return latitude; }
        public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
        public List<String> getImageUrls() { return imageUrls; }
        public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
        public List<String> getFacilities() { return facilities; }
        public void setFacilities(List<String> facilities) { this.facilities = facilities; }
        public String getTrafficNote() { return trafficNote; }
        public void setTrafficNote(String trafficNote) { this.trafficNote = trafficNote; }
        public String getEntryNotice() { return entryNotice; }
        public void setEntryNotice(String entryNotice) { this.entryNotice = entryNotice; }
        public String getEquipmentNote() { return equipmentNote; }
        public void setEquipmentNote(String equipmentNote) { this.equipmentNote = equipmentNote; }
        public String getParkingNote() { return parkingNote; }
        public void setParkingNote(String parkingNote) { this.parkingNote = parkingNote; }
        public String getLiabilityNote() { return liabilityNote; }
        public void setLiabilityNote(String liabilityNote) { this.liabilityNote = liabilityNote; }
    }

    @Service
    static class VenueProfileService {
        private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() { };
        private final JdbcTemplate jdbcTemplate;
        private final ObjectMapper objectMapper;

        VenueProfileService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            this.jdbcTemplate = jdbcTemplate;
            this.objectMapper = objectMapper;
        }

        Map<String, Object> profile(Long storeId) {
            if (storeId == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场馆资料访问权限");
            }
            List<Map<String, Object>> results = jdbcTemplate.query(
                "SELECT o.id AS organization_id, o.organization_no, o.name AS organization_name, s.id AS store_id, s.store_no, s.name AS store_name, s.status, s.service_rate_basis_points, "
                    + "s.introduction, s.address, s.contact_phone, s.longitude, s.latitude, s.image_urls, s.facilities, s.traffic_note, s.entry_notice, s.equipment_note, s.parking_note, s.liability_note, s.gmt_created_time "
                    + "FROM store s JOIN organization o ON o.id = s.organization_id "
                    + "WHERE s.id = ? AND s.is_deleted = 0 AND o.is_deleted = 0",
                (resultSet, rowNum) -> profileData(resultSet), storeId);
            if (results.isEmpty()) {
                throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场馆资料访问权限");
            }
            return results.get(0);
        }

        @Transactional
        Map<String, Object> update(long operatorId, Long organizationId, Long storeId, VenueProfileUpdateRequest body) {
            if (storeId == null || organizationId == null || !StringUtils.hasText(body.getStoreName())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "场馆资料参数不正确");
            }
            int updated = jdbcTemplate.update(
                "UPDATE store SET name = ?, introduction = ?, address = ?, contact_phone = ?, longitude = ?, latitude = ?, image_urls = ?, facilities = ?, traffic_note = ?, entry_notice = ?, equipment_note = ?, parking_note = ?, liability_note = ?, modify_operator_id = ? WHERE id = ? AND organization_id = ? AND is_deleted = 0",
                body.getStoreName(), body.getIntroduction(), body.getAddress(), body.getContactPhone(), body.getLongitude(), body.getLatitude(),
                writeList(body.getImageUrls()), writeList(body.getFacilities()), body.getTrafficNote(), body.getEntryNotice(), body.getEquipmentNote(),
                body.getParkingNote(), body.getLiabilityNote(), operatorId, storeId, organizationId);
            if (updated == 0) {
                throw new ApiException(HttpStatus.FORBIDDEN, 0, "无场馆资料访问权限");
            }
            jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) VALUES (?, 'VENUE_PROFILE_UPDATED', 'STORE', ?, ?, ?, 'SUCCEEDED', '场馆资料已更新', ?, ?)",
                operatorId, storeId, organizationId, storeId, operatorId, operatorId);
            return profile(storeId);
        }

        private Map<String, Object> profileData(java.sql.ResultSet resultSet) throws java.sql.SQLException {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("organizationId", String.valueOf(resultSet.getLong("organization_id")));
            data.put("organizationNo", resultSet.getString("organization_no"));
            data.put("organizationName", resultSet.getString("organization_name"));
            data.put("storeId", String.valueOf(resultSet.getLong("store_id")));
            data.put("storeNo", resultSet.getString("store_no"));
            data.put("storeName", resultSet.getString("store_name"));
            data.put("status", resultSet.getString("status"));
            data.put("serviceRateBasisPoints", resultSet.getInt("service_rate_basis_points"));
            data.put("introduction", resultSet.getString("introduction"));
            data.put("address", resultSet.getString("address"));
            data.put("contactPhone", resultSet.getString("contact_phone"));
            data.put("longitude", decimalText(resultSet.getBigDecimal("longitude")));
            data.put("latitude", decimalText(resultSet.getBigDecimal("latitude")));
            data.put("imageUrls", readList(resultSet.getString("image_urls")));
            data.put("facilities", readList(resultSet.getString("facilities")));
            data.put("trafficNote", resultSet.getString("traffic_note"));
            data.put("entryNotice", resultSet.getString("entry_notice"));
            data.put("equipmentNote", resultSet.getString("equipment_note"));
            data.put("parkingNote", resultSet.getString("parking_note"));
            data.put("liabilityNote", resultSet.getString("liability_note"));
            data.put("createTime", DATETIME_FORMATTER.format(resultSet.getObject("gmt_created_time", LocalDateTime.class)));
            return data;
        }

        private List<String> readList(String value) {
            if (!StringUtils.hasText(value)) {
                return Collections.emptyList();
            }
            try {
                return objectMapper.readValue(value, STRING_LIST);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("场馆资料数组格式错误", exception);
            }
        }

        private String writeList(List<String> values) {
            try {
                return objectMapper.writeValueAsString(values == null ? Collections.emptyList() : values);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("场馆资料数组序列化失败", exception);
            }
        }

        private String decimalText(BigDecimal value) {
            return value == null ? null : value.toPlainString();
        }
    }
}
