package com.sportsbooking.admin.mini;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sportsbooking.admin.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MiniVenueService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbcTemplate;

    public MiniVenueService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(LocalDate date, Long sportCategoryId, String keyword, int page, int pageSize) {
        validatePage(page, pageSize);
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(date);
        String where = publicStoreWhere();
        if (sportCategoryId != null) {
            if (sportCategoryId <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "运动项目参数不正确");
            }
            where += " AND EXISTS (SELECT 1 FROM inventory_slot si JOIN sellable_unit_version sv ON sv.id = si.sellable_unit_version_id "
                + "JOIN sellable_unit su ON su.id = sv.sellable_unit_id JOIN physical_court pc ON pc.id = su.physical_court_id "
                + "WHERE si.store_id = s.id AND pc.sport_category_id = ? AND si.inventory_date = ? AND si.is_deleted = 0)";
            parameters.add(sportCategoryId);
            parameters.add(date);
        }
        if (StringUtils.hasText(keyword)) {
            if (keyword.length() > 64) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "场馆名称参数过长");
            }
            where += " AND (s.name LIKE ? OR s.address LIKE ?)";
            parameters.add("%" + keyword.trim() + "%");
            parameters.add("%" + keyword.trim() + "%");
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM store s JOIN organization o ON o.id = s.organization_id "
                + "JOIN transaction_gate og ON og.scope_type = 'ORGANIZATION' AND og.scope_id = o.id AND og.is_deleted = 0 "
                + "JOIN transaction_gate sg ON sg.scope_type = 'STORE' AND sg.scope_id = s.id AND sg.is_deleted = 0" + where,
            Long.class, parameters.toArray());
        List<Object> recordParameters = new ArrayList<Object>();
        recordParameters.add(date);
        recordParameters.addAll(parameters);
        recordParameters.add(pageSize);
        recordParameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query("SELECT s.id, s.name, s.address, s.contact_phone, s.introduction, s.image_urls, s.facilities, "
                + "(SELECT MIN(mi.price_fen) FROM inventory_slot mi WHERE mi.store_id = s.id AND mi.inventory_date = ? AND mi.is_deleted = 0) AS starting_price_fen, "
                + "(SELECT GROUP_CONCAT(DISTINCT mc.name ORDER BY mc.name SEPARATOR '、') FROM physical_court mp JOIN sport_category mc ON mc.id = mp.sport_category_id WHERE mp.store_id = s.id AND mp.status = 'ACTIVE' AND mp.is_deleted = 0 AND mc.is_deleted = 0) AS sport_category_names "
                + "FROM store s JOIN organization o ON o.id = s.organization_id JOIN transaction_gate og ON og.scope_type = 'ORGANIZATION' AND og.scope_id = o.id AND og.is_deleted = 0 "
                + "JOIN transaction_gate sg ON sg.scope_type = 'STORE' AND sg.scope_id = s.id AND sg.is_deleted = 0" + where
                + " ORDER BY s.id DESC LIMIT ? OFFSET ?", (resultSet, rowNum) -> venueData(resultSet), recordParameters.toArray());
        return pageData(page, pageSize, total == null ? 0 : total, records);
    }

    public List<Map<String, Object>> listSportCategories(LocalDate date) {
        return jdbcTemplate.query("SELECT DISTINCT c.id, c.name FROM sport_category c JOIN physical_court p ON p.sport_category_id = c.id "
                + "JOIN sellable_unit u ON u.physical_court_id = p.id JOIN sellable_unit_version v ON v.sellable_unit_id = u.id "
                + "JOIN inventory_slot i ON i.sellable_unit_version_id = v.id WHERE i.inventory_date = ? AND c.status = 'ACTIVE' AND p.status = 'ACTIVE' "
                + "AND u.status = 'ACTIVE' AND c.is_deleted = 0 AND p.is_deleted = 0 AND u.is_deleted = 0 AND v.is_deleted = 0 AND i.is_deleted = 0 ORDER BY c.name",
            (resultSet, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", String.valueOf(resultSet.getLong("id")));
                item.put("name", resultSet.getString("name"));
                return item;
            }, date);
    }

    public Map<String, Object> detail(long storeId, LocalDate date) {
        List<Map<String, Object>> venues = jdbcTemplate.query("SELECT s.id, s.name, s.address, s.contact_phone, s.introduction, s.image_urls, s.facilities, NULL AS starting_price_fen, NULL AS sport_category_names, "
                + "s.traffic_note, s.entry_notice, s.equipment_note, s.parking_note FROM store s JOIN organization o ON o.id = s.organization_id "
                + "JOIN transaction_gate og ON og.scope_type = 'ORGANIZATION' AND og.scope_id = o.id AND og.is_deleted = 0 "
                + "JOIN transaction_gate sg ON sg.scope_type = 'STORE' AND sg.scope_id = s.id AND sg.is_deleted = 0" + publicStoreWhere()
                + " AND s.id = ?", (resultSet, rowNum) -> venueDetailData(resultSet), date, storeId);
        if (venues.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "场馆不存在或当前不可预约");
        }
        Map<String, Object> data = venues.get(0);
        data.put("date", DATE_FORMATTER.format(date));
        data.put("slots", availableSlots(storeId, date));
        return data;
    }

    private String publicStoreWhere() {
        return " WHERE s.status = 'ACTIVE' AND s.is_deleted = 0 AND o.status = 'ACTIVE' AND o.is_deleted = 0 "
            + "AND og.gate_status = 'OPEN' AND sg.gate_status = 'OPEN' AND EXISTS (SELECT 1 FROM inventory_slot i "
            + "WHERE i.store_id = s.id AND i.inventory_date = ? AND i.is_deleted = 0 "
            + "AND EXISTS (SELECT 1 FROM schedule_version v WHERE v.id = i.schedule_version_id AND v.version_status = 'PUBLISHED' "
            + "AND v.effective_from <= i.inventory_date AND (v.effective_to IS NULL OR v.effective_to >= i.inventory_date) AND v.is_deleted = 0) "
            + "AND NOT EXISTS (SELECT 1 FROM inventory_slot_atom m JOIN inventory_atom a ON a.id = m.inventory_atom_id "
            + "WHERE m.inventory_slot_id = i.id AND m.is_deleted = 0 AND a.is_deleted = 0 "
            + "AND (a.inventory_status <> 'AVAILABLE' OR a.manual_block_id IS NOT NULL OR a.occupying_order_id IS NOT NULL)))";
    }

    private List<Map<String, Object>> availableSlots(long storeId, LocalDate date) {
        return jdbcTemplate.query("SELECT i.id, i.schedule_version_id, i.sellable_unit_version_id, i.start_time, i.end_time, i.price_fen, "
                + "u.name AS sellable_unit_name, v.version_no AS sellable_unit_version_no FROM inventory_slot i "
                + "JOIN sellable_unit_version v ON v.id = i.sellable_unit_version_id JOIN sellable_unit u ON u.id = v.sellable_unit_id "
                + "WHERE i.store_id = ? AND i.inventory_date = ? AND i.is_deleted = 0 AND EXISTS (SELECT 1 FROM schedule_version sv "
                + "WHERE sv.id = i.schedule_version_id AND sv.version_status = 'PUBLISHED' AND sv.effective_from <= i.inventory_date "
                + "AND (sv.effective_to IS NULL OR sv.effective_to >= i.inventory_date) AND sv.is_deleted = 0) AND NOT EXISTS "
                + "(SELECT 1 FROM inventory_slot_atom m JOIN inventory_atom a ON a.id = m.inventory_atom_id WHERE m.inventory_slot_id = i.id "
                + "AND m.is_deleted = 0 AND a.is_deleted = 0 AND (a.inventory_status <> 'AVAILABLE' OR a.manual_block_id IS NOT NULL "
                + "OR a.occupying_order_id IS NOT NULL)) ORDER BY i.start_time, i.id", (resultSet, rowNum) -> {
                Map<String, Object> slot = new LinkedHashMap<String, Object>();
                slot.put("id", String.valueOf(resultSet.getLong("id")));
                slot.put("scheduleVersionId", String.valueOf(resultSet.getLong("schedule_version_id")));
                slot.put("sellableUnitVersionId", String.valueOf(resultSet.getLong("sellable_unit_version_id")));
                slot.put("sellableUnitName", resultSet.getString("sellable_unit_name"));
                slot.put("sellableUnitVersionNo", resultSet.getInt("sellable_unit_version_no"));
                slot.put("startTime", TIME_FORMATTER.format(resultSet.getObject("start_time", java.time.LocalTime.class)));
                slot.put("endTime", TIME_FORMATTER.format(resultSet.getObject("end_time", java.time.LocalTime.class)));
                slot.put("priceFen", resultSet.getInt("price_fen"));
                return slot;
            }, storeId, date);
    }

    private Map<String, Object> venueData(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("storeId", String.valueOf(resultSet.getLong("id")));
        data.put("storeName", resultSet.getString("name"));
        data.put("address", resultSet.getString("address"));
        data.put("contactPhone", resultSet.getString("contact_phone"));
        data.put("introduction", resultSet.getString("introduction"));
        data.put("imageUrls", jsonList(resultSet.getString("image_urls")));
        data.put("facilities", jsonList(resultSet.getString("facilities")));
        data.put("startingPriceFen", resultSet.getObject("starting_price_fen") == null ? null : resultSet.getInt("starting_price_fen"));
        data.put("sportCategoryNames", resultSet.getString("sport_category_names"));
        return data;
    }

    private Map<String, Object> venueDetailData(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Map<String, Object> data = venueData(resultSet);
        data.put("trafficNote", resultSet.getString("traffic_note"));
        data.put("entryNotice", resultSet.getString("entry_notice"));
        data.put("equipmentNote", resultSet.getString("equipment_note"));
        data.put("parkingNote", resultSet.getString("parking_note"));
        return data;
    }

    private List<String> jsonList(String value) {
        if (!StringUtils.hasText(value)) {
            return java.util.Collections.emptyList();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value,
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("场馆展示列表格式错误", exception);
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        }
    }

    private Map<String, Object> pageData(int page, int pageSize, long total, List<Map<String, Object>> records) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("current", String.valueOf(page));
        data.put("size", String.valueOf(pageSize));
        data.put("total", String.valueOf(total));
        data.put("records", records);
        return data;
    }

    public LocalDate parseOrToday(String date) {
        if (!StringUtils.hasText(date)) {
            return LocalDate.now(BUSINESS_ZONE);
        }
        try {
            return LocalDate.parse(date, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "日期格式不正确");
        }
    }
}
