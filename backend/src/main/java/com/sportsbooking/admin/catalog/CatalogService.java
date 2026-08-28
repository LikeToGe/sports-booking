package com.sportsbooking.admin.catalog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sportsbooking.admin.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CatalogService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;

    public CatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> listSportCategories(int page, int pageSize, String status) {
        validatePage(page, pageSize);
        validateStatusFilter(status);
        List<Object> parameters = new ArrayList<Object>();
        String where = " WHERE is_deleted = 0";
        if (StringUtils.hasText(status)) {
            where += " AND status = ?";
            parameters.add(status);
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sport_category" + where, Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(
            "SELECT id, parent_id, code, name, level, icon, sort_order, status, gmt_created_time FROM sport_category" + where
                + " ORDER BY level, parent_id, sort_order, id LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> sportCategoryData(resultSet.getLong("id"), nullableId(resultSet, "parent_id"),
                resultSet.getString("code"), resultSet.getString("name"), resultSet.getInt("level"), resultSet.getString("icon"),
                resultSet.getInt("sort_order"), resultSet.getString("status"), resultSet.getObject("gmt_created_time", LocalDateTime.class)),
            parameters.toArray());
        return pageData(page, pageSize, total, records);
    }

    public List<Map<String, Object>> sportCategoryTree() {
        List<Map<String, Object>> categories = jdbcTemplate.query(
            "SELECT id, parent_id, code, name, level, icon, sort_order, status, gmt_created_time FROM sport_category WHERE is_deleted = 0"
                + " ORDER BY level, parent_id, sort_order, id",
            (resultSet, rowNum) -> sportCategoryData(resultSet.getLong("id"), nullableId(resultSet, "parent_id"),
                resultSet.getString("code"), resultSet.getString("name"), resultSet.getInt("level"), resultSet.getString("icon"),
                resultSet.getInt("sort_order"), resultSet.getString("status"), resultSet.getObject("gmt_created_time", LocalDateTime.class)));
        Map<String, List<Map<String, Object>>> children = new LinkedHashMap<String, List<Map<String, Object>>>();
        List<Map<String, Object>> roots = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> category : categories) {
            if (category.get("parentId") == null) {
                roots.add(category);
            } else {
                children.computeIfAbsent(String.valueOf(category.get("parentId")), key -> new ArrayList<Map<String, Object>>()).add(category);
            }
        }
        for (Map<String, Object> root : roots) {
            root.put("children", children.getOrDefault(root.get("id"), List.of()));
        }
        return roots;
    }

    @Transactional
    public Map<String, Object> createSportCategory(long operatorId, Long parentId, String code, String name, String icon, Integer sortOrder) {
        int level = validateCategoryInput(parentId, null, code, name, sortOrder);
        try {
            jdbcTemplate.update("INSERT INTO sport_category (parent_id, code, name, level, icon, sort_order, status, create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                parentId, code, name, level, icon, sortOrder, operatorId, operatorId);
            long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            audit(operatorId, "SPORT_CATEGORY_CREATED", "SPORT_CATEGORY", id, "SUCCEEDED", null);
            return getSportCategory(id);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "运动分类编码已存在");
        }
    }

    @Transactional
    public Map<String, Object> updateSportCategory(long operatorId, long categoryId, Long parentId, String code, String name, String icon, Integer sortOrder) {
        SportCategory current = requiredSportCategory(categoryId);
        if (!sameId(current.parentId, parentId) && hasSportCategoryChildren(categoryId)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "有子分类的运动分类不能调整层级");
        }
        int level = validateCategoryInput(parentId, categoryId, code, name, sortOrder);
        try {
            jdbcTemplate.update("UPDATE sport_category SET parent_id = ?, code = ?, name = ?, level = ?, icon = ?, sort_order = ?, modify_operator_id = ? WHERE id = ? AND is_deleted = 0",
                parentId, code, name, level, icon, sortOrder, operatorId, categoryId);
            audit(operatorId, "SPORT_CATEGORY_UPDATED", "SPORT_CATEGORY", categoryId, "SUCCEEDED", null);
            return getSportCategory(categoryId);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "运动分类编码已存在");
        }
    }

    @Transactional
    public void updateSportCategoryStatus(long operatorId, long categoryId, String status) {
        validateCatalogStatus(status);
        requiredSportCategory(categoryId);
        jdbcTemplate.update("UPDATE sport_category SET status = ?, modify_operator_id = ? WHERE id = ? AND is_deleted = 0", status, operatorId, categoryId);
        audit(operatorId, "SPORT_CATEGORY_STATUS_CHANGED", "SPORT_CATEGORY", categoryId, "SUCCEEDED", status);
    }

    public Map<String, Object> listRentalTypes(int page, int pageSize, Long sportCategoryId, String status) {
        validatePage(page, pageSize);
        validateStatusFilter(status);
        List<Object> parameters = new ArrayList<Object>();
        String where = " WHERE r.is_deleted = 0 AND c.is_deleted = 0";
        if (sportCategoryId != null) {
            where += " AND r.sport_category_id = ?";
            parameters.add(sportCategoryId);
        }
        if (StringUtils.hasText(status)) {
            where += " AND r.status = ?";
            parameters.add(status);
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM rental_type r JOIN sport_category c ON c.id = r.sport_category_id" + where,
            Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(
            "SELECT r.id, r.sport_category_id, c.name AS sport_category_name, r.name, r.status, r.gmt_created_time FROM rental_type r JOIN sport_category c ON c.id = r.sport_category_id"
                + where + " ORDER BY r.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> rentalTypeData(resultSet.getLong("id"), resultSet.getLong("sport_category_id"),
                resultSet.getString("sport_category_name"), resultSet.getString("name"), resultSet.getString("status"),
                resultSet.getObject("gmt_created_time", LocalDateTime.class)), parameters.toArray());
        return pageData(page, pageSize, total, records);
    }

    @Transactional
    public Map<String, Object> createRentalType(long operatorId, Long sportCategoryId, String name) {
        validateRentalTypeInput(sportCategoryId, name);
        try {
            jdbcTemplate.update("INSERT INTO rental_type (sport_category_id, name, status, create_operator_id, modify_operator_id) VALUES (?, ?, 'ACTIVE', ?, ?)",
                sportCategoryId, name, operatorId, operatorId);
            long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            audit(operatorId, "RENTAL_TYPE_CREATED", "RENTAL_TYPE", id, "SUCCEEDED", null);
            return getRentalType(id);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "该运动项目下的出租类型名称已存在");
        }
    }

    @Transactional
    public Map<String, Object> updateRentalType(long operatorId, long rentalTypeId, Long sportCategoryId, String name) {
        requiredRentalType(rentalTypeId);
        validateRentalTypeInput(sportCategoryId, name);
        try {
            jdbcTemplate.update("UPDATE rental_type SET sport_category_id = ?, name = ?, modify_operator_id = ? WHERE id = ? AND is_deleted = 0",
                sportCategoryId, name, operatorId, rentalTypeId);
            audit(operatorId, "RENTAL_TYPE_UPDATED", "RENTAL_TYPE", rentalTypeId, "SUCCEEDED", null);
            return getRentalType(rentalTypeId);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "该运动项目下的出租类型名称已存在");
        }
    }

    @Transactional
    public void updateRentalTypeStatus(long operatorId, long rentalTypeId, String status) {
        validateCatalogStatus(status);
        requiredRentalType(rentalTypeId);
        jdbcTemplate.update("UPDATE rental_type SET status = ?, modify_operator_id = ? WHERE id = ? AND is_deleted = 0", status, operatorId, rentalTypeId);
        audit(operatorId, "RENTAL_TYPE_STATUS_CHANGED", "RENTAL_TYPE", rentalTypeId, "SUCCEEDED", status);
    }

    private int validateCategoryInput(Long parentId, Long categoryId, String code, String name, Integer sortOrder) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name) || sortOrder == null || sortOrder < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "运动分类参数不正确");
        }
        if (parentId == null) {
            return 1;
        }
        if (parentId.equals(categoryId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "运动分类不能选择自身为父级");
        }
        List<Integer> parentLevels = jdbcTemplate.query("SELECT level FROM sport_category WHERE id = ? AND is_deleted = 0",
            (resultSet, rowNum) -> resultSet.getInt("level"), parentId);
        if (parentLevels.isEmpty() || parentLevels.get(0) != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "父级必须是一级运动分类");
        }
        return 2;
    }

    private void validateRentalTypeInput(Long sportCategoryId, String name) {
        if (sportCategoryId == null || !StringUtils.hasText(name)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "出租类型参数不正确");
        }
        List<Integer> levels = jdbcTemplate.query("SELECT level FROM sport_category WHERE id = ? AND is_deleted = 0",
            (resultSet, rowNum) -> resultSet.getInt("level"), sportCategoryId);
        if (levels.isEmpty() || levels.get(0) != 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "出租类型必须关联具体运动项目");
        }
    }

    private Map<String, Object> getSportCategory(long categoryId) {
        return requiredSportCategory(categoryId).toData();
    }

    private SportCategory requiredSportCategory(long categoryId) {
        List<SportCategory> categories = jdbcTemplate.query(
            "SELECT id, parent_id, code, name, level, icon, sort_order, status, gmt_created_time FROM sport_category WHERE id = ? AND is_deleted = 0",
            (resultSet, rowNum) -> new SportCategory(resultSet.getLong("id"), nullableId(resultSet, "parent_id"), resultSet.getString("code"),
                resultSet.getString("name"), resultSet.getInt("level"), resultSet.getString("icon"), resultSet.getInt("sort_order"),
                resultSet.getString("status"), resultSet.getObject("gmt_created_time", LocalDateTime.class)), categoryId);
        if (categories.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "运动分类不存在");
        }
        return categories.get(0);
    }

    private Map<String, Object> getRentalType(long rentalTypeId) {
        List<Map<String, Object>> rentalTypes = jdbcTemplate.query(
            "SELECT r.id, r.sport_category_id, c.name AS sport_category_name, r.name, r.status, r.gmt_created_time FROM rental_type r JOIN sport_category c ON c.id = r.sport_category_id WHERE r.id = ? AND r.is_deleted = 0 AND c.is_deleted = 0",
            (resultSet, rowNum) -> rentalTypeData(resultSet.getLong("id"), resultSet.getLong("sport_category_id"), resultSet.getString("sport_category_name"),
                resultSet.getString("name"), resultSet.getString("status"), resultSet.getObject("gmt_created_time", LocalDateTime.class)), rentalTypeId);
        if (rentalTypes.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "出租类型不存在");
        }
        return rentalTypes.get(0);
    }

    private void requiredRentalType(long rentalTypeId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM rental_type WHERE id = ? AND is_deleted = 0", Integer.class, rentalTypeId);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "出租类型不存在");
        }
    }

    private boolean hasSportCategoryChildren(long categoryId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sport_category WHERE parent_id = ? AND is_deleted = 0", Integer.class, categoryId);
        return count != null && count > 0;
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

    private void validateCatalogStatus(String status) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "状态参数不正确");
        }
    }

    private void audit(long operatorId, String action, String targetType, long targetId, String result, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, result, reason, create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            operatorId, action, targetType, targetId, result, reason, operatorId, operatorId);
    }

    private Map<String, Object> pageData(int page, int pageSize, long total, List<Map<String, Object>> records) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("current", String.valueOf(page));
        result.put("size", String.valueOf(pageSize));
        result.put("total", String.valueOf(total));
        result.put("records", records);
        return result;
    }

    private Map<String, Object> sportCategoryData(long id, Long parentId, String code, String name, int level, String icon, int sortOrder,
                                                   String status, LocalDateTime createdTime) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(id));
        data.put("parentId", parentId == null ? null : String.valueOf(parentId));
        data.put("code", code);
        data.put("name", name);
        data.put("level", level);
        data.put("icon", icon);
        data.put("sortOrder", sortOrder);
        data.put("status", status);
        data.put("createTime", DATETIME_FORMATTER.format(createdTime));
        return data;
    }

    private Map<String, Object> rentalTypeData(long id, long sportCategoryId, String sportCategoryName, String name, String status,
                                                LocalDateTime createdTime) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(id));
        data.put("sportCategoryId", String.valueOf(sportCategoryId));
        data.put("sportCategoryName", sportCategoryName);
        data.put("name", name);
        data.put("status", status);
        data.put("createTime", DATETIME_FORMATTER.format(createdTime));
        return data;
    }

    private Long nullableId(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private boolean sameId(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private final class SportCategory {
        private final long id;
        private final Long parentId;
        private final String code;
        private final String name;
        private final int level;
        private final String icon;
        private final int sortOrder;
        private final String status;
        private final LocalDateTime createdTime;

        private SportCategory(long id, Long parentId, String code, String name, int level, String icon, int sortOrder, String status,
                              LocalDateTime createdTime) {
            this.id = id;
            this.parentId = parentId;
            this.code = code;
            this.name = name;
            this.level = level;
            this.icon = icon;
            this.sortOrder = sortOrder;
            this.status = status;
            this.createdTime = createdTime;
        }

        private Map<String, Object> toData() {
            return sportCategoryData(id, parentId, code, name, level, icon, sortOrder, status, createdTime);
        }
    }
}
