package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.biz.pojo.request.ExtraSlotCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.WeeklySlotSaveRequest;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.po.WeeklySlot;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.base.mapper.WeeklySlotMapper;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.biz.pojo.vo.SessionSlotVO;
import com.sportsbooking.admin.biz.service.VenueService;
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

    public ScheduleServiceImpl(WeeklySlotMapper weeklySlotMapper, SessionSlotMapper sessionSlotMapper,
                           CourtMapper courtMapper, SportTypeMapper sportTypeMapper, VenueService venueService) {
        this.weeklySlotMapper = weeklySlotMapper;
        this.sessionSlotMapper = sessionSlotMapper;
        this.courtMapper = courtMapper;
        this.sportTypeMapper = sportTypeMapper;
        this.venueService = venueService;
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
     * @author gjm
     */
    @Transactional
    @Override
    public WeeklySlot saveWeekly(long venueId, Long weeklySlotId, WeeklySlotSaveRequest body) {
        Court court = requiredCourt(venueId, body.getCourtId());
        SportType sport = requiredSport(venueId, body.getSportTypeId());
        if (body.getDayOfWeek() == null || body.getDayOfWeek() < 1 || body.getDayOfWeek() > 7) {
            throw badRequest();
        }
        if (body.getStartTime() == null || body.getEndTime() == null || !body.getStartTime().isBefore(body.getEndTime())) {
            throw badRequest();
        }
        if (body.getPriceFen() == null || body.getPriceFen() < 0) {
            throw badRequest();
        }
        if (!courtMapper.selectSportIds(court.id).contains(sport.id)) {
            throw badRequest();
        }
        // 锁定物理场地后检查时间区间，防止并发创建两个互相重叠的库存。
        courtMapper.selectForUpdate(court.id);
        if (weeklySlotMapper.countOverlapping(court.id, body.getDayOfWeek(), body.getStartTime(),
                body.getEndTime(), weeklySlotId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
        }

        WeeklySlot weekly = weeklySlotId == null ? new WeeklySlot() : requiredWeekly(venueId, weeklySlotId);
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
        syncTemplate(venueService.getVenue(venueId), weekly);
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
        WeeklySlot weekly = requiredWeekly(venueId, weeklySlotId);
        weekly.status = EnableStatus.DISABLED;
        weeklySlotMapper.updateById(weekly);
        sessionSlotMapper.closeOpenForTemplate(weekly.id, Instant.now());
    }

    /**
     * 功能描述: 将源星期的有效模板复制到目标星期。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Transactional
    @Override
    public List<WeeklySlot> copyDay(long venueId, int sourceDay, List<Integer> targetDays) {
        if (sourceDay < 1 || sourceDay > 7 || targetDays == null || targetDays.isEmpty()) {
            throw badRequest();
        }
        List<WeeklySlot> source = weeklySlotMapper.selectByVenue(venueId, EnableStatus.ACTIVE).stream()
                .filter(slot -> slot.dayOfWeek == sourceDay)
                .toList();
        if (source.isEmpty()) {
            throw badRequest();
        }
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
                syncTemplate(venueService.getVenue(venueId), copiedSlot);
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
    public List<SessionSlotVO> listDay(long venueId, LocalDate date) {
        syncVenue(venueId);
        return sessionSlotMapper.selectVenueDay(venueId, date).stream().map(SessionSlotVO::from).toList();
    }

    /**
     * 功能描述: 创建不属于周模板的临时具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Transactional
    @Override
    public SessionSlot createExtra(long venueId, ExtraSlotCreateRequest body) {
        Court court = requiredCourt(venueId, body.getCourtId());
        SportType sport = requiredSport(venueId, body.getSportTypeId());
        if (body.getDate() == null || body.getStartTime() == null || body.getEndTime() == null
                || !body.getStartTime().isBefore(body.getEndTime())) {
            throw badRequest();
        }
        if (!courtMapper.selectSportIds(court.id).contains(sport.id)) {
            throw badRequest();
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
     * 功能描述: 关闭一条未被占用的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Transactional
    @Override
    public void closeSlot(long venueId, long sessionSlotId) {
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
    @Transactional
    @Override
    public void openSlot(long venueId, long sessionSlotId) {
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
        if (slot.status != SessionSlotStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_BUSY);
        }
        if (!slot.startAt.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
        }
        if (slot.weeklySlotId != null) {
            WeeklySlot weekly = weeklySlotMapper.selectById(slot.weeklySlotId);
            if (weekly == null || weekly.status != EnableStatus.ACTIVE) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
            }
        }
        requireNoOverlap(slot);
        slot.status = SessionSlotStatus.OPEN;
        slot.updatedAt = LocalDateTime.now();
        sessionSlotMapper.updateById(slot);
    }

    /**
     * 功能描述: 关闭指定日期全部未被占用的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Transactional
    @Override
    public void closeDay(long venueId, LocalDate date) {
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
        Venue venue = venueService.getVenue(venueId);
        for (WeeklySlot weekly : weeklySlotMapper.selectByVenue(venueId, EnableStatus.ACTIVE)) {
            syncTemplate(venue, weekly);
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
                syncVenue(venue.id);
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
        boolean templateActive = slot.weeklySlotId == null;
        if (slot.weeklySlotId != null) {
            WeeklySlot weekly = weeklySlotMapper.selectById(slot.weeklySlotId);
            templateActive = weekly != null && weekly.status == EnableStatus.ACTIVE;
        }
        // 已停用模板或已经开始的场次保持关闭，避免取消后重新产生无效库存。
        slot.status = templateActive && slot.startAt.isAfter(Instant.now())
                ? SessionSlotStatus.OPEN : SessionSlotStatus.CLOSED;
        slot.holdBookingId = null;
        slot.holdExpiresAt = null;
        slot.updatedAt = LocalDateTime.now();
        sessionSlotMapper.updateById(slot);
    }

    /**
     * 功能描述: 将一条启用周模板同步到场馆未来预约窗口，只更新仍为 OPEN 的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private void syncTemplate(Venue venue, WeeklySlot weekly) {
        LocalDate firstDate = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        LocalDate lastDate = firstDate.plusDays(venue.bookingDaysAhead - 1L);
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() != weekly.dayOfWeek) {
                continue;
            }
            SessionSlot slot = session(venue.id, weekly.id, weekly.courtId, weekly.sportTypeId, date,
                    weekly.startTime, weekly.endTime, weekly.priceFen);
            requireNoOverlap(slot);
            if (sessionSlotMapper.updateOpenForTemplate(slot) == 0) {
                sessionSlotMapper.insertIgnore(slot);
            }
        }
    }

    /**
     * 功能描述: 校验具体场次不与同一物理场地的可售库存重叠。
     * 创建时间: 2026/08/28 16:29
     *
     * @param slot 待创建或同步的具体场次
     * @author gjm
     */
    private void requireNoOverlap(SessionSlot slot) {
        if (sessionSlotMapper.countOverlapping(
                slot.courtId, slot.startAt, slot.endAt, slot.weeklySlotId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_CONFLICT);
        }
    }

    /**
     * 功能描述: 按郑州业务时区构造一条具体场次实体。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private SessionSlot session(long venueId, Long weeklyId, long courtId, long sportId, LocalDate date,
                                LocalTime startTime, LocalTime endTime, Integer priceFen) {
        if (priceFen == null || priceFen < 0) {
            throw badRequest();
        }
        SessionSlot slot = new SessionSlot();
        slot.venueId = venueId;
        slot.courtId = courtId;
        slot.sportTypeId = sportId;
        slot.weeklySlotId = weeklyId;
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
    private SportType requiredSport(long venueId, long sportId) {
        SportType sport = sportTypeMapper.selectById(sportId);
        if (sport == null || sport.status != EnableStatus.ACTIVE
                || sport.ownerVenueId != null && sport.ownerVenueId != venueId) {
            throw notFound();
        }
        return sport;
    }

    /**
     * 功能描述: 构造统一参数错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private ApiException badRequest() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
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
