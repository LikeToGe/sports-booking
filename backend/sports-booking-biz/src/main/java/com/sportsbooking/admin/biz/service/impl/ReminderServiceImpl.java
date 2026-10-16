package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.NotificationMapper;
import com.sportsbooking.admin.base.mapper.ReminderSubscriptionMapper;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.base.mapper.SlotReleaseEventMapper;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.ReminderSubscription;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.po.SlotReleaseEvent;
import com.sportsbooking.admin.base.po.UserAccount;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.ReminderSubscriptionRequest;
import com.sportsbooking.admin.biz.pojo.vo.ReminderSubscriptionVO;
import com.sportsbooking.admin.biz.service.ContentMessageService;
import com.sportsbooking.admin.biz.service.ReminderService;
import com.sportsbooking.admin.biz.service.VenueService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** 提醒调度只写站内消息，外部 Mock 投递由消息服务异步处理。 */
@Service
public class ReminderServiceImpl implements ReminderService {
    private static final String ACTIVE = "ACTIVE";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");

    private final ReminderSubscriptionMapper subscriptions;
    private final SlotReleaseEventMapper releaseEvents;
    private final SessionSlotMapper slots;
    private final NotificationMapper notifications;
    private final BookingMapper bookings;
    private final ContentMessageService messages;
    private final VenueService venues;
    private final VenueMapper venueMapper;
    private final UserAccountMapper users;

    public ReminderServiceImpl(ReminderSubscriptionMapper subscriptions, SlotReleaseEventMapper releaseEvents,
                               SessionSlotMapper slots, NotificationMapper notifications, BookingMapper bookings,
                               ContentMessageService messages, VenueService venues,
                               VenueMapper venueMapper, UserAccountMapper users) {
        this.subscriptions = subscriptions;
        this.releaseEvents = releaseEvents;
        this.slots = slots;
        this.notifications = notifications;
        this.bookings = bookings;
        this.messages = messages;
        this.venues = venues;
        this.venueMapper = venueMapper;
        this.users = users;
    }

    @Override
    @Transactional
    public ReminderSubscription subscribe(long userId, ReminderSubscriptionRequest request) {
        if (request == null || request.getVenueId() == null || request.getSportTypeId() == null
                || request.getTargetDate() == null || request.getStartTime() == null || request.getEndTime() == null
                || request.getType() == null || !request.getStartTime().isBefore(request.getEndTime())
                // 与场次检索保持分钟精度，落地时不能截秒改变登记范围。
                || request.getStartTime().getSecond() != 0 || request.getEndTime().getSecond() != 0
                || request.getStartTime().getNano() != 0 || request.getEndTime().getNano() != 0
                || !List.of("OPENING", "RELEASED").contains(request.getType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        UserAccount user = users.selectForUpdate(userId);
        if (user == null || user.status != EnableStatus.ACTIVE || user.deletedAt != null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.USER_DISABLED);
        }
        LocalDate today = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        if (request.getTargetDate().isBefore(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        Venue venue = venues.getVenue(request.getVenueId());
        if (venue.status != VenueStatus.ACTIVE || venueMapper.selectUsedSports(venue.id).stream()
                .noneMatch(sport -> sport.id.equals(request.getSportTypeId()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        int bookingDaysAhead = venue.bookingDaysAhead == null ? 7 : venue.bookingDaysAhead;
        // 当前窗口之外仅允许登记下一次开放的一天，不能订阅任意远期日期。
        if (request.getTargetDate().isAfter(today.plusDays(bookingDaysAhead))) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
        }
        if (!request.getTargetDate().atTime(request.getEndTime()).atZone(BusinessConstants.BUSINESS_ZONE)
                .toInstant().isAfter(Instant.now().plus(Duration.ofMinutes(venue.minBookingMinutes)))) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
        }
        ReminderSubscription row = new ReminderSubscription();
        row.userId = userId;
        row.venueId = request.getVenueId();
        row.sportTypeId = request.getSportTypeId();
        row.targetDate = request.getTargetDate();
        row.startTime = request.getStartTime();
        row.endTime = request.getEndTime();
        row.type = request.getType();
        row.status = ACTIVE;
        row.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        row.updatedAt = row.createdAt;
        row.hadOpenAtRegistration = !slots.selectOpenMatching(row.venueId, row.sportTypeId,
                row.targetDate, row.startTime, row.endTime).isEmpty();
        // 首次登记时已有可订库存只引导查看，不补造开售或释放事件。
        if ("OPENING".equals(row.type) && row.hadOpenAtRegistration) row.openingNotifiedAt = row.createdAt;
        try {
            subscriptions.insert(row);
        } catch (DuplicateKeyException duplicate) {
            ReminderSubscription existing = subscriptions.selectOne(new QueryWrapper<ReminderSubscription>()
                    .eq("user_id", userId).eq("venue_id", row.venueId).eq("sport_type_id", row.sportTypeId)
                    .eq("target_date", row.targetDate).eq("start_time", row.startTime)
                    .eq("end_time", row.endTime).eq("type", row.type).eq("status", ACTIVE));
            if (existing != null) return existing;
            throw duplicate;
        }
        return row;
    }

    @Override
    public List<ReminderSubscriptionVO> listSubscriptions(long userId) {
        return subscriptions.selectByUser(userId).stream().map(ReminderSubscriptionVO::from).toList();
    }

    @Override
    @Transactional
    public void cancelSubscription(long userId, long subscriptionId) {
        ReminderSubscription row = subscriptions.selectById(subscriptionId);
        if (row == null || !Long.valueOf(userId).equals(row.userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, com.sportsbooking.admin.base.enums.ErrorCode.NOT_FOUND);
        }
        subscriptions.cancelActive(subscriptionId, userId);
    }

    @Override
    @Transactional
    public void onSlotReleased(long sessionSlotId, String releaseKey) {
        SessionSlot slot = slots.selectById(sessionSlotId);
        if (slot == null || slot.status != SessionSlotStatus.OPEN || slot.startAt == null
                || !slot.startAt.isAfter(Instant.now())) return;
        String eventKey = "slot-release:" + slot.id + ":" + releaseKey;
        SlotReleaseEvent event = new SlotReleaseEvent();
        event.sessionSlotId = slot.id;
        event.eventKey = eventKey;
        releaseEvents.insertIdempotent(event);
    }

    @Override
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void runReminderJobs() {
        subscriptions.expirePast();
        notifications.insertDueArrivalAlerts();
        // 集合 SQL 在独立调度事务中读取已提交供给；重复运行依靠消息业务键去重。
        notifications.insertReadyOpeningAlerts();
        subscriptions.markOpeningNotifiedForMessages();
        List<Long> releaseIds = releaseEvents.selectUnprocessedIds(200);
        if (!releaseIds.isEmpty()) {
            notifications.insertReleasedAlerts(releaseIds);
            releaseEvents.markProcessed(releaseIds);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateArrivalIfDue(long bookingId) {
        Booking booking = bookings.selectById(bookingId);
        if (booking != null) recordArrival(booking);
    }

    /** 即时补发和固定调度共用相同预约标识，插入 SQL 再复核最新状态。 */
    private void recordArrival(Booking booking) {
        Instant now = Instant.now();
        if (booking.source != BookingSource.ONLINE || booking.status != BookingStatus.CONFIRMED
                || booking.userId == null || booking.startAt == null || !booking.startAt.isAfter(now)
                || booking.startAt.isAfter(now.plus(Duration.ofHours(2)))) return;
        messages.recordArrivalIfEligible(booking.id, "到场提醒",
                "你的 " + booking.venueName + " 预约将于 "
                        + TIME.format(booking.startAt.atZone(BusinessConstants.BUSINESS_ZONE))
                        + " 开始，请打开预约详情查看导航、入场须知和核销凭证。");
    }
}
