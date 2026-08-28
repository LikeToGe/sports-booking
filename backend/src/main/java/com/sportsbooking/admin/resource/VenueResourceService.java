package com.sportsbooking.admin.resource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.resource.VenueResourceController.CreatePhysicalCourtRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VenueResourceService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> RESOURCE_CODES = new HashSet<String>(Arrays.asList("A", "B"));
    private final JdbcTemplate jdbcTemplate;

    public VenueResourceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> listCourts(long storeId, int page, int pageSize, String status) {
        validatePage(page, pageSize);
        validateStatusFilter(status);
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(storeId);
        String where = " WHERE p.store_id = ? AND p.is_deleted = 0 AND c.is_deleted = 0";
        if (StringUtils.hasText(status)) {
            where += " AND p.status = ?";
            parameters.add(status);
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM physical_court p JOIN sport_category c ON c.id = p.sport_category_id" + where,
            Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(
            "SELECT p.id, p.store_id, p.sport_category_id, c.code AS sport_category_code, c.name AS sport_category_name, p.name, p.sort_order, p.status, p.gmt_created_time "
                + "FROM physical_court p JOIN sport_category c ON c.id = p.sport_category_id" + where
                + " ORDER BY p.sort_order, p.id LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> courtData(court(resultSet)), parameters.toArray());
        return pageData(page, pageSize, total, records);
    }

    public Map<String, Object> listSellableUnits(long storeId, int page, int pageSize, String status) {
        validatePage(page, pageSize);
        validateStatusFilter(status);
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(storeId);
        String where = " WHERE p.store_id = ? AND u.is_deleted = 0 AND p.is_deleted = 0";
        if (StringUtils.hasText(status)) {
            where += " AND u.status = ?";
            parameters.add(status);
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sellable_unit u JOIN physical_court p ON p.id = u.physical_court_id" + where,
            Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(
            "SELECT u.id, u.physical_court_id, p.store_id, u.rental_type_id, u.name, u.sort_order, u.status, u.gmt_created_time, p.name AS court_name, c.name AS sport_category_name "
                + "FROM sellable_unit u JOIN physical_court p ON p.id = u.physical_court_id JOIN sport_category c ON c.id = p.sport_category_id" + where
                + " ORDER BY p.sort_order, p.id, u.sort_order, u.id LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> {
                SellableUnit unit = sellableUnit(resultSet);
                Map<String, Object> data = sellableUnitData(unit);
                data.put("physicalCourtId", String.valueOf(unit.physicalCourtId));
                data.put("physicalCourtName", resultSet.getString("court_name"));
                data.put("sportCategoryName", resultSet.getString("sport_category_name"));
                return data;
            }, parameters.toArray());
        return pageData(page, pageSize, total, records);
    }

    @Transactional
    public Map<String, Object> createCourt(long operatorId, long storeId, CreatePhysicalCourtRequest body) {
        String name = validateCourtInput(body);
        long sportCategoryId = body.getSportCategoryId();
        requiredActiveSportCategory(sportCategoryId);
        if (body.getFullRentalTypeId().equals(body.getHalfRentalTypeId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "全场和半场必须选择不同的出租类型");
        }
        requiredActiveRentalType(body.getFullRentalTypeId(), sportCategoryId);
        requiredActiveRentalType(body.getHalfRentalTypeId(), sportCategoryId);
        try {
            jdbcTemplate.update("INSERT INTO physical_court (store_id, sport_category_id, name, sort_order, status, create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                storeId, sportCategoryId, name, body.getSortOrder(), operatorId, operatorId);
            long courtId = lastInsertId();
            long resourceAId = createBaseResource(operatorId, courtId, "A", "A半场", 1);
            long resourceBId = createBaseResource(operatorId, courtId, "B", "B半场", 2);
            createSellableUnit(operatorId, courtId, body.getFullRentalTypeId(), name + "全场", 1, Arrays.asList(resourceAId, resourceBId));
            createSellableUnit(operatorId, courtId, body.getHalfRentalTypeId(), name + " A半场", 2, Arrays.asList(resourceAId));
            createSellableUnit(operatorId, courtId, body.getHalfRentalTypeId(), name + " B半场", 3, Arrays.asList(resourceBId));
            audit(operatorId, "PHYSICAL_COURT_CREATED", "PHYSICAL_COURT", courtId, storeId, "已创建 A/B 基础资源和全场、A/B 半场 v1");
            return getCourt(storeId, courtId);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "同一场馆的物理场地名称已存在");
        }
    }

    public Map<String, Object> getCourt(long storeId, long courtId) {
        Court court = requiredCourt(storeId, courtId);
        Map<String, Object> data = courtData(court);
        data.put("baseResources", listBaseResources(courtId));
        data.put("sellableUnits", listSellableUnits(courtId));
        return data;
    }

    @Transactional
    public void updateCourtStatus(long operatorId, long storeId, long courtId, String status) {
        validateResourceStatus(status);
        requiredCourt(storeId, courtId);
        jdbcTemplate.update("UPDATE physical_court SET status = ?, modify_operator_id = ? WHERE id = ? AND store_id = ? AND is_deleted = 0",
            status, operatorId, courtId, storeId);
        audit(operatorId, "PHYSICAL_COURT_STATUS_CHANGED", "PHYSICAL_COURT", courtId, storeId, status);
    }

    @Transactional
    public void updateSellableUnitStatus(long operatorId, long storeId, long sellableUnitId, String status) {
        validateResourceStatus(status);
        SellableUnit unit = requiredSellableUnit(storeId, sellableUnitId, false);
        jdbcTemplate.update("UPDATE sellable_unit SET status = ?, modify_operator_id = ? WHERE id = ? AND is_deleted = 0",
            status, operatorId, sellableUnitId);
        audit(operatorId, "SELLABLE_UNIT_STATUS_CHANGED", "SELLABLE_UNIT", sellableUnitId, unit.storeId, status);
    }

    public Map<String, Object> listVersions(long storeId, long sellableUnitId, int page, int pageSize) {
        validatePage(page, pageSize);
        requiredSellableUnit(storeId, sellableUnitId, false);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sellable_unit_version WHERE sellable_unit_id = ? AND is_deleted = 0",
            Long.class, sellableUnitId);
        List<Version> versions = jdbcTemplate.query(
            "SELECT id, sellable_unit_id, version_no, version_status, gmt_created_time FROM sellable_unit_version WHERE sellable_unit_id = ? AND is_deleted = 0 "
                + "ORDER BY version_no DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> version(resultSet), sellableUnitId, pageSize, (page - 1L) * pageSize);
        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        for (Version value : versions) {
            records.add(versionData(value));
        }
        return pageData(page, pageSize, total, records);
    }

    @Transactional
    public Map<String, Object> createVersion(long operatorId, long storeId, long sellableUnitId, List<Long> baseResourceIds) {
        SellableUnit unit = requiredSellableUnit(storeId, sellableUnitId, true);
        List<BaseResource> resources = requiredBaseResources(unit.physicalCourtId, baseResourceIds);
        Version current = requiredCurrentVersion(unit.id);
        if (sameResources(resources, listVersionResources(current.id))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "资源组合未变化，无需创建新版本");
        }
        int retired = jdbcTemplate.update("UPDATE sellable_unit_version SET version_status = 'RETIRED', modify_operator_id = ? "
                + "WHERE id = ? AND version_status = 'CURRENT' AND is_deleted = 0", operatorId, current.id);
        if (retired != 1) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "当前资源版本已变化，请刷新后重试");
        }
        long versionId = insertVersion(operatorId, unit.id, current.versionNo + 1, resources);
        audit(operatorId, "SELLABLE_UNIT_VERSION_CREATED", "SELLABLE_UNIT_VERSION", versionId, unit.storeId,
            "由 v" + current.versionNo + " 变更为 v" + (current.versionNo + 1) + "，资源=" + resourceCodes(resources));
        return versionData(requiredVersion(versionId));
    }

    private String validateCourtInput(CreatePhysicalCourtRequest body) {
        if (body == null || body.getSportCategoryId() == null || body.getFullRentalTypeId() == null || body.getHalfRentalTypeId() == null
            || body.getSortOrder() == null || body.getSortOrder() < 0 || !StringUtils.hasText(body.getName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "物理场地参数不正确");
        }
        String name = body.getName().trim();
        if (name.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "物理场地名称长度不能超过128");
        }
        return name;
    }

    private void requiredActiveSportCategory(long sportCategoryId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sport_category WHERE id = ? AND level = 2 AND status = 'ACTIVE' AND is_deleted = 0",
            Integer.class, sportCategoryId);
        if (count == null || count != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "必须选择启用的二级运动项目");
        }
    }

    private void requiredActiveRentalType(long rentalTypeId, long sportCategoryId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM rental_type r JOIN sport_category c ON c.id = r.sport_category_id "
                + "WHERE r.id = ? AND r.sport_category_id = ? AND r.status = 'ACTIVE' AND r.is_deleted = 0 "
                + "AND c.level = 2 AND c.status = 'ACTIVE' AND c.is_deleted = 0", Integer.class, rentalTypeId, sportCategoryId);
        if (count == null || count != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "出租类型必须属于该启用运动项目");
        }
    }

    private long createBaseResource(long operatorId, long courtId, String code, String name, int sortOrder) {
        jdbcTemplate.update("INSERT INTO base_resource (physical_court_id, code, name, sort_order, create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, ?, ?)",
            courtId, code, name, sortOrder, operatorId, operatorId);
        return lastInsertId();
    }

    private void createSellableUnit(long operatorId, long courtId, long rentalTypeId, String name, int sortOrder, List<Long> resourceIds) {
        jdbcTemplate.update("INSERT INTO sellable_unit (physical_court_id, rental_type_id, name, sort_order, status, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)", courtId, rentalTypeId, name, sortOrder, operatorId, operatorId);
        long unitId = lastInsertId();
        List<BaseResource> resources = requiredBaseResources(courtId, resourceIds);
        insertVersion(operatorId, unitId, 1, resources);
    }

    private long insertVersion(long operatorId, long unitId, int versionNo, List<BaseResource> resources) {
        jdbcTemplate.update("INSERT INTO sellable_unit_version (sellable_unit_id, version_no, version_status, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, 'CURRENT', ?, ?)", unitId, versionNo, operatorId, operatorId);
        long versionId = lastInsertId();
        for (BaseResource resource : resources) {
            jdbcTemplate.update("INSERT INTO sellable_unit_version_resource (sellable_unit_version_id, base_resource_id, sort_order, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, ?, ?, ?)", versionId, resource.id, resource.sortOrder, operatorId, operatorId);
        }
        return versionId;
    }

    private Court requiredCourt(long storeId, long courtId) {
        List<Court> courts = jdbcTemplate.query(
            "SELECT p.id, p.store_id, p.sport_category_id, c.code AS sport_category_code, c.name AS sport_category_name, p.name, p.sort_order, p.status, p.gmt_created_time "
                + "FROM physical_court p JOIN sport_category c ON c.id = p.sport_category_id "
                + "WHERE p.id = ? AND p.store_id = ? AND p.is_deleted = 0 AND c.is_deleted = 0",
            (resultSet, rowNum) -> court(resultSet), courtId, storeId);
        if (courts.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "物理场地不存在或不属于当前场馆");
        }
        return courts.get(0);
    }

    private SellableUnit requiredSellableUnit(long storeId, long sellableUnitId, boolean lock) {
        String sql = "SELECT u.id, u.physical_court_id, p.store_id, u.rental_type_id, u.name, u.sort_order, u.status, u.gmt_created_time "
            + "FROM sellable_unit u JOIN physical_court p ON p.id = u.physical_court_id "
            + "WHERE u.id = ? AND p.store_id = ? AND u.is_deleted = 0 AND p.is_deleted = 0" + (lock ? " FOR UPDATE" : "");
        List<SellableUnit> units = jdbcTemplate.query(sql, (resultSet, rowNum) -> sellableUnit(resultSet), sellableUnitId, storeId);
        if (units.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "可售单元不存在或不属于当前场馆");
        }
        return units.get(0);
    }

    private Version requiredCurrentVersion(long unitId) {
        List<Version> versions = jdbcTemplate.query(
            "SELECT id, sellable_unit_id, version_no, version_status, gmt_created_time FROM sellable_unit_version "
                + "WHERE sellable_unit_id = ? AND version_status = 'CURRENT' AND is_deleted = 0",
            (resultSet, rowNum) -> version(resultSet), unitId);
        if (versions.isEmpty()) {
            throw new IllegalStateException("可售单元缺少当前资源版本");
        }
        return versions.get(0);
    }

    private Version requiredVersion(long versionId) {
        List<Version> versions = jdbcTemplate.query(
            "SELECT id, sellable_unit_id, version_no, version_status, gmt_created_time FROM sellable_unit_version WHERE id = ? AND is_deleted = 0",
            (resultSet, rowNum) -> version(resultSet), versionId);
        if (versions.isEmpty()) {
            throw new IllegalStateException("可售单元版本不存在");
        }
        return versions.get(0);
    }

    private List<BaseResource> requiredBaseResources(long courtId, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "资源组合不能为空");
        }
        Set<Long> uniqueIds = new HashSet<Long>();
        for (Long resourceId : resourceIds) {
            if (resourceId == null || resourceId <= 0 || !uniqueIds.add(resourceId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "基础资源参数不正确");
            }
        }
        StringBuilder placeholders = new StringBuilder();
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(courtId);
        for (Long resourceId : uniqueIds) {
            if (placeholders.length() > 0) {
                placeholders.append(", ");
            }
            placeholders.append("?");
            parameters.add(resourceId);
        }
        List<BaseResource> resources = jdbcTemplate.query(
            "SELECT id, code, name, sort_order FROM base_resource WHERE physical_court_id = ? AND is_deleted = 0 AND id IN (" + placeholders + ") "
                + "ORDER BY sort_order, id",
            (resultSet, rowNum) -> baseResource(resultSet), parameters.toArray());
        if (resources.size() != uniqueIds.size() || resources.size() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "基础资源必须属于当前物理场地");
        }
        for (BaseResource resource : resources) {
            if (!RESOURCE_CODES.contains(resource.code)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "仅支持 A/B 基础资源组合");
            }
        }
        return resources;
    }

    private List<Map<String, Object>> listBaseResources(long courtId) {
        List<BaseResource> resources = jdbcTemplate.query(
            "SELECT id, code, name, sort_order FROM base_resource WHERE physical_court_id = ? AND is_deleted = 0 ORDER BY sort_order, id",
            (resultSet, rowNum) -> baseResource(resultSet), courtId);
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (BaseResource resource : resources) {
            data.add(baseResourceData(resource));
        }
        return data;
    }

    private List<Map<String, Object>> listSellableUnits(long courtId) {
        List<SellableUnit> units = jdbcTemplate.query(
            "SELECT u.id, u.physical_court_id, p.store_id, u.rental_type_id, u.name, u.sort_order, u.status, u.gmt_created_time "
                + "FROM sellable_unit u JOIN physical_court p ON p.id = u.physical_court_id "
                + "WHERE u.physical_court_id = ? AND u.is_deleted = 0 AND p.is_deleted = 0 ORDER BY u.sort_order, u.id",
            (resultSet, rowNum) -> sellableUnit(resultSet), courtId);
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (SellableUnit unit : units) {
            data.add(sellableUnitData(unit));
        }
        return data;
    }

    private Map<String, Object> sellableUnitData(SellableUnit unit) {
        List<Map<String, Object>> typeData = jdbcTemplate.query(
            "SELECT name FROM rental_type WHERE id = ? AND is_deleted = 0", (resultSet, rowNum) -> {
                Map<String, Object> value = new LinkedHashMap<String, Object>();
                value.put("name", resultSet.getString("name"));
                return value;
            }, unit.rentalTypeId);
        if (typeData.isEmpty()) {
            throw new IllegalStateException("可售单元出租类型不存在");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(unit.id));
        data.put("rentalTypeId", String.valueOf(unit.rentalTypeId));
        data.put("rentalTypeName", typeData.get(0).get("name"));
        data.put("name", unit.name);
        data.put("sortOrder", unit.sortOrder);
        data.put("status", unit.status);
        data.put("currentVersion", versionData(requiredCurrentVersion(unit.id)));
        data.put("createTime", DATETIME_FORMATTER.format(unit.createdTime));
        return data;
    }

    private Map<String, Object> versionData(Version version) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(version.id));
        data.put("versionNo", version.versionNo);
        data.put("versionStatus", version.versionStatus);
        data.put("baseResources", baseResourceDataList(listVersionResources(version.id)));
        data.put("createTime", DATETIME_FORMATTER.format(version.createdTime));
        return data;
    }

    private List<BaseResource> listVersionResources(long versionId) {
        return jdbcTemplate.query(
            "SELECT r.id, r.code, r.name, r.sort_order FROM sellable_unit_version_resource vr "
                + "JOIN base_resource r ON r.id = vr.base_resource_id "
                + "WHERE vr.sellable_unit_version_id = ? AND vr.is_deleted = 0 AND r.is_deleted = 0 ORDER BY vr.sort_order, r.id",
            (resultSet, rowNum) -> baseResource(resultSet), versionId);
    }

    private List<Map<String, Object>> baseResourceDataList(List<BaseResource> resources) {
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (BaseResource resource : resources) {
            data.add(baseResourceData(resource));
        }
        return data;
    }

    private boolean sameResources(List<BaseResource> left, List<BaseResource> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index).id != right.get(index).id) {
                return false;
            }
        }
        return true;
    }

    private String resourceCodes(List<BaseResource> resources) {
        StringBuilder codes = new StringBuilder();
        for (BaseResource resource : resources) {
            if (codes.length() > 0) {
                codes.append(',');
            }
            codes.append(resource.code);
        }
        return codes.toString();
    }

    private Court court(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new Court(resultSet.getLong("id"), resultSet.getLong("store_id"), resultSet.getLong("sport_category_id"),
            resultSet.getString("sport_category_code"), resultSet.getString("sport_category_name"), resultSet.getString("name"),
            resultSet.getInt("sort_order"), resultSet.getString("status"), resultSet.getObject("gmt_created_time", LocalDateTime.class));
    }

    private SellableUnit sellableUnit(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new SellableUnit(resultSet.getLong("id"), resultSet.getLong("physical_court_id"), resultSet.getLong("store_id"),
            resultSet.getLong("rental_type_id"), resultSet.getString("name"), resultSet.getInt("sort_order"), resultSet.getString("status"),
            resultSet.getObject("gmt_created_time", LocalDateTime.class));
    }

    private Version version(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new Version(resultSet.getLong("id"), resultSet.getLong("sellable_unit_id"), resultSet.getInt("version_no"),
            resultSet.getString("version_status"), resultSet.getObject("gmt_created_time", LocalDateTime.class));
    }

    private BaseResource baseResource(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new BaseResource(resultSet.getLong("id"), resultSet.getString("code"), resultSet.getString("name"), resultSet.getInt("sort_order"));
    }

    private Map<String, Object> courtData(Court court) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(court.id));
        data.put("sportCategoryId", String.valueOf(court.sportCategoryId));
        data.put("sportCategoryCode", court.sportCategoryCode);
        data.put("sportCategoryName", court.sportCategoryName);
        data.put("name", court.name);
        data.put("sortOrder", court.sortOrder);
        data.put("status", court.status);
        data.put("createTime", DATETIME_FORMATTER.format(court.createdTime));
        return data;
    }

    private Map<String, Object> baseResourceData(BaseResource resource) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(resource.id));
        data.put("code", resource.code);
        data.put("name", resource.name);
        data.put("sortOrder", resource.sortOrder);
        return data;
    }

    private Map<String, Object> pageData(int page, int pageSize, long total, List<Map<String, Object>> records) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("current", String.valueOf(page));
        data.put("size", String.valueOf(pageSize));
        data.put("total", String.valueOf(total));
        data.put("records", records);
        return data;
    }

    private long lastInsertId() {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        }
    }

    private void validateStatusFilter(String status) {
        if (StringUtils.hasText(status) && !"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "状态参数不正确");
        }
    }

    private void validateResourceStatus(String status) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "状态参数不正确");
        }
    }

    private void audit(long operatorId, String action, String targetType, long targetId, long storeId, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', ?, ?, ?)", operatorId, action, targetType, targetId, storeId, reason, operatorId, operatorId);
    }

    private static final class Court {
        private final long id;
        private final long storeId;
        private final long sportCategoryId;
        private final String sportCategoryCode;
        private final String sportCategoryName;
        private final String name;
        private final int sortOrder;
        private final String status;
        private final LocalDateTime createdTime;

        private Court(long id, long storeId, long sportCategoryId, String sportCategoryCode, String sportCategoryName, String name,
                      int sortOrder, String status, LocalDateTime createdTime) {
            this.id = id;
            this.storeId = storeId;
            this.sportCategoryId = sportCategoryId;
            this.sportCategoryCode = sportCategoryCode;
            this.sportCategoryName = sportCategoryName;
            this.name = name;
            this.sortOrder = sortOrder;
            this.status = status;
            this.createdTime = createdTime;
        }
    }

    private static final class SellableUnit {
        private final long id;
        private final long physicalCourtId;
        private final long storeId;
        private final long rentalTypeId;
        private final String name;
        private final int sortOrder;
        private final String status;
        private final LocalDateTime createdTime;

        private SellableUnit(long id, long physicalCourtId, long storeId, long rentalTypeId, String name, int sortOrder,
                             String status, LocalDateTime createdTime) {
            this.id = id;
            this.physicalCourtId = physicalCourtId;
            this.storeId = storeId;
            this.rentalTypeId = rentalTypeId;
            this.name = name;
            this.sortOrder = sortOrder;
            this.status = status;
            this.createdTime = createdTime;
        }
    }

    private static final class Version {
        private final long id;
        private final long sellableUnitId;
        private final int versionNo;
        private final String versionStatus;
        private final LocalDateTime createdTime;

        private Version(long id, long sellableUnitId, int versionNo, String versionStatus, LocalDateTime createdTime) {
            this.id = id;
            this.sellableUnitId = sellableUnitId;
            this.versionNo = versionNo;
            this.versionStatus = versionStatus;
            this.createdTime = createdTime;
        }
    }

    private static final class BaseResource {
        private final long id;
        private final String code;
        private final String name;
        private final int sortOrder;

        private BaseResource(long id, String code, String name, int sortOrder) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.sortOrder = sortOrder;
        }
    }
}
