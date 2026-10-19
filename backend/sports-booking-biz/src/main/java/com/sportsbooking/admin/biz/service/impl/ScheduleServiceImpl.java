package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.biz.constants.RedisLockType;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.biz.pojo.request.ExtraSlotCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.WeeklySlotSaveRequest;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.WeeklySlot;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.WeeklySlotMapper;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.biz.pojo.vo.SessionSlotVO;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.ReminderService;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能描述: 排期业务实现，负责周模板维护及未来具体场次同步。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class ScheduleServiceImpl implements ScheduleService {
    private final WeeklySlotMapper weeklySlotMapper;
    private final SessionSlotMapper sessionSlotMapper;
    private final CourtMapper courtMapper;
    private final SportTypeMapper sportTypeMapper;
    private final VenueService venueService;
    private final RedisLockService redisLockService;
    private final ReminderService reminderService;
    private final BookingMapper bookingMapper;

    public ScheduleServiceImpl(WeeklySlotMapper weeklySlotMapper, SessionSlotMapper sessionSlotMapper,
                           CourtMapper courtMapper, SportTypeMapper sportTypeMapper, VenueService venueService,
                           RedisLockService redisLockService, BookingMapper bookingMapper) {
        this(weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, venueService, redisLockService, null, bookingMapper);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ScheduleServiceImpl(WeeklySlotMapper weeklySlotMapper, SessionSlotMapper sessionSlotMapper,
                           CourtMapper courtMapper, SportTypeMapper sportTypeMapper, VenueService venueService,
                           RedisLockService redisLockService, ReminderService reminderService, BookingMapper bookingMapper) {
        this.weeklySlotMapper = weeklySlotMapper;
        this.sessionSlotMapper = sessionSlotMapper;
        this.courtMapper = courtMapper;
        this.sportTypeMapper = sportTypeMapper;
        this.venueService = venueService;
        this.redisLockService = redisLockService;
        this.reminderService = reminderService;
        this.bookingMapper = bookingMapper;
    }

    /**
     * 功能描述: 查询指定场馆全部周场次模板。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<WeeklySlot> listWeekly(long venueId) {
        venueService.getVenue(venueId);
        return weeklySlotMapper.selectByVenue(venueId, null);
    }

    /**
     * 功能描述: 新增或更新周场次模板并同步未来具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param weeklySlotId 待编辑的周场次 ID，新增时为空
     * @param body 周场次保存请求
     * @return 已保存的周场次
     * @author gjm
     */
    @Transactional
    @Override
    public WeeklySlot saveWeekly(long venueId, Long weeklySlotId, WeeklySlotSaveRequest body) {
        courtMapper.selectVenueForUpdate(venueId);
        WeeklySlot weekly = weeklySlotId == null ? new WeeklySlot() : requiredWeekly(venueId, weeklySlotId);
        requireSlotSelection(body == null ? null : body.getCourtId(), body == null ? null : body.getSportTypeId());
        Court court = requiredCourt(venueId, body.getCourtId());
        SportType sport = requiredSport(venueId, body.getSportTypeId(), weeklySlotId == null ? null : weekly.sportTypeId);
        if (body.getDayOfWeek() == null || body.getDayOfWeek() < 1 || body.getDayOfWeek() > 7) {
            throw badRequest("请选择星期一至星期日");
        }
        if (body.getStartTime() == null || body.getEndTime() == null || !body.getStartTime().isBefore(body.getEndTime())) {
            throw badRequest("请填写开始和结束时间，结束时间必须晚于开始时间");
        }
        if (body.getPriceFen() == null || body.getPriceFen() < 0) {
            throw badRequest("场次价格不能为空且不能小于 0");
        }
        if (!courtMapper.selectSportIds(court.id).contains(sport.id)) {
            throw badRequest("所选场地不支持该运动，请重新选择运动类型");
        }
        // 锁定物理场地后检查时间区间，防止并发创建两个互相重叠的库存。
        courtMapper.selectForUpdate(court.id);
        if (weeklySlotMapper.countOverlapping(court.id, body.getDayOfWeek(), body.getStartTime(),
                body.getEndTime(), weeklySlotId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
        }

        weekly.courtId = court.id;
        weekly.sportTypeId = sport.id;
        weekly.dayOfWeek = body.getDayOfWeek();
        weekly.startTime = body.getStartTime();
        weekly.endTime = body.getEndTime();
        weekly.priceFen = body.getPriceFen();
        weekly.status = EnableStatus.ACTIVE;
        if (weeklySlotId == null) {
            weekly.createdAt = LocalDateTime.now();
            weeklySlotMapper.insert(weekly);
        } else {
            weeklySlotMapper.updateById(weekly);
        }
        Venue venue = venueService.getVenue(venueId);
        LocalDate firstDate = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        List<Booking> snapshots = loadWindowSnapshots(venue, List.of(weekly), firstDate);
        syncTemplate(venue, weekly, firstDate, snapshots);
        return weekly;
    }

    /**
     * 功能描述: 停用周场次模板并关闭未占用的未来场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Transactional
    @Override
    public void disableWeekly(long venueId, long weeklySlotId) {
        courtMapper.selectVenueForUpdate(venueId);
        WeeklySlot weekly = requiredWeekly(venueId, weeklySlotId);
        weekly.status = EnableStatus.DISABLED;
        weeklySlotMapper.updateById(weekly);
        sessionSlotMapper.closeOpenForTemplate(weekly.id, Instant.now());
    }

    /**
     * 功能描述: 将源星期的有效模板复制到目标星期。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param sourceDay 来源星期
     * @param targetDays 目标星期列表
     * @return 已复制的周场次列表
     * @author gjm
     */
    @Transactional
    @Override
    public List<WeeklySlot> copyDay(long venueId, int sourceDay, List<Integer> targetDays) {
        if (sourceDay < 1 || sourceDay > 7 || targetDays == null || targetDays.isEmpty()) {
            throw badRequest("请选择来源星期和目标星期");
        }
        courtMapper.selectVenueForUpdate(venueId);
        List<WeeklySlot> source = weeklySlotMapper.selectByVenue(venueId, EnableStatus.ACTIVE).stream()
                .filter(slot -> slot.dayOfWeek == sourceDay)
                .toList();
        if (source.isEmpty()) {
            throw badRequest("来源星期没有启用中的固定排期，无法复制");
        }
        Venue venue = venueService.getVenue(venueId);
        LocalDate firstDate = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        List<Booking> snapshots = loadWindowSnapshots(venue, source, firstDate);
        for (Integer targetDay : targetDays.stream().distinct().toList()) {
            if (targetDay == null || targetDay < 1 || targetDay > 7 || targetDay == sourceDay) {
                continue;
            }
            List<WeeklySlot> existing = weeklySlotMapper.selectByVenue(venueId, null).stream()
                    .filter(slot -> slot.dayOfWeek.equals(targetDay))
                    .toList();
            Set<Long> reusedIds = new HashSet<>();
            List<WeeklySlot> copiedSlots = new ArrayList<>();
            // 优先复用同场地同时间的旧模板 ID，避免唯一具体场次无法切换到新模板。
            for (WeeklySlot template : source) {
                WeeklySlot copy = existing.stream()
                        .filter(slot -> slot.courtId.equals(template.courtId)
                                && slot.startTime.equals(template.startTime)
                                && slot.endTime.equals(template.endTime))
                        .findFirst()
                        .orElseGet(WeeklySlot::new);
                copy.courtId = template.courtId;
                copy.sportTypeId = template.sportTypeId;
                copy.dayOfWeek = targetDay;
                copy.startTime = template.startTime;
                copy.endTime = template.endTime;
                copy.priceFen = template.priceFen;
                copy.status = EnableStatus.ACTIVE;
                if (copy.id == null) {
                    copy.createdAt = LocalDateTime.now();
                    weeklySlotMapper.insert(copy);
                } else {
                    weeklySlotMapper.updateById(copy);
                }
                reusedIds.add(copy.id);
                copiedSlots.add(copy);
            }
            for (WeeklySlot old : existing) {
                if (!reusedIds.contains(old.id) && old.status == EnableStatus.ACTIVE) {
                    old.status = EnableStatus.DISABLED;
                    weeklySlotMapper.updateById(old);
                    sessionSlotMapper.closeOpenForTemplate(old.id, Instant.now());
                }
            }
            // 先停用被替换模板再同步新模板；已有占位或预约仍会阻止重叠库存生成。
            for (WeeklySlot copiedSlot : copiedSlots) {
                syncTemplate(venue, copiedSlot, firstDate, snapshots);
            }
        }
        return listWeekly(venueId);
    }

    /**
     * 功能描述: 同步并查询场馆指定日期的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public List<SessionSlotVO> listDay(long venueId, LocalDate date) {
        return listDay(venueId, date, null);
    }

    /**
     * 功能描述: 同步并按运动类型查询场馆指定日期的具体场次及当前关联预约。
     *
     * @param venueId 场馆 ID
     * @param date 日期
     * @param sportTypeId 运动类型 ID，为空时查询全部运动
     * @return 具体场次列表
     * @author gjm
     */
    @Override
    @Transactional
    public List<SessionSlotVO> listDay(long venueId, LocalDate date, Long sportTypeId) {
        syncVenue(venueId);
        return sessionSlotMapper.selectVenueDay(venueId, date, sportTypeId).stream().map(SessionSlotVO::from).toList();
    }

    /**
     * 功能描述: 创建不属于周模板的临时具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param body 临时场次创建请求
     * @return 已创建的具体场次
     * @author gjm
     */
    @Transactional
    @Override
    public SessionSlot createExtra(long venueId, ExtraSlotCreateRequest body) {
        requireSlotSelection(body == null ? null : body.getCourtId(), body == null ? null : body.getSportTypeId());
        Court court = requiredCourt(venueId, body.getCourtId());
        SportType sport = requiredSport(venueId, body.getSportTypeId(), null);
        requireSessionTime(body);

        if (!courtMapper.selectSportIds(court.id).contains(sport.id)) {
            throw badRequest("所选场地不支持该运动，请重新选择运动类型");
        }
        // 与周模板共用同一场地锁，确保临时场次也不能制造重叠库存。
        courtMapper.selectForUpdate(court.id);
        SessionSlot slot = session(venueId, null, court.id, sport.id, body.getDate(), body.getStartTime(), body.getEndTime(),
                body.getPriceFen());
        requireNoOverlap(slot);
        if (sessionSlotMapper.insertIgnore(slot) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
        }
        return slot;
    }

    /**
     * 功能描述: 编辑任意库存状态的具体场次并保留状态及预约关联。
     * 创建时间: 2026/09/11
     *
     * @param venueId 场馆 ID
     * @param sessionSlotId 待编辑的具体场次 ID
     * @param body 场次保存请求
     * @return 已更新的具体场次
     * @author gjm
     */
    @Override
    public SessionSlot updateSession(long venueId, long sessionSlotId, ExtraSlotCreateRequest body) {
        requireSlotSelection(body == null ? null : body.getCourtId(), body == null ? null : body.getSportTypeId());
        SessionSlot candidate = sessionSlotMapper.selectById(sessionSlotId);
        if (candidate == null || candidate.venueId != venueId) throw notFound();
        return redisLockService.execute(List.of(RedisLockType.SESSION_SLOT.key(sessionSlotId)),
                () -> updateSessionLocked(venueId, sessionSlotId, candidate.courtId, body));
    }

    /**
     * 功能描述: 在具体场次业务锁和数据库事务内编辑场次，并保留库存状态及预约关联。
     *
     * @param venueId 场馆 ID
     * @param sessionSlotId 具体场次 ID
     * @param body 场次保存请求
     * @return 更新后的具体场次
     * @author gjm
     */
    private SessionSlot updateSessionLocked(long venueId, long sessionSlotId, long originalCourtId,
                                            ExtraSlotCreateRequest body) {
        // 场地先按 ID 加锁，再锁具体场次，与预约及同价改期保持同一顺序。
        courtMapper.selectIdsForUpdate(new ArrayList<>(
                new java.util.TreeSet<>(List.of(originalCourtId, body.getCourtId()))));
        SessionSlot slot = sessionSlotMapper.selectForUpdate(sessionSlotId);
        if (slot == null || slot.venueId != venueId) {
            throw notFound();
        }
        if (slot.courtId != originalCourtId) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        Court court = courtMapper.selectById(body.getCourtId());
        if (court == null || court.venueId != venueId
                || (court.status != EnableStatus.ACTIVE && !court.id.equals(slot.courtId))) {
            throw notFound();
        }
        SportType sport = requiredSport(venueId, body.getSportTypeId(), slot.sportTypeId);
        requireSessionTime(body);
        if (body.getPriceFen() == null || body.getPriceFen() < 0) {
            throw badRequest("场次价格不能为空且不能小于 0");
        }
        if (!courtMapper.selectSportIds(court.id).contains(sport.id)) {
            throw badRequest("所选场地不支持该运动，请重新选择运动类型");
        }
        Instant startAt = body.getDate().atTime(body.getStartTime())
                .atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        Instant endAt = body.getDate().atTime(body.getEndTime())
                .atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        if (sessionSlotMapper.countOverlappingExcludingSlot(court.id, startAt, endAt, slot.id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
        }
        requireNoBookingOverlap(court.id, startAt, endAt, slot.id);
        // 人工编辑保留库存状态和预约关联，但不再允许后续周模板同步覆盖。
        slot.courtId = court.id;
        slot.sportTypeId = sport.id;
        slot.manualOverride = true;
        slot.slotDate = body.getDate();
        slot.startAt = startAt;
        slot.endAt = endAt;
        slot.priceFen = body.getPriceFen();
        slot.updatedAt = LocalDateTime.now();
        sessionSlotMapper.updateById(slot);
        return slot;
    }

    /**
     * 功能描述: 关闭一条未被占用的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void closeSlot(long venueId, long sessionSlotId) {
        redisLockService.execute(List.of(RedisLockType.SESSION_SLOT.key(sessionSlotId)), () -> {
            closeSlotLocked(venueId, sessionSlotId);
            return null;
        });
    }

    /**
     * 功能描述: 在具体场次业务锁和数据库事务内关闭未被占用的场次。
     *
     * @param venueId 场馆 ID
     * @param sessionSlotId 具体场次 ID
     * @author gjm
     */
    private void closeSlotLocked(long venueId, long sessionSlotId) {
        SessionSlot slot = sessionSlotMapper.selectForUpdate(sessionSlotId);
        if (slot == null || slot.venueId != venueId) {
            throw notFound();
        }
        if (slot.status != SessionSlotStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_BUSY);
        }
        slot.status = SessionSlotStatus.CLOSED;
        slot.updatedAt = LocalDateTime.now();
        sessionSlotMapper.updateById(slot);
    }

    /**
     * 功能描述: 重新开启一条未来且未冲突的已关闭具体场次。
     * 创建时间: 2026/09/03 10:20
     *
     * @author gjm
     */
    @Override
    public void openSlot(long venueId, long sessionSlotId) {
        redisLockService.execute(List.of(RedisLockType.SESSION_SLOT.key(sessionSlotId)), () -> {
            openSlotLocked(venueId, sessionSlotId);
            return null;
        });
    }

    /**
     * 功能描述: 在具体场次业务锁和数据库事务内重新开放未来且不重叠的场次。
     *
     * @param venueId 场馆 ID
     * @param sessionSlotId 具体场次 ID
     * @author gjm
     */
    private void openSlotLocked(long venueId, long sessionSlotId) {
        SessionSlot existing = sessionSlotMapper.selectById(sessionSlotId);
        if (existing == null || existing.venueId != venueId) {
            throw notFound();
        }
        // 与新增、同步场次使用相同的场地锁，避免并发开启出重叠库存。
        courtMapper.selectForUpdate(existing.courtId);
        SessionSlot slot = sessionSlotMapper.selectForUpdate(sessionSlotId);
        if (slot == null || slot.venueId != venueId) {
            throw notFound();
        }
        if (!slot.courtId.equals(existing.courtId)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        if (slot.status != SessionSlotStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_BUSY);
        }
        if (!slot.startAt.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
        }
        if (slot.weeklySlotId != null && !Boolean.TRUE.equals(slot.manualOverride)) {
            WeeklySlot weekly = weeklySlotMapper.selectById(slot.weeklySlotId);
            if (weekly == null || weekly.status != EnableStatus.ACTIVE) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
            }
        }
        if (sessionSlotMapper.countOverlappingExcludingSlot(
                slot.courtId, slot.startAt, slot.endAt, slot.id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
        }
        requireNoBookingOverlap(slot.courtId, slot.startAt, slot.endAt, null);
        slot.status = SessionSlotStatus.OPEN;
        slot.updatedAt = LocalDateTime.now();
        sessionSlotMapper.updateById(slot);
        if (reminderService != null) reminderService.onSlotReleased(slot.id,
                "manual:" + java.util.UUID.randomUUID());
    }

    /**
     * 功能描述: 关闭指定日期全部未被占用的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param date 关闭日期，已有占位或预约时拒绝整日关闭
     * @author gjm
     */
    @Transactional
    @Override
    public void closeDay(long venueId, LocalDate date) {
        // 与创建预约共用场地锁，等待结束后再读取最新占用，避免封场检查与写入交错。
        courtMapper.selectVenueForUpdate(venueId);
        if (sessionSlotMapper.countBusyForDate(venueId, date) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DAY_HAS_BOOKINGS);
        }
        sessionSlotMapper.closeOpenForDate(venueId, date);
    }

    /**
     * 功能描述: 将场馆有效周模板同步到未来预约窗口。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Transactional
    @Override
    public void syncVenue(long venueId) {
        // 先锁定本馆物理场地再建立查询视图，防止等待期间继续使用旧模板或旧快照。
        courtMapper.selectVenueForUpdate(venueId);
        Venue venue = venueService.getVenue(venueId);
        List<WeeklySlot> weeklies = weeklySlotMapper.selectByVenue(venueId, EnableStatus.ACTIVE);
        LocalDate firstDate = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        List<Booking> snapshots = loadWindowSnapshots(venue, weeklies, firstDate);
        for (WeeklySlot weekly : weeklies) {
            syncTemplate(venue, weekly, firstDate, snapshots);
        }
    }

    /**
     * 功能描述: 定时同步全部已上架场馆的未来场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Scheduled(cron = "0 5 0 * * *", zone = BusinessConstants.BUSINESS_ZONE_ID)
    @Override
    public void syncAllActiveVenues() {
        // 只同步已上架场馆；单机场景下按日生成未来窗口足够稳定。
        for (Venue venue : venueService.listPlatformVenues()) {
            if (venue.status == VenueStatus.ACTIVE) {
                redisLockService.execute(List.of(), () -> {
                    syncVenue(venue.id);
                    return null;
                });
            }
        }
    }

    /**
     * 功能描述: 预约取消或超时后释放具体场次占用。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public void releaseBookingSlot(long sessionSlotId) {
        SessionSlot slot = sessionSlotMapper.selectForUpdate(sessionSlotId);
        if (slot == null) {
            return;
        }
        SessionSlotStatus previousStatus = slot.status;
        boolean templateActive = slot.weeklySlotId == null || Boolean.TRUE.equals(slot.manualOverride);
        if (slot.weeklySlotId != null && !Boolean.TRUE.equals(slot.manualOverride)) {
            WeeklySlot weekly = weeklySlotMapper.selectById(slot.weeklySlotId);
            templateActive = weekly != null && weekly.status == EnableStatus.ACTIVE;
        }
        // 已停用模板或已经开始的场次保持关闭，避免取消后重新产生无效库存。
        slot.status = templateActive && slot.startAt.isAfter(Instant.now())
                ? SessionSlotStatus.OPEN : SessionSlotStatus.CLOSED;
        slot.holdBookingId = null;
        slot.holdExpiresAt = null;
        slot.updatedAt = LocalDateTime.now();
        sessionSlotMapper.clearBookingHold(slot);
        if (reminderService != null && previousStatus != SessionSlotStatus.OPEN
                && slot.status == SessionSlotStatus.OPEN) reminderService.onSlotReleased(slot.id,
                        "release:" + java.util.UUID.randomUUID());
    }

    /**
     * 功能描述: 将一条启用周模板同步到场馆未来预约窗口，只更新仍为 OPEN 的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private void syncTemplate(Venue venue, WeeklySlot weekly, LocalDate firstDate, List<Booking> snapshots) {
        LocalDate lastDate = firstDate.plusDays(venue.bookingDaysAhead - 1L);
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() != weekly.dayOfWeek) {
                continue;
            }
            SessionSlot existing = sessionSlotMapper.selectTemplateForUpdate(weekly.id, date);
            // 原生成日已有人工覆盖或被占用/关闭的场次时，不能复制第二份同模板库存。
            if (existing != null && (Boolean.TRUE.equals(existing.manualOverride)
                    || existing.status != SessionSlotStatus.OPEN)) {
                continue;
            }
            SessionSlot slot = session(venue.id, weekly.id, weekly.courtId, weekly.sportTypeId, date,
                    weekly.startTime, weekly.endTime, weekly.priceFen);
            // 已持有场地锁，使用窗口内一次当前读取的快照；日期循环不重复查询预约。
            boolean snapshotOverlap = snapshots.stream().anyMatch(booking -> booking.courtId.equals(slot.courtId)
                    && booking.startAt.isBefore(slot.endAt) && booking.endAt.isAfter(slot.startAt));
            if (snapshotOverlap) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
            }
            if (existing == null) {
                if (sessionSlotMapper.countOverlapping(slot.courtId, slot.startAt, slot.endAt, null) > 0) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
                }
            } else {
                if (sessionSlotMapper.countOverlappingExcludingSlot(
                        slot.courtId, slot.startAt, slot.endAt, existing.id) > 0) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
                }
            }
            if (sessionSlotMapper.updateOpenForTemplate(slot) == 0) {
                sessionSlotMapper.insertIgnore(slot);
            }
        }
    }

    /** 持有物理场地锁后一次当前读取生成窗口，起止日期与模板循环共用以避免跨午夜偏移。 */
    private List<Booking> loadWindowSnapshots(Venue venue, List<WeeklySlot> weeklies, LocalDate firstDate) {
        if (weeklies.isEmpty() || venue.bookingDaysAhead <= 0) return List.of();
        List<Long> courtIds = weeklies.stream().map(weekly -> weekly.courtId).distinct().sorted().toList();
        Instant startAt = firstDate.atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        Instant endAt = firstDate.plusDays(venue.bookingDaysAhead)
                .atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        return bookingMapper.selectActiveSnapshotsForUpdate(courtIds, startAt, endAt);
    }

    /**
     * 功能描述: 校验具体场次不与同一物理场地的可售库存重叠。
     * 创建时间: 2026/08/28 16:29
     *
     * @param slot 待创建或同步的具体场次
     * @author gjm
     */
    private void requireNoOverlap(SessionSlot slot) {
        requireNoBookingOverlap(slot.courtId, slot.startAt, slot.endAt, null);
        if (sessionSlotMapper.countOverlapping(
                slot.courtId, slot.startAt, slot.endAt, null) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
        }
    }

    /** 已有预约快照继续保护原时段；编辑自身场次仅排除其关联快照，不改变该快照。 */
    private void requireNoBookingOverlap(long courtId, Instant startAt, Instant endAt, Long excludedSessionSlotId) {
        if (!bookingMapper.selectOverlappingActiveForUpdate(
                courtId, startAt, endAt, null, excludedSessionSlotId).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
        }
    }

    /**
     * 功能描述: 按郑州业务时区构造一条具体场次实体。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param weeklyId 来源周场次 ID，临时场次为空
     * @param courtId 场地 ID
     * @param sportId 运动类型 ID
     * @param date 场次日期
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param priceFen 价格，单位为分
     * @return 待保存的具体场次
     * @author gjm
     */
    private SessionSlot session(long venueId, Long weeklyId, long courtId, long sportId, LocalDate date,
                                LocalTime startTime, LocalTime endTime, Integer priceFen) {
        if (priceFen == null || priceFen < 0) {
            throw badRequest("场次价格不能为空且不能小于 0");
        }
        SessionSlot slot = new SessionSlot();
        slot.venueId = venueId;
        slot.courtId = courtId;
        slot.sportTypeId = sportId;
        slot.weeklySlotId = weeklyId;
        slot.manualOverride = false;
        slot.templateSlotDate = weeklyId == null ? null : date;
        slot.slotDate = date;
        slot.startAt = date.atTime(startTime).atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        slot.endAt = date.atTime(endTime).atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        slot.priceFen = priceFen;
        slot.status = SessionSlotStatus.OPEN;
        return slot;
    }

    /**
     * 功能描述: 查询周场次并通过关联场地校验所属场馆。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private WeeklySlot requiredWeekly(long venueId, long weeklySlotId) {
        WeeklySlot weekly = weeklySlotMapper.selectById(weeklySlotId);
        if (weekly == null) {
            throw notFound();
        }
        requiredCourt(venueId, weekly.courtId);
        return weekly;
    }

    /**
     * 功能描述: 查询当前场馆启用场地，防止跨场馆排期。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private Court requiredCourt(long venueId, long courtId) {
        Court court = courtMapper.selectById(courtId);
        if (court == null || court.venueId != venueId || court.status != EnableStatus.ACTIVE) {
            throw notFound();
        }
        return court;
    }

    /**
     * 功能描述: 查询平台或当前场馆可用的运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private SportType requiredSport(long venueId, long sportId, Long existingSportId) {
        SportType sport = sportTypeMapper.selectById(sportId);
        if (sport == null || (sport.status != EnableStatus.ACTIVE && !sport.id.equals(existingSportId))
                || sport.ownerVenueId != null && sport.ownerVenueId != venueId) {
            throw notFound();
        }
        return sport;
    }

    /**
     * 功能描述: 构造包含具体原因的参数错误。
     *
     * @param message 面向用户的错误说明
     * @return 参数错误异常
     * @author gjm
     */
    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, message, null);
    }

    /**
     * 在主键拆箱和查库前指出缺失的场地或运动。
     *
     * @param courtId 场地 ID
     * @param sportId 运动类型 ID
     * @author gjm
     */
    private void requireSlotSelection(Long courtId, Long sportId) {
        if (courtId == null || courtId <= 0) throw badRequest("请选择场地");
        if (sportId == null || sportId <= 0) throw badRequest("请选择运动类型");
    }

    /**
     * 校验新增和编辑具体场次共用的日期与时间规则。
     *
     * @param body 场次保存请求
     * @author gjm
     */
    private void requireSessionTime(ExtraSlotCreateRequest body) {
        if (body.getDate() == null) throw badRequest("请选择场次日期");
        if (body.getStartTime() == null) throw badRequest("请选择开始时间");
        if (body.getEndTime() == null) throw badRequest("请选择结束时间");
        if (!body.getStartTime().isBefore(body.getEndTime())) {
            throw badRequest("结束时间必须晚于开始时间，不能跨天");
        }
    }

    /**
     * 功能描述: 构造统一资源不存在错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

}
