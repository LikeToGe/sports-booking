package com.sportsbooking.admin.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.schedule.VenueScheduleController.CreateManualBlockRequest;
import com.sportsbooking.admin.schedule.VenueScheduleController.ScheduleSlotRequest;
import com.sportsbooking.admin.schedule.VenueScheduleController.ScheduleVersionRequest;
import com.sportsbooking.admin.schedule.VenueScheduleController.SpecialDateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VenueScheduleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SCHEDULE_VERSION_STATUSES = new HashSet<String>(Arrays.asList("DRAFT", "PUBLISHED", "RETIRED"));
    private static final Set<String> SPECIAL_TYPES = new HashSet<String>(Arrays.asList("CLOSED", "OVERRIDE"));
    private static final Set<String> INVENTORY_STATUSES = new HashSet<String>(Arrays.asList(
        "AVAILABLE", "PAYMENT_LOCKED", "PROCESSING", "UNKNOWN", "BOOKED", "INTERNAL_BLOCKED", "RECONCILE_PENDING"));
    private static final Set<String> BLOCK_TYPES = new HashSet<String>(Arrays.asList("OFFLINE_RESERVATION", "MAINTENANCE", "EVENT", "SELF_USE"));
    private static final Set<String> BLOCK_STATUSES = new HashSet<String>(Arrays.asList("ACTIVE", "CANCELLED"));
    private final JdbcTemplate jdbcTemplate;

    public VenueScheduleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> listScheduleVersions(long storeId, int page, int pageSize, String status) {
        validatePage(page, pageSize);
        validateStatusFilter(status, SCHEDULE_VERSION_STATUSES);
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(storeId);
        String where = " WHERE store_id = ? AND is_deleted = 0";
        if (StringUtils.hasText(status)) {
            where += " AND version_status = ?";
            parameters.add(status);
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM schedule_version" + where, Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(
            "SELECT id, store_id, version_no, version_status, effective_from, effective_to, published_at, gmt_created_time "
                + "FROM schedule_version" + where + " ORDER BY effective_from DESC, version_no DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> scheduleVersionSummary(scheduleVersion(resultSet)), parameters.toArray());
        return pageData(page, pageSize, total, records);
    }

    @Transactional
    public Map<String, Object> createDraft(long operatorId, long storeId, ScheduleVersionRequest body) {
        lockStore(storeId);
        ScheduleConfig config = validateScheduleConfig(storeId, body);
        int nextVersionNo = nextScheduleVersionNo(storeId);
        try {
            jdbcTemplate.update("INSERT INTO schedule_version (store_id, version_no, version_status, effective_from, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, 'DRAFT', ?, ?, ?)", storeId, nextVersionNo, config.effectiveFrom, operatorId, operatorId);
            long versionId = lastInsertId();
            writeScheduleConfig(operatorId, versionId, config);
            audit(operatorId, "SCHEDULE_DRAFT_CREATED", "SCHEDULE_VERSION", versionId, storeId, "v" + nextVersionNo);
            return getScheduleVersion(storeId, versionId);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "该生效日期已存在排期版本");
        }
    }

    public Map<String, Object> getScheduleVersion(long storeId, long scheduleVersionId) {
        ScheduleVersion version = requiredScheduleVersion(storeId, scheduleVersionId, false);
        Map<String, Object> data = scheduleVersionSummary(version);
        data.put("weeklySlots", listWeeklySlots(version.id));
        data.put("specialDates", listSpecialDates(version.id));
        return data;
    }

    @Transactional
    public Map<String, Object> updateDraft(long operatorId, long storeId, long scheduleVersionId, ScheduleVersionRequest body) {
        lockStore(storeId);
        ScheduleVersion version = requiredScheduleVersion(storeId, scheduleVersionId, true);
        if (!"DRAFT".equals(version.versionStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "已发布排期版本不能修改");
        }
        ScheduleConfig config = validateScheduleConfig(storeId, body);
        try {
            jdbcTemplate.update("UPDATE schedule_version SET effective_from = ?, modify_operator_id = ? WHERE id = ? AND version_status = 'DRAFT' AND is_deleted = 0",
                config.effectiveFrom, operatorId, version.id);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "该生效日期已存在排期版本");
        }
        jdbcTemplate.update("DELETE FROM special_date_slot WHERE special_date_id IN (SELECT id FROM special_date_schedule WHERE schedule_version_id = ?)", version.id);
        jdbcTemplate.update("DELETE FROM special_date_schedule WHERE schedule_version_id = ?", version.id);
        jdbcTemplate.update("DELETE FROM weekly_schedule_slot WHERE schedule_version_id = ?", version.id);
        writeScheduleConfig(operatorId, version.id, config);
        audit(operatorId, "SCHEDULE_DRAFT_UPDATED", "SCHEDULE_VERSION", version.id, storeId, "配置已整体替换");
        return getScheduleVersion(storeId, version.id);
    }

    @Transactional
    public Map<String, Object> publishDraft(long operatorId, long storeId, long scheduleVersionId) {
        lockStore(storeId);
        ScheduleVersion draft = requiredScheduleVersion(storeId, scheduleVersionId, true);
        if (!"DRAFT".equals(draft.versionStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "仅草稿排期版本可以发布");
        }
        if (draft.effectiveFrom.isBefore(today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "排期生效日期不能早于今天");
        }
        List<ScheduleVersion> publishedVersions = jdbcTemplate.query(
            "SELECT id, store_id, version_no, version_status, effective_from, effective_to, published_at, gmt_created_time "
                + "FROM schedule_version WHERE store_id = ? AND version_status = 'PUBLISHED' AND is_deleted = 0 "
                + "ORDER BY effective_from DESC LIMIT 1 FOR UPDATE",
            (resultSet, rowNum) -> scheduleVersion(resultSet), storeId);
        if (!publishedVersions.isEmpty()) {
            ScheduleVersion previous = publishedVersions.get(0);
            if (!draft.effectiveFrom.isAfter(previous.effectiveFrom)) {
                throw new ApiException(HttpStatus.CONFLICT, 0, "新排期生效日期必须晚于已发布版本");
            }
            jdbcTemplate.update("UPDATE schedule_version SET effective_to = ?, modify_operator_id = ? WHERE id = ?",
                draft.effectiveFrom.minusDays(1), operatorId, previous.id);
        }
        jdbcTemplate.update("UPDATE schedule_version SET version_status = 'PUBLISHED', effective_to = NULL, published_at = UTC_TIMESTAMP(), modify_operator_id = ? "
                + "WHERE id = ? AND version_status = 'DRAFT' AND is_deleted = 0", operatorId, draft.id);
        rebuildHorizon(storeId);
        audit(operatorId, "SCHEDULE_VERSION_PUBLISHED", "SCHEDULE_VERSION", draft.id, storeId, "自 " + DATE_FORMATTER.format(draft.effectiveFrom) + " 生效");
        return getScheduleVersion(storeId, draft.id);
    }

    public Map<String, Object> listInventory(long storeId, String dateValue, int page, int pageSize, String status) {
        validatePage(page, pageSize);
        validateStatusFilter(status, INVENTORY_STATUSES);
        LocalDate date = parseDate(dateValue, "库存日期格式不正确");
        List<InventorySlot> slots = jdbcTemplate.query(
            "SELECT i.id, i.store_id, i.schedule_version_id, i.sellable_unit_version_id, i.inventory_date, i.start_time, i.end_time, i.price_fen, "
                + "u.name AS sellable_unit_name, v.version_no AS sellable_unit_version_no "
                + "FROM inventory_slot i JOIN sellable_unit_version v ON v.id = i.sellable_unit_version_id "
                + "JOIN sellable_unit u ON u.id = v.sellable_unit_id "
                + "WHERE i.store_id = ? AND i.inventory_date = ? AND i.is_deleted = 0 ORDER BY i.start_time, i.id",
            (resultSet, rowNum) -> inventorySlot(resultSet), storeId, date);
        List<Map<String, Object>> filtered = new ArrayList<Map<String, Object>>();
        for (InventorySlot slot : slots) {
            Map<String, Object> data = inventorySlotData(slot, listInventoryAtoms(slot.id, false));
            if (!StringUtils.hasText(status) || status.equals(data.get("inventoryStatus"))) {
                filtered.add(data);
            }
        }
        return pageData(page, pageSize, filtered.size(), slice(filtered, page, pageSize));
    }

    public Map<String, Object> listBlocks(long storeId, int page, int pageSize, String dateValue, String status) {
        validatePage(page, pageSize);
        validateStatusFilter(status, BLOCK_STATUSES);
        LocalDate date = StringUtils.hasText(dateValue) ? parseDate(dateValue, "占用日期格式不正确") : null;
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(storeId);
        String where = " WHERE b.store_id = ? AND b.is_deleted = 0";
        if (date != null) {
            where += " AND i.inventory_date = ?";
            parameters.add(date);
        }
        if (StringUtils.hasText(status)) {
            where += " AND b.block_status = ?";
            parameters.add(status);
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM manual_block b JOIN inventory_slot i ON i.id = b.inventory_slot_id" + where,
            Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(
            "SELECT b.id, b.block_no, b.store_id, b.inventory_slot_id, b.sellable_unit_version_id, b.block_type, b.contact_name, b.contact_phone, b.remark, "
                + "b.block_status, b.cancelled_at, b.gmt_created_time, i.inventory_date, i.start_time, i.end_time, u.name AS sellable_unit_name, v.version_no AS sellable_unit_version_no "
                + "FROM manual_block b JOIN inventory_slot i ON i.id = b.inventory_slot_id "
                + "JOIN sellable_unit_version v ON v.id = b.sellable_unit_version_id JOIN sellable_unit u ON u.id = v.sellable_unit_id" + where
                + " ORDER BY i.inventory_date DESC, i.start_time DESC, b.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> blockData(manualBlock(resultSet)), parameters.toArray());
        return pageData(page, pageSize, total, records);
    }

    @Transactional
    public Map<String, Object> createManualBlock(long operatorId, long storeId, CreateManualBlockRequest body) {
        validateBlockRequest(body);
        InventorySlot slot = requiredInventorySlot(storeId, body.getInventorySlotId(), true);
        if (!slot.startDateTime().isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "仅可创建未来固定时段的人工占用");
        }
        List<InventoryAtom> atoms = listInventoryAtoms(slot.id, true);
        if (atoms.isEmpty() || !allAvailable(atoms)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "所选固定时段存在库存冲突");
        }
        String blockNo = "BLK" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        jdbcTemplate.update("INSERT INTO manual_block (block_no, store_id, inventory_slot_id, sellable_unit_version_id, block_type, contact_name, contact_phone, remark, "
                + "block_status, create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
            blockNo, storeId, slot.id, slot.sellableUnitVersionId, body.getBlockType(), body.getContactName(), body.getContactPhone(), body.getRemark(),
            operatorId, operatorId);
        long blockId = lastInsertId();
        for (InventoryAtom atom : atoms) {
            int updated = jdbcTemplate.update("UPDATE inventory_atom SET inventory_status = 'INTERNAL_BLOCKED', manual_block_id = ?, modify_operator_id = ? "
                    + "WHERE id = ? AND inventory_status = 'AVAILABLE' AND manual_block_id IS NULL AND is_deleted = 0", blockId, operatorId, atom.id);
            if (updated != 1) {
                throw new ApiException(HttpStatus.CONFLICT, 0, "所选固定时段存在库存冲突");
            }
        }
        audit(operatorId, "MANUAL_BLOCK_CREATED", "MANUAL_BLOCK", blockId, storeId, body.getBlockType());
        return getManualBlock(storeId, blockId);
    }

    @Transactional
    public void cancelManualBlock(long operatorId, long storeId, long blockId) {
        ManualBlock block = requiredManualBlock(storeId, blockId, true);
        if (!"ACTIVE".equals(block.blockStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "人工占用已取消");
        }
        if (!block.startDateTime().isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "仅可取消未来人工占用");
        }
        List<InventoryAtom> atoms = listManualBlockAtoms(block.id, true);
        if (atoms.isEmpty()) {
            throw new IllegalStateException("人工占用缺少库存原子");
        }
        String releasedStatus = isScheduleVersionEffective(storeId, block.scheduleVersionId, block.inventoryDate) ? "AVAILABLE" : "RECONCILE_PENDING";
        for (InventoryAtom atom : atoms) {
            int updated = jdbcTemplate.update("UPDATE inventory_atom SET inventory_status = ?, manual_block_id = NULL, modify_operator_id = ? "
                    + "WHERE id = ? AND inventory_status = 'INTERNAL_BLOCKED' AND manual_block_id = ? AND is_deleted = 0",
                releasedStatus, operatorId, atom.id, block.id);
            if (updated != 1) {
                throw new ApiException(HttpStatus.CONFLICT, 0, "人工占用库存状态已变化");
            }
        }
        jdbcTemplate.update("UPDATE manual_block SET block_status = 'CANCELLED', cancelled_at = UTC_TIMESTAMP(), modify_operator_id = ? "
                + "WHERE id = ? AND block_status = 'ACTIVE' AND is_deleted = 0", operatorId, block.id);
        audit(operatorId, "MANUAL_BLOCK_CANCELLED", "MANUAL_BLOCK", block.id, storeId, releasedStatus);
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void rollPublishedInventory() {
        List<Long> storeIds = jdbcTemplate.query("SELECT DISTINCT store_id FROM schedule_version WHERE version_status = 'PUBLISHED' AND is_deleted = 0",
            (resultSet, rowNum) -> resultSet.getLong("store_id"));
        for (Long storeId : storeIds) {
            lockStore(storeId);
            rebuildHorizon(storeId);
        }
    }

    private ScheduleConfig validateScheduleConfig(long storeId, ScheduleVersionRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "排期版本参数不正确");
        }
        LocalDate effectiveFrom = parseDate(body.getEffectiveFrom(), "排期生效日期格式不正确");
        if (effectiveFrom.isBefore(today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "排期生效日期不能早于今天");
        }
        List<SlotSpec> weeklySlots = new ArrayList<SlotSpec>();
        for (ScheduleSlotRequest slot : nonNullList(body.getWeeklySlots())) {
            if (slot == null || slot.getWeekDay() == null || slot.getWeekDay() < 1 || slot.getWeekDay() > 7) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "周模板星期参数不正确");
            }
            weeklySlots.add(toSlotSpec(storeId, slot, slot.getWeekDay(), null));
        }
        validateResourceConflicts(weeklySlots);

        Map<LocalDate, SpecialDateSpec> specialDates = new HashMap<LocalDate, SpecialDateSpec>();
        for (SpecialDateRequest specialDate : nonNullList(body.getSpecialDates())) {
            if (specialDate == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "特殊日期参数不正确");
            }
            LocalDate date = parseDate(specialDate.getDate(), "特殊日期格式不正确");
            if (date.isBefore(effectiveFrom) || !SPECIAL_TYPES.contains(specialDate.getSpecialType()) || specialDates.containsKey(date)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "特殊日期参数不正确");
            }
            List<SlotSpec> slots = new ArrayList<SlotSpec>();
            for (ScheduleSlotRequest slot : nonNullList(specialDate.getSlots())) {
                if (slot == null || slot.getWeekDay() != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, 0, "特殊日期时段不能传星期");
                }
                slots.add(toSlotSpec(storeId, slot, null, date));
            }
            if (("CLOSED".equals(specialDate.getSpecialType()) && !slots.isEmpty())
                || ("OVERRIDE".equals(specialDate.getSpecialType()) && slots.isEmpty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "特殊日期覆盖内容不正确");
            }
            validateResourceConflicts(slots);
            specialDates.put(date, new SpecialDateSpec(date, specialDate.getSpecialType(), slots));
        }
        return new ScheduleConfig(effectiveFrom, weeklySlots, new ArrayList<SpecialDateSpec>(specialDates.values()));
    }

    private SlotSpec toSlotSpec(long storeId, ScheduleSlotRequest slot, Integer weekDay, LocalDate specialDate) {
        if (slot.getSellableUnitVersionId() == null || slot.getPriceFen() == null || slot.getPriceFen() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "固定时段参数不正确");
        }
        LocalTime startTime = parseTime(slot.getStartTime());
        LocalTime endTime = parseTime(slot.getEndTime());
        if (!startTime.isBefore(endTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "固定时段结束时间必须晚于开始时间");
        }
        SellableUnitVersion unitVersion = requiredCurrentSellableUnitVersion(storeId, slot.getSellableUnitVersionId());
        return new SlotSpec(weekDay, specialDate, unitVersion, startTime, endTime, slot.getPriceFen());
    }

    private void validateResourceConflicts(List<SlotSpec> slots) {
        for (int leftIndex = 0; leftIndex < slots.size(); leftIndex++) {
            SlotSpec left = slots.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < slots.size(); rightIndex++) {
                SlotSpec right = slots.get(rightIndex);
                if (sameScheduleDimension(left, right) && overlaps(left.startTime, left.endTime, right.startTime, right.endTime)
                    && left.unitVersion.id == right.unitVersion.id) {
                    throw new ApiException(HttpStatus.CONFLICT, 0, "同一可售单元的固定时段不能重叠");
                }
            }
        }
    }

    private boolean sameScheduleDimension(SlotSpec left, SlotSpec right) {
        return left.weekDay != null ? left.weekDay.equals(right.weekDay) : left.specialDate.equals(right.specialDate);
    }

    private void writeScheduleConfig(long operatorId, long scheduleVersionId, ScheduleConfig config) {
        for (SlotSpec slot : config.weeklySlots) {
            jdbcTemplate.update("INSERT INTO weekly_schedule_slot (schedule_version_id, week_day, sellable_unit_version_id, start_time, end_time, price_fen, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", scheduleVersionId, slot.weekDay, slot.unitVersion.id, slot.startTime, slot.endTime,
                slot.priceFen, operatorId, operatorId);
        }
        for (SpecialDateSpec specialDate : config.specialDates) {
            jdbcTemplate.update("INSERT INTO special_date_schedule (schedule_version_id, schedule_date, special_type, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, ?, ?, ?)", scheduleVersionId, specialDate.date, specialDate.specialType, operatorId, operatorId);
            long specialDateId = lastInsertId();
            for (SlotSpec slot : specialDate.slots) {
                jdbcTemplate.update("INSERT INTO special_date_slot (special_date_id, sellable_unit_version_id, start_time, end_time, price_fen, create_operator_id, modify_operator_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", specialDateId, slot.unitVersion.id, slot.startTime, slot.endTime, slot.priceFen,
                    operatorId, operatorId);
            }
        }
    }

    private void rebuildHorizon(long storeId) {
        Integer bookingMaxDays = jdbcTemplate.queryForObject("SELECT booking_max_days FROM store WHERE id = ? AND is_deleted = 0", Integer.class, storeId);
        if (bookingMaxDays == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "场馆不存在");
        }
        LocalDate date = today();
        for (int offset = 0; offset < bookingMaxDays; offset++) {
            rebuildInventoryDate(storeId, date.plusDays(offset));
        }
    }

    private void rebuildInventoryDate(long storeId, LocalDate date) {
        clearRebuildableInventorySlots(storeId, date);
        for (InventoryOccurrence occurrence : occurrencesForDate(storeId, date)) {
            createInventoryOccurrence(storeId, date, occurrence);
        }
    }

    private void clearRebuildableInventorySlots(long storeId, LocalDate date) {
        List<Long> slotIds = jdbcTemplate.query(
            "SELECT s.id FROM inventory_slot s WHERE s.store_id = ? AND s.inventory_date = ? AND s.is_deleted = 0 "
                + "AND NOT EXISTS (SELECT 1 FROM inventory_slot_atom m JOIN inventory_atom a ON a.id = m.inventory_atom_id "
                + "WHERE m.inventory_slot_id = s.id AND m.is_deleted = 0 AND a.is_deleted = 0 "
                + "AND (a.manual_block_id IS NOT NULL OR a.inventory_status NOT IN ('AVAILABLE', 'RECONCILE_PENDING')))",
            (resultSet, rowNum) -> resultSet.getLong("id"), storeId, date);
        for (Long slotId : slotIds) {
            jdbcTemplate.update("DELETE FROM inventory_slot_atom WHERE inventory_slot_id = ?", slotId);
            jdbcTemplate.update("DELETE FROM inventory_slot WHERE id = ?", slotId);
        }
        jdbcTemplate.update("DELETE a FROM inventory_atom a LEFT JOIN inventory_slot_atom m ON m.inventory_atom_id = a.id AND m.is_deleted = 0 "
                + "WHERE a.store_id = ? AND a.inventory_date = ? AND m.inventory_atom_id IS NULL AND a.manual_block_id IS NULL "
                + "AND a.inventory_status IN ('AVAILABLE', 'RECONCILE_PENDING')", storeId, date);
    }

    private List<InventoryOccurrence> occurrencesForDate(long storeId, LocalDate date) {
        ScheduleVersion version = publishedVersionForDate(storeId, date);
        if (version == null) {
            return Collections.emptyList();
        }
        List<SpecialDateHeader> specialDates = jdbcTemplate.query(
            "SELECT id, schedule_date, special_type FROM special_date_schedule WHERE schedule_version_id = ? AND schedule_date = ? AND is_deleted = 0",
            (resultSet, rowNum) -> new SpecialDateHeader(resultSet.getLong("id"), resultSet.getObject("schedule_date", LocalDate.class), resultSet.getString("special_type")),
            version.id, date);
        if (!specialDates.isEmpty()) {
            SpecialDateHeader specialDate = specialDates.get(0);
            if ("CLOSED".equals(specialDate.specialType)) {
                return Collections.emptyList();
            }
            return jdbcTemplate.query(
                "SELECT id, sellable_unit_version_id, start_time, end_time, price_fen FROM special_date_slot WHERE special_date_id = ? AND is_deleted = 0 ORDER BY start_time, id",
                (resultSet, rowNum) -> new InventoryOccurrence("SPECIAL", resultSet.getLong("id"), resultSet.getLong("sellable_unit_version_id"),
                    resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class), resultSet.getInt("price_fen"), version.id), specialDate.id);
        }
        return jdbcTemplate.query(
            "SELECT id, sellable_unit_version_id, start_time, end_time, price_fen FROM weekly_schedule_slot "
                + "WHERE schedule_version_id = ? AND week_day = ? AND is_deleted = 0 ORDER BY start_time, id",
            (resultSet, rowNum) -> new InventoryOccurrence("WEEKLY", resultSet.getLong("id"), resultSet.getLong("sellable_unit_version_id"),
                resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class), resultSet.getInt("price_fen"), version.id),
            version.id, date.getDayOfWeek().getValue());
    }

    private void createInventoryOccurrence(long storeId, LocalDate date, InventoryOccurrence occurrence) {
        SellableUnitVersion version = requiredHistoricalSellableUnitVersion(storeId, occurrence.sellableUnitVersionId);
        if (inventorySlotExists(occurrence, date)) {
            return;
        }
        jdbcTemplate.update("INSERT INTO inventory_slot (store_id, schedule_version_id, source_type, weekly_schedule_slot_id, special_date_slot_id, "
                + "sellable_unit_version_id, inventory_date, start_time, end_time, price_fen, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)",
            storeId, occurrence.scheduleVersionId, occurrence.sourceType,
            "WEEKLY".equals(occurrence.sourceType) ? occurrence.sourceId : null,
            "SPECIAL".equals(occurrence.sourceType) ? occurrence.sourceId : null,
            occurrence.sellableUnitVersionId, date, occurrence.startTime, occurrence.endTime, occurrence.priceFen);
        long inventorySlotId = lastInsertId();
        for (Long baseResourceId : version.baseResourceIds) {
            for (LocalTime atomStart = occurrence.startTime; atomStart.isBefore(occurrence.endTime); atomStart = atomStart.plusMinutes(30)) {
                jdbcTemplate.update("INSERT INTO inventory_atom (store_id, base_resource_id, inventory_date, start_time, inventory_status, create_operator_id, modify_operator_id) "
                        + "VALUES (?, ?, ?, ?, 'AVAILABLE', 0, 0) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)",
                    storeId, baseResourceId, date, atomStart);
                long inventoryAtomId = lastInsertId();
                jdbcTemplate.update("INSERT INTO inventory_slot_atom (inventory_slot_id, inventory_atom_id, create_operator_id, modify_operator_id) VALUES (?, ?, 0, 0)",
                    inventorySlotId, inventoryAtomId);
            }
        }
    }

    private boolean inventorySlotExists(InventoryOccurrence occurrence, LocalDate date) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM inventory_slot WHERE "
                + ("WEEKLY".equals(occurrence.sourceType) ? "weekly_schedule_slot_id = ?" : "special_date_slot_id = ?")
                + " AND inventory_date = ? AND is_deleted = 0", Integer.class, occurrence.sourceId, date);
        return count != null && count > 0;
    }

    private ScheduleVersion publishedVersionForDate(long storeId, LocalDate date) {
        List<ScheduleVersion> versions = jdbcTemplate.query(
            "SELECT id, store_id, version_no, version_status, effective_from, effective_to, published_at, gmt_created_time FROM schedule_version "
                + "WHERE store_id = ? AND version_status = 'PUBLISHED' AND effective_from <= ? "
                + "AND (effective_to IS NULL OR effective_to >= ?) AND is_deleted = 0 ORDER BY effective_from DESC LIMIT 1",
            (resultSet, rowNum) -> scheduleVersion(resultSet), storeId, date, date);
        return versions.isEmpty() ? null : versions.get(0);
    }

    private boolean isScheduleVersionEffective(long storeId, long scheduleVersionId, LocalDate date) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM schedule_version WHERE id = ? AND store_id = ? AND version_status = 'PUBLISHED' "
                + "AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?) AND is_deleted = 0", Integer.class,
            scheduleVersionId, storeId, date, date);
        return count != null && count == 1;
    }

    private SellableUnitVersion requiredCurrentSellableUnitVersion(long storeId, long sellableUnitVersionId) {
        List<SellableUnitVersion> versions = jdbcTemplate.query(
            "SELECT v.id, v.sellable_unit_id, v.version_no, u.name AS sellable_unit_name FROM sellable_unit_version v "
                + "JOIN sellable_unit u ON u.id = v.sellable_unit_id JOIN physical_court p ON p.id = u.physical_court_id "
                + "WHERE v.id = ? AND v.version_status = 'CURRENT' AND v.is_deleted = 0 AND u.status = 'ACTIVE' AND u.is_deleted = 0 "
                + "AND p.store_id = ? AND p.status = 'ACTIVE' AND p.is_deleted = 0",
            (resultSet, rowNum) -> unitVersion(resultSet), sellableUnitVersionId, storeId);
        if (versions.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "必须选择当前上架的可售单元版本");
        }
        SellableUnitVersion version = versions.get(0);
        version.baseResourceIds.addAll(baseResourceIds(version.id));
        return version;
    }

    private SellableUnitVersion requiredHistoricalSellableUnitVersion(long storeId, long sellableUnitVersionId) {
        List<SellableUnitVersion> versions = jdbcTemplate.query(
            "SELECT v.id, v.sellable_unit_id, v.version_no, u.name AS sellable_unit_name FROM sellable_unit_version v "
                + "JOIN sellable_unit u ON u.id = v.sellable_unit_id JOIN physical_court p ON p.id = u.physical_court_id "
                + "WHERE v.id = ? AND v.is_deleted = 0 AND u.is_deleted = 0 AND p.store_id = ? AND p.is_deleted = 0",
            (resultSet, rowNum) -> unitVersion(resultSet), sellableUnitVersionId, storeId);
        if (versions.isEmpty()) {
            throw new IllegalStateException("库存引用的可售单元版本不存在");
        }
        SellableUnitVersion version = versions.get(0);
        version.baseResourceIds.addAll(baseResourceIds(version.id));
        return version;
    }

    private List<Long> baseResourceIds(long sellableUnitVersionId) {
        return jdbcTemplate.query(
            "SELECT base_resource_id FROM sellable_unit_version_resource WHERE sellable_unit_version_id = ? AND is_deleted = 0 ORDER BY sort_order, base_resource_id",
            (resultSet, rowNum) -> resultSet.getLong("base_resource_id"), sellableUnitVersionId);
    }

    private boolean sharesBaseResource(SellableUnitVersion left, SellableUnitVersion right) {
        for (Long baseResourceId : left.baseResourceIds) {
            if (right.baseResourceIds.contains(baseResourceId)) {
                return true;
            }
        }
        return false;
    }

    private ScheduleVersion requiredScheduleVersion(long storeId, long scheduleVersionId, boolean lock) {
        String sql = "SELECT id, store_id, version_no, version_status, effective_from, effective_to, published_at, gmt_created_time "
            + "FROM schedule_version WHERE id = ? AND store_id = ? AND is_deleted = 0" + (lock ? " FOR UPDATE" : "");
        List<ScheduleVersion> versions = jdbcTemplate.query(sql, (resultSet, rowNum) -> scheduleVersion(resultSet), scheduleVersionId, storeId);
        if (versions.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "排期版本不存在或不属于当前场馆");
        }
        return versions.get(0);
    }

    private InventorySlot requiredInventorySlot(long storeId, long inventorySlotId, boolean lock) {
        String sql = "SELECT i.id, i.store_id, i.schedule_version_id, i.sellable_unit_version_id, i.inventory_date, i.start_time, i.end_time, i.price_fen, "
            + "u.name AS sellable_unit_name, v.version_no AS sellable_unit_version_no "
            + "FROM inventory_slot i JOIN sellable_unit_version v ON v.id = i.sellable_unit_version_id JOIN sellable_unit u ON u.id = v.sellable_unit_id "
            + "WHERE i.id = ? AND i.store_id = ? AND i.is_deleted = 0" + (lock ? " FOR UPDATE" : "");
        List<InventorySlot> slots = jdbcTemplate.query(sql, (resultSet, rowNum) -> inventorySlot(resultSet), inventorySlotId, storeId);
        if (slots.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "固定库存时段不存在或不属于当前场馆");
        }
        return slots.get(0);
    }

    private ManualBlock requiredManualBlock(long storeId, long blockId, boolean lock) {
        String sql = "SELECT b.id, b.block_no, b.store_id, b.inventory_slot_id, b.sellable_unit_version_id, b.block_type, b.contact_name, b.contact_phone, b.remark, "
            + "b.block_status, b.cancelled_at, b.gmt_created_time, i.inventory_date, i.start_time, i.end_time, i.schedule_version_id, "
            + "u.name AS sellable_unit_name, v.version_no AS sellable_unit_version_no "
            + "FROM manual_block b JOIN inventory_slot i ON i.id = b.inventory_slot_id JOIN sellable_unit_version v ON v.id = b.sellable_unit_version_id "
            + "JOIN sellable_unit u ON u.id = v.sellable_unit_id WHERE b.id = ? AND b.store_id = ? AND b.is_deleted = 0" + (lock ? " FOR UPDATE" : "");
        List<ManualBlock> blocks = jdbcTemplate.query(sql, (resultSet, rowNum) -> manualBlock(resultSet), blockId, storeId);
        if (blocks.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "人工占用不存在或不属于当前场馆");
        }
        return blocks.get(0);
    }

    private Map<String, Object> getManualBlock(long storeId, long blockId) {
        return blockData(requiredManualBlock(storeId, blockId, false));
    }

    private List<InventoryAtom> listInventoryAtoms(long inventorySlotId, boolean lock) {
        String sql = "SELECT a.id, a.base_resource_id, a.start_time, a.inventory_status, a.manual_block_id FROM inventory_slot_atom m "
            + "JOIN inventory_atom a ON a.id = m.inventory_atom_id WHERE m.inventory_slot_id = ? AND m.is_deleted = 0 AND a.is_deleted = 0 "
            + "ORDER BY a.base_resource_id, a.start_time" + (lock ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> inventoryAtom(resultSet), inventorySlotId);
    }

    private List<InventoryAtom> listManualBlockAtoms(long manualBlockId, boolean lock) {
        String sql = "SELECT id, base_resource_id, start_time, inventory_status, manual_block_id FROM inventory_atom "
            + "WHERE manual_block_id = ? AND is_deleted = 0 ORDER BY base_resource_id, start_time" + (lock ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> inventoryAtom(resultSet), manualBlockId);
    }

    private List<Map<String, Object>> listWeeklySlots(long scheduleVersionId) {
        List<TemplateSlot> slots = jdbcTemplate.query(
            "SELECT w.id, w.week_day, w.sellable_unit_version_id, w.start_time, w.end_time, w.price_fen, u.name AS sellable_unit_name, v.version_no AS sellable_unit_version_no "
                + "FROM weekly_schedule_slot w JOIN sellable_unit_version v ON v.id = w.sellable_unit_version_id JOIN sellable_unit u ON u.id = v.sellable_unit_id "
                + "WHERE w.schedule_version_id = ? AND w.is_deleted = 0 ORDER BY w.week_day, w.start_time, w.id",
            (resultSet, rowNum) -> templateSlot(resultSet), scheduleVersionId);
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (TemplateSlot slot : slots) {
            data.add(templateSlotData(slot, true));
        }
        return data;
    }

    private List<Map<String, Object>> listSpecialDates(long scheduleVersionId) {
        List<SpecialDateHeader> headers = jdbcTemplate.query(
            "SELECT id, schedule_date, special_type FROM special_date_schedule WHERE schedule_version_id = ? AND is_deleted = 0 ORDER BY schedule_date, id",
            (resultSet, rowNum) -> new SpecialDateHeader(resultSet.getLong("id"), resultSet.getObject("schedule_date", LocalDate.class), resultSet.getString("special_type")),
            scheduleVersionId);
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (SpecialDateHeader header : headers) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", String.valueOf(header.id));
            item.put("date", DATE_FORMATTER.format(header.date));
            item.put("specialType", header.specialType);
            List<TemplateSlot> slots = jdbcTemplate.query(
                "SELECT s.id, NULL AS week_day, s.sellable_unit_version_id, s.start_time, s.end_time, s.price_fen, u.name AS sellable_unit_name, "
                    + "v.version_no AS sellable_unit_version_no FROM special_date_slot s JOIN sellable_unit_version v ON v.id = s.sellable_unit_version_id "
                    + "JOIN sellable_unit u ON u.id = v.sellable_unit_id WHERE s.special_date_id = ? AND s.is_deleted = 0 ORDER BY s.start_time, s.id",
                (resultSet, rowNum) -> templateSlot(resultSet), header.id);
            List<Map<String, Object>> slotData = new ArrayList<Map<String, Object>>();
            for (TemplateSlot slot : slots) {
                slotData.add(templateSlotData(slot, false));
            }
            item.put("slots", slotData);
            data.add(item);
        }
        return data;
    }

    private Map<String, Object> inventorySlotData(InventorySlot slot, List<InventoryAtom> atoms) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(slot.id));
        data.put("scheduleVersionId", String.valueOf(slot.scheduleVersionId));
        data.put("sellableUnitVersionId", String.valueOf(slot.sellableUnitVersionId));
        data.put("sellableUnitName", slot.sellableUnitName);
        data.put("sellableUnitVersionNo", slot.sellableUnitVersionNo);
        data.put("date", DATE_FORMATTER.format(slot.inventoryDate));
        data.put("startTime", TIME_FORMATTER.format(slot.startTime));
        data.put("endTime", TIME_FORMATTER.format(slot.endTime));
        data.put("priceFen", slot.priceFen);
        data.put("inventoryStatus", aggregateInventoryStatus(atoms));
        List<Map<String, Object>> atomData = new ArrayList<Map<String, Object>>();
        for (InventoryAtom atom : atoms) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("baseResourceId", String.valueOf(atom.baseResourceId));
            item.put("startTime", TIME_FORMATTER.format(atom.startTime));
            item.put("inventoryStatus", atom.inventoryStatus);
            item.put("manualBlockId", atom.manualBlockId == null ? null : String.valueOf(atom.manualBlockId));
            atomData.add(item);
        }
        data.put("atoms", atomData);
        return data;
    }

    private Map<String, Object> scheduleVersionSummary(ScheduleVersion version) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(version.id));
        data.put("versionNo", version.versionNo);
        data.put("versionStatus", version.versionStatus);
        data.put("effectiveFrom", DATE_FORMATTER.format(version.effectiveFrom));
        data.put("effectiveTo", version.effectiveTo == null ? null : DATE_FORMATTER.format(version.effectiveTo));
        data.put("publishedAt", version.publishedAt == null ? null : DATETIME_FORMATTER.format(version.publishedAt));
        data.put("createTime", DATETIME_FORMATTER.format(version.createdTime));
        return data;
    }

    private Map<String, Object> templateSlotData(TemplateSlot slot, boolean includeWeekDay) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(slot.id));
        if (includeWeekDay) {
            data.put("weekDay", slot.weekDay);
        }
        data.put("sellableUnitVersionId", String.valueOf(slot.sellableUnitVersionId));
        data.put("sellableUnitName", slot.sellableUnitName);
        data.put("sellableUnitVersionNo", slot.sellableUnitVersionNo);
        data.put("startTime", TIME_FORMATTER.format(slot.startTime));
        data.put("endTime", TIME_FORMATTER.format(slot.endTime));
        data.put("priceFen", slot.priceFen);
        return data;
    }

    private Map<String, Object> blockData(ManualBlock block) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(block.id));
        data.put("blockNo", block.blockNo);
        data.put("inventorySlotId", String.valueOf(block.inventorySlotId));
        data.put("sellableUnitVersionId", String.valueOf(block.sellableUnitVersionId));
        data.put("sellableUnitName", block.sellableUnitName);
        data.put("sellableUnitVersionNo", block.sellableUnitVersionNo);
        data.put("date", DATE_FORMATTER.format(block.inventoryDate));
        data.put("startTime", TIME_FORMATTER.format(block.startTime));
        data.put("endTime", TIME_FORMATTER.format(block.endTime));
        data.put("blockType", block.blockType);
        data.put("contactName", block.contactName);
        data.put("contactPhone", block.contactPhone);
        data.put("remark", block.remark);
        data.put("blockStatus", block.blockStatus);
        data.put("cancelledAt", block.cancelledAt == null ? null : DATETIME_FORMATTER.format(block.cancelledAt));
        data.put("createTime", DATETIME_FORMATTER.format(block.createdTime));
        return data;
    }

    private ScheduleVersion scheduleVersion(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ScheduleVersion(resultSet.getLong("id"), resultSet.getLong("store_id"), resultSet.getInt("version_no"),
            resultSet.getString("version_status"), resultSet.getObject("effective_from", LocalDate.class),
            resultSet.getObject("effective_to", LocalDate.class), resultSet.getObject("published_at", LocalDateTime.class),
            resultSet.getObject("gmt_created_time", LocalDateTime.class));
    }

    private SellableUnitVersion unitVersion(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new SellableUnitVersion(resultSet.getLong("id"), resultSet.getLong("sellable_unit_id"), resultSet.getInt("version_no"),
            resultSet.getString("sellable_unit_name"));
    }

    private TemplateSlot templateSlot(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        int weekDay = resultSet.getInt("week_day");
        Integer nullableWeekDay = resultSet.wasNull() ? null : weekDay;
        return new TemplateSlot(resultSet.getLong("id"), nullableWeekDay, resultSet.getLong("sellable_unit_version_id"),
            resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class), resultSet.getInt("price_fen"),
            resultSet.getString("sellable_unit_name"), resultSet.getInt("sellable_unit_version_no"));
    }

    private InventorySlot inventorySlot(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new InventorySlot(resultSet.getLong("id"), resultSet.getLong("store_id"), resultSet.getLong("schedule_version_id"),
            resultSet.getLong("sellable_unit_version_id"), resultSet.getObject("inventory_date", LocalDate.class),
            resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class), resultSet.getInt("price_fen"),
            resultSet.getString("sellable_unit_name"), resultSet.getInt("sellable_unit_version_no"));
    }

    private InventoryAtom inventoryAtom(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        long manualBlockId = resultSet.getLong("manual_block_id");
        boolean manualBlockIdIsNull = resultSet.wasNull();
        return new InventoryAtom(resultSet.getLong("id"), resultSet.getLong("base_resource_id"), resultSet.getObject("start_time", LocalTime.class),
            resultSet.getString("inventory_status"), manualBlockIdIsNull ? null : manualBlockId);
    }

    private ManualBlock manualBlock(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ManualBlock(resultSet.getLong("id"), resultSet.getString("block_no"), resultSet.getLong("store_id"),
            resultSet.getLong("inventory_slot_id"), resultSet.getLong("sellable_unit_version_id"), resultSet.getString("block_type"),
            resultSet.getString("contact_name"), resultSet.getString("contact_phone"), resultSet.getString("remark"), resultSet.getString("block_status"),
            resultSet.getObject("cancelled_at", LocalDateTime.class), resultSet.getObject("gmt_created_time", LocalDateTime.class),
            resultSet.getObject("inventory_date", LocalDate.class), resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class),
            resultSet.getLong("schedule_version_id"), resultSet.getString("sellable_unit_name"), resultSet.getInt("sellable_unit_version_no"));
    }

    private void lockStore(long storeId) {
        List<Long> storeIds = jdbcTemplate.query("SELECT id FROM store WHERE id = ? AND is_deleted = 0 FOR UPDATE",
            (resultSet, rowNum) -> resultSet.getLong("id"), storeId);
        if (storeIds.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "场馆不存在");
        }
    }

    private int nextScheduleVersionNo(long storeId) {
        Integer maximum = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(version_no), 0) FROM schedule_version WHERE store_id = ?", Integer.class, storeId);
        return maximum == null ? 1 : maximum + 1;
    }

    private void validateBlockRequest(CreateManualBlockRequest body) {
        if (body == null || body.getInventorySlotId() == null || body.getInventorySlotId() <= 0 || !BLOCK_TYPES.contains(body.getBlockType())
            || lengthExceeds(body.getContactName(), 64) || lengthExceeds(body.getContactPhone(), 32) || lengthExceeds(body.getRemark(), 255)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "人工占用参数不正确");
        }
    }

    private boolean lengthExceeds(String value, int maximum) {
        return value != null && value.length() > maximum;
    }

    private LocalDate parseDate(String value, String message) {
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, message);
        }
    }

    private LocalTime parseTime(String value) {
        try {
            LocalTime time = LocalTime.parse(value, TIME_FORMATTER);
            if (time.getMinute() % 30 != 0 || time.getSecond() != 0 || time.getNano() != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 0, "固定时段必须按30分钟对齐");
            }
            return time;
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "固定时段时间格式不正确");
        }
    }

    private boolean overlaps(LocalTime leftStart, LocalTime leftEnd, LocalTime rightStart, LocalTime rightEnd) {
        return leftStart.isBefore(rightEnd) && rightStart.isBefore(leftEnd);
    }

    private boolean allAvailable(List<InventoryAtom> atoms) {
        for (InventoryAtom atom : atoms) {
            if (!"AVAILABLE".equals(atom.inventoryStatus) || atom.manualBlockId != null) {
                return false;
            }
        }
        return true;
    }

    private String aggregateInventoryStatus(List<InventoryAtom> atoms) {
        for (InventoryAtom atom : atoms) {
            if (!"AVAILABLE".equals(atom.inventoryStatus)) {
                return atom.inventoryStatus;
            }
        }
        return "AVAILABLE";
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        }
    }

    private void validateStatusFilter(String status, Set<String> allowedStatuses) {
        if (StringUtils.hasText(status) && !allowedStatuses.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "状态参数不正确");
        }
    }

    private List<Map<String, Object>> slice(List<Map<String, Object>> records, int page, int pageSize) {
        int start = Math.min((page - 1) * pageSize, records.size());
        int end = Math.min(start + pageSize, records.size());
        return new ArrayList<Map<String, Object>>(records.subList(start, end));
    }

    private <T> List<T> nonNullList(List<T> values) {
        return values == null ? Collections.<T>emptyList() : values;
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

    private void audit(long operatorId, String action, String targetType, long targetId, long storeId, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', ?, ?, ?)", operatorId, action, targetType, targetId, storeId, reason, operatorId, operatorId);
    }

    private static final class ScheduleConfig {
        private final LocalDate effectiveFrom;
        private final List<SlotSpec> weeklySlots;
        private final List<SpecialDateSpec> specialDates;

        private ScheduleConfig(LocalDate effectiveFrom, List<SlotSpec> weeklySlots, List<SpecialDateSpec> specialDates) {
            this.effectiveFrom = effectiveFrom;
            this.weeklySlots = weeklySlots;
            this.specialDates = specialDates;
        }
    }

    private static final class SlotSpec {
        private final Integer weekDay;
        private final LocalDate specialDate;
        private final SellableUnitVersion unitVersion;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final int priceFen;

        private SlotSpec(Integer weekDay, LocalDate specialDate, SellableUnitVersion unitVersion, LocalTime startTime, LocalTime endTime, int priceFen) {
            this.weekDay = weekDay;
            this.specialDate = specialDate;
            this.unitVersion = unitVersion;
            this.startTime = startTime;
            this.endTime = endTime;
            this.priceFen = priceFen;
        }
    }

    private static final class SpecialDateSpec {
        private final LocalDate date;
        private final String specialType;
        private final List<SlotSpec> slots;

        private SpecialDateSpec(LocalDate date, String specialType, List<SlotSpec> slots) {
            this.date = date;
            this.specialType = specialType;
            this.slots = slots;
        }
    }

    private static final class ScheduleVersion {
        private final long id;
        private final long storeId;
        private final int versionNo;
        private final String versionStatus;
        private final LocalDate effectiveFrom;
        private final LocalDate effectiveTo;
        private final LocalDateTime publishedAt;
        private final LocalDateTime createdTime;

        private ScheduleVersion(long id, long storeId, int versionNo, String versionStatus, LocalDate effectiveFrom, LocalDate effectiveTo,
                                LocalDateTime publishedAt, LocalDateTime createdTime) {
            this.id = id;
            this.storeId = storeId;
            this.versionNo = versionNo;
            this.versionStatus = versionStatus;
            this.effectiveFrom = effectiveFrom;
            this.effectiveTo = effectiveTo;
            this.publishedAt = publishedAt;
            this.createdTime = createdTime;
        }
    }

    private static final class SellableUnitVersion {
        private final long id;
        private final long sellableUnitId;
        private final int versionNo;
        private final String sellableUnitName;
        private final List<Long> baseResourceIds = new ArrayList<Long>();

        private SellableUnitVersion(long id, long sellableUnitId, int versionNo, String sellableUnitName) {
            this.id = id;
            this.sellableUnitId = sellableUnitId;
            this.versionNo = versionNo;
            this.sellableUnitName = sellableUnitName;
        }
    }

    private static final class TemplateSlot {
        private final long id;
        private final Integer weekDay;
        private final long sellableUnitVersionId;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final int priceFen;
        private final String sellableUnitName;
        private final int sellableUnitVersionNo;

        private TemplateSlot(long id, Integer weekDay, long sellableUnitVersionId, LocalTime startTime, LocalTime endTime, int priceFen,
                             String sellableUnitName, int sellableUnitVersionNo) {
            this.id = id;
            this.weekDay = weekDay;
            this.sellableUnitVersionId = sellableUnitVersionId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.priceFen = priceFen;
            this.sellableUnitName = sellableUnitName;
            this.sellableUnitVersionNo = sellableUnitVersionNo;
        }
    }

    private static final class SpecialDateHeader {
        private final long id;
        private final LocalDate date;
        private final String specialType;

        private SpecialDateHeader(long id, LocalDate date, String specialType) {
            this.id = id;
            this.date = date;
            this.specialType = specialType;
        }
    }

    private static final class InventoryOccurrence {
        private final String sourceType;
        private final long sourceId;
        private final long sellableUnitVersionId;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final int priceFen;
        private final long scheduleVersionId;

        private InventoryOccurrence(String sourceType, long sourceId, long sellableUnitVersionId, LocalTime startTime, LocalTime endTime,
                                    int priceFen, long scheduleVersionId) {
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.sellableUnitVersionId = sellableUnitVersionId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.priceFen = priceFen;
            this.scheduleVersionId = scheduleVersionId;
        }
    }

    private static final class InventorySlot {
        private final long id;
        private final long storeId;
        private final long scheduleVersionId;
        private final long sellableUnitVersionId;
        private final LocalDate inventoryDate;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final int priceFen;
        private final String sellableUnitName;
        private final int sellableUnitVersionNo;

        private InventorySlot(long id, long storeId, long scheduleVersionId, long sellableUnitVersionId, LocalDate inventoryDate,
                              LocalTime startTime, LocalTime endTime, int priceFen, String sellableUnitName, int sellableUnitVersionNo) {
            this.id = id;
            this.storeId = storeId;
            this.scheduleVersionId = scheduleVersionId;
            this.sellableUnitVersionId = sellableUnitVersionId;
            this.inventoryDate = inventoryDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.priceFen = priceFen;
            this.sellableUnitName = sellableUnitName;
            this.sellableUnitVersionNo = sellableUnitVersionNo;
        }

        private LocalDateTime startDateTime() {
            return LocalDateTime.of(inventoryDate, startTime);
        }
    }

    private static final class InventoryAtom {
        private final long id;
        private final long baseResourceId;
        private final LocalTime startTime;
        private final String inventoryStatus;
        private final Long manualBlockId;

        private InventoryAtom(long id, long baseResourceId, LocalTime startTime, String inventoryStatus, Long manualBlockId) {
            this.id = id;
            this.baseResourceId = baseResourceId;
            this.startTime = startTime;
            this.inventoryStatus = inventoryStatus;
            this.manualBlockId = manualBlockId;
        }
    }

    private static final class ManualBlock {
        private final long id;
        private final String blockNo;
        private final long storeId;
        private final long inventorySlotId;
        private final long sellableUnitVersionId;
        private final String blockType;
        private final String contactName;
        private final String contactPhone;
        private final String remark;
        private final String blockStatus;
        private final LocalDateTime cancelledAt;
        private final LocalDateTime createdTime;
        private final LocalDate inventoryDate;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final long scheduleVersionId;
        private final String sellableUnitName;
        private final int sellableUnitVersionNo;

        private ManualBlock(long id, String blockNo, long storeId, long inventorySlotId, long sellableUnitVersionId, String blockType,
                            String contactName, String contactPhone, String remark, String blockStatus, LocalDateTime cancelledAt,
                            LocalDateTime createdTime, LocalDate inventoryDate, LocalTime startTime, LocalTime endTime, long scheduleVersionId,
                            String sellableUnitName, int sellableUnitVersionNo) {
            this.id = id;
            this.blockNo = blockNo;
            this.storeId = storeId;
            this.inventorySlotId = inventorySlotId;
            this.sellableUnitVersionId = sellableUnitVersionId;
            this.blockType = blockType;
            this.contactName = contactName;
            this.contactPhone = contactPhone;
            this.remark = remark;
            this.blockStatus = blockStatus;
            this.cancelledAt = cancelledAt;
            this.createdTime = createdTime;
            this.inventoryDate = inventoryDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.scheduleVersionId = scheduleVersionId;
            this.sellableUnitName = sellableUnitName;
            this.sellableUnitVersionNo = sellableUnitVersionNo;
        }

        private LocalDateTime startDateTime() {
            return LocalDateTime.of(inventoryDate, startTime);
        }
    }
}
