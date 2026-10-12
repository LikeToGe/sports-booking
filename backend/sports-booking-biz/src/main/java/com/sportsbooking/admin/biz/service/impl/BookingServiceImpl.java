package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.po.UserAccount;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.BookingPaymentStatusLog;
import com.sportsbooking.admin.base.po.BookingRescheduleLog;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.po.Refund;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.BookingPaymentStatusLogMapper;
import com.sportsbooking.admin.base.mapper.BookingRescheduleLogMapper;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.service.PaymentService;
import com.sportsbooking.admin.biz.pojo.vo.BookingDetailVO;
import com.sportsbooking.admin.biz.pojo.vo.BookingVO;
import com.sportsbooking.admin.biz.pojo.vo.CredentialVO;
import com.sportsbooking.admin.biz.pojo.vo.PaymentVO;
import com.sportsbooking.admin.biz.pojo.vo.RefundVO;
import com.sportsbooking.admin.biz.pojo.vo.SignedCredentialVO;
import com.sportsbooking.admin.biz.pojo.vo.UserBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.OperationReportDailyVO;
import com.sportsbooking.admin.biz.pojo.vo.OperationReportVO;
import com.sportsbooking.admin.base.bo.OperationReportDailyBO;
import com.sportsbooking.admin.base.bo.ServiceFeeBookingBO;
import com.sportsbooking.admin.biz.pojo.vo.ServiceFeeBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.ServiceFeeSettlementVO;
import com.sportsbooking.admin.biz.pojo.vo.ServiceFeeVenueSummaryVO;
import com.sportsbooking.admin.biz.constants.AuthConstants;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.biz.constants.RedisLockType;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.CancelledBy;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.VenueCustomerService;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.service.NotificationService;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.base.po.VenueCustomer;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能描述: 用户和场馆预约、模拟支付退款及预约查询业务实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class BookingServiceImpl implements BookingService {
    private final BookingMapper bookingMapper;
    private final BookingPaymentStatusLogMapper bookingPaymentStatusLogMapper;
    private final BookingRescheduleLogMapper bookingRescheduleLogMapper;
    private final SessionSlotMapper sessionSlotMapper;
    private final UserAccountMapper userAccountMapper;
    private final CourtMapper courtMapper;
    private final SportTypeMapper sportTypeMapper;
    private final VenueService venueService;
    private final VenueCustomerService venueCustomerService;
    private final MemberFundsService memberFundsService;
    private final NotificationService notificationService;
    private final CredentialService credentialService;
    private final PaymentService paymentService;
    private final ScheduleService scheduleService;
    private final RedisLockService redisLockService;
    private final StringRedisTemplate redisTemplate;
    private final int paymentTimeoutMinutes;
    private final int pendingLimit;
    private final int createLimit;

    public BookingServiceImpl(BookingMapper bookingMapper,
                          BookingPaymentStatusLogMapper bookingPaymentStatusLogMapper,
                          BookingRescheduleLogMapper bookingRescheduleLogMapper,
                          SessionSlotMapper sessionSlotMapper,
                          UserAccountMapper userAccountMapper,
                          CourtMapper courtMapper, SportTypeMapper sportTypeMapper, VenueService venueService,
                          VenueCustomerService venueCustomerService, MemberFundsService memberFundsService,
                          NotificationService notificationService,
                          CredentialService credentialService, PaymentService paymentService,
                          ScheduleService scheduleService, RedisLockService redisLockService,
                          StringRedisTemplate redisTemplate,
                          @Value("${app.booking.payment-timeout-minutes}") int paymentTimeoutMinutes,
                          @Value("${app.booking.pending-limit}") int pendingLimit,
                          @Value("${app.booking.create-limit-per-ten-minutes}") int createLimit) {
        this.bookingMapper = bookingMapper;
        this.bookingPaymentStatusLogMapper = bookingPaymentStatusLogMapper;
        this.bookingRescheduleLogMapper = bookingRescheduleLogMapper;
        this.sessionSlotMapper = sessionSlotMapper;
        this.userAccountMapper = userAccountMapper;
        this.courtMapper = courtMapper;
        this.sportTypeMapper = sportTypeMapper;
        this.venueService = venueService;
        this.venueCustomerService = venueCustomerService;
        this.memberFundsService = memberFundsService;
        this.notificationService = notificationService;
        this.credentialService = credentialService;
        this.paymentService = paymentService;
        this.scheduleService = scheduleService;
        this.redisLockService = redisLockService;
        this.redisTemplate = redisTemplate;
        this.paymentTimeoutMinutes = paymentTimeoutMinutes;
        this.pendingLimit = pendingLimit;
        this.createLimit = createLimit;
    }

    /**
     * 功能描述: 创建线上待支付预约并独占具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Booking createOnline(long userId, long sessionSlotId, String contactName, String contactPhone) {
        return createOnline(userId, sessionSlotId, contactName, contactPhone, null);
    }

    @Override
    public Booking createOnline(long userId, long sessionSlotId, String contactName, String contactPhone,
                                String idempotencyKey) {
        String clientKey = idempotencyKey == null || idempotencyKey.trim().isEmpty() ? null : idempotencyKey.trim();
        if (clientKey != null) {
            Booking existing = bookingMapper.selectOne(new QueryWrapper<Booking>()
                    .eq("user_id", userId).eq("client_idempotency_key", clientKey));
            if (existing != null) {
                return existing;
            }
        }
        VenueCustomer venueCustomer = venueCustomerService.ensureCustomer(
                sessionSlotVenueId(sessionSlotId), userId);
        Booking created = redisLockService.execute(List.of(RedisLockType.SESSION_SLOT.key(sessionSlotId),
                RedisLockType.MEMBER_BALANCE.key(venueCustomer.id)), () -> {
            // 与注销共用用户行锁，已认证但尚未写入的并发请求不能复活已注销账号。
            UserAccount user = userAccountMapper.selectForUpdate(userId);
            if (user == null || user.status != com.sportsbooking.admin.base.enums.EnableStatus.ACTIVE
                    || user.deletedAt != null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.USER_DISABLED);
            }
            if (clientKey != null) {
                Booking existing = bookingMapper.selectOne(new QueryWrapper<Booking>()
                        .eq("user_id", userId).eq("client_idempotency_key", clientKey));
                if (existing != null) {
                    return existing;
                }
            }
            enforceCreateLimit(userId);
            Long pending = bookingMapper.selectCount(new QueryWrapper<Booking>()
                    .eq("user_id", userId).eq("status", BookingStatus.PENDING_PAYMENT));
            if (pending >= pendingLimit) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PENDING_LIMIT);
            }
            SessionSlot slot = sessionSlotMapper.selectForUpdate(sessionSlotId);
            Instant now = Instant.now();
            if (slot == null || !SessionSlotStatus.OPEN.equals(slot.status)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
            }
            Venue venue = venueService.getVenue(slot.venueId);
            // 场馆规则必须在服务端再次校验，不能依赖详情页隐藏临近开场的场次。
            if (!slot.startAt.isAfter(now.plus(Duration.ofMinutes(venue.minBookingMinutes)))) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
            }
            Court court = courtMapper.selectById(slot.courtId);
            SportType sport = sportTypeMapper.selectById(slot.sportTypeId);
            String name = requireText(contactName);
            String phone = requireText(contactPhone);

            Booking booking = baseBooking(slot, venue, court, sport, name, phone);
            booking.userId = userId;
            booking.venueCustomerId = venueCustomer.id;
            booking.source = BookingSource.ONLINE;
            // 保存创建时费率，后续场馆调价不得改变已有预约的计费口径。
            booking.serviceFeeRateBasisPoints = venue.serviceFeeRateBasisPoints == null
                    ? 0 : venue.serviceFeeRateBasisPoints;
            booking.status = BookingStatus.PENDING_PAYMENT;
            booking.paymentStatus = BookingPaymentStatus.PAYING;
            booking.paymentExpiresAt = Instant.now().plus(Duration.ofMinutes(paymentTimeoutMinutes));
            booking.balanceAmountFen = 0;
            booking.giftBalanceAmountFen = 0;
            booking.paidBalanceAmountFen = 0;
            booking.externalAmountFen = booking.amountFen;
            booking.clientIdempotencyKey = clientKey;
            bookingMapper.insert(booking);

            // 预约和具体场次在同一事务内写入；行锁保证并发请求只有一个能从 OPEN 转为 HELD。
            slot.status = SessionSlotStatus.HELD;
            slot.holdBookingId = booking.id;
            slot.holdExpiresAt = booking.paymentExpiresAt;
            sessionSlotMapper.updateById(slot);

            memberFundsService.freeze(booking, venueCustomer.id, now);
            if (booking.externalAmountFen == 0) {
                memberFundsService.consume(booking.id, now);
                booking.status = BookingStatus.CONFIRMED;
                booking.paymentStatus = BookingPaymentStatus.PAID;
                booking.paymentExpiresAt = null;
                slot.status = SessionSlotStatus.BOOKED;
                slot.holdExpiresAt = null;
                sessionSlotMapper.updateById(slot);
                notificationService.record(booking, com.sportsbooking.admin.base.enums.NotificationType.BOOKING_CONFIRMED);
            } else {
                paymentService.createPending(booking);
            }
            booking.updatedAt = LocalDateTime.now();
            bookingMapper.updateById(booking);

            if (user != null) {
                user.contactName = name;
                user.phone = phone;
                user.updatedAt = LocalDateTime.now();
                userAccountMapper.updateById(user);
            }
            return booking;
        });
        if (created.id != null && created.externalAmountFen != null && created.externalAmountFen > 0) {
            Booking started = paymentService.startPayment(userId, created.id);
            return started == null ? created : started;
        }
        return created;
    }

    @Override
    public Booking createOffline(long venueId, long sessionSlotId, String contactName, String contactPhone,
                                 BookingPaymentStatus paymentStatus, Long venueCustomerId) {
        validateOfflinePaymentStatus(paymentStatus);
        validateVenueCustomer(venueId, venueCustomerId);
        return redisLockService.execute(List.of(RedisLockType.SESSION_SLOT.key(sessionSlotId)), () -> {
            SessionSlot slot = sessionSlotMapper.selectForUpdate(sessionSlotId);
            if (slot == null || slot.venueId != venueId || !SessionSlotStatus.OPEN.equals(slot.status)
                    || !slot.startAt.isAfter(Instant.now())) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
            }
            Venue venue = venueService.getVenue(venueId);
            // 线下预约同样遵守场馆最晚预约规则，避免后台绕过对用户端的库存保护。
            if (!slot.startAt.isAfter(Instant.now().plus(Duration.ofMinutes(venue.minBookingMinutes)))) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
            }
            return createOfflineLocked(slot, venue, courtMapper.selectById(slot.courtId),
                    sportTypeMapper.selectById(slot.sportTypeId), requireText(contactName),
                    requireText(contactPhone), paymentStatus, venueCustomerId);
        });
    }

    /**
     * 功能描述: 对日期去重后整批解析、锁定并创建线下预约。
     *
     * @author gjm
     */
    @Override
    public List<Booking> createOfflineBatch(long venueId, long templateSessionSlotId, List<LocalDate> dates,
                                            String contactName, String contactPhone,
                                            BookingPaymentStatus paymentStatus, Long venueCustomerId) {
        List<LocalDate> uniqueDates = normalizeDates(dates);
        String name = requireText(contactName);
        String phone = requireText(contactPhone);
        validateOfflinePaymentStatus(paymentStatus);
        validateVenueCustomer(venueId, venueCustomerId);

        SessionSlot template = sessionSlotMapper.selectById(templateSessionSlotId);
        if (template == null || template.venueId != venueId) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        Venue venue = venueService.getVenue(venueId);
        List<SessionSlot> resolvedSlots = bookingMapper.selectTemplateSlots(templateSessionSlotId, uniqueDates);
        List<LocalDate> conflictDates = findConflictDates(uniqueDates, resolvedSlots, venue, Instant.now());
        if (!conflictDates.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BATCH_SLOT_CONFLICT, conflictDates);
        }

        List<Long> slotIds = new ArrayList<>(new TreeSet<>(resolvedSlots.stream().map(slot -> slot.id).toList()));
        List<String> lockKeys = slotIds.stream().map(RedisLockType.SESSION_SLOT::key).toList();
        return redisLockService.execute(lockKeys, () -> {
            List<SessionSlot> lockedSlots = bookingMapper.selectSlotsForUpdate(slotIds);
            List<LocalDate> lockedConflicts = findConflictDates(uniqueDates, lockedSlots, venue, Instant.now());
            if (!lockedConflicts.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BATCH_SLOT_CONFLICT, lockedConflicts);
            }

            Court court = courtMapper.selectById(template.courtId);
            SportType sport = sportTypeMapper.selectById(template.sportTypeId);
            Map<LocalDate, SessionSlot> slotsByDate = slotsByDate(lockedSlots);
            List<Booking> bookings = new ArrayList<>();
            for (LocalDate date : uniqueDates) {
                bookings.add(createOfflineLocked(slotsByDate.get(date), venue, court, sport, name, phone,
                        paymentStatus, venueCustomerId));
            }
            return bookings;
        });
    }

    /**
     * 功能描述: 修改任意状态线下预约的联系人资料。
     * 创建时间: 2026/09/11
     *
     * @author gjm
     */
    @Override
    public Booking updateOfflineContact(long venueId, long bookingId, String contactName, String contactPhone) {
        return redisLockService.execute(List.of(RedisLockType.BOOKING.key(bookingId)), () -> {
            Booking booking = requiredLockedBooking(bookingId);
            if (booking.venueId != venueId || booking.source != BookingSource.OFFLINE) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
            }
            booking.contactName = requireText(contactName);
            booking.contactPhone = requireText(contactPhone);
            booking.updatedAt = LocalDateTime.now();
            bookingMapper.updateById(booking);
            return booking;
        });
    }

    /**
     * 功能描述: 按有限状态机修改线下登记收款状态并写审计日志。
     *
     * @author gjm
     */
    @Override
    public Booking updateOfflinePaymentStatus(long venueId, Long operatorAdminId, Long operatorEmployeeId,
                                              long bookingId,
                                              BookingPaymentStatus paymentStatus, String reason) {
        String normalizedReason = requireText(reason);
        return redisLockService.execute(List.of(RedisLockType.BOOKING.key(bookingId)), () -> {
            Booking booking = requiredLockedBooking(bookingId);
            if (booking.venueId != venueId || booking.source != BookingSource.OFFLINE) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
            }
            BookingPaymentStatus beforeStatus = booking.paymentStatus;
            if (!isAllowedOfflinePaymentTransition(booking.status, beforeStatus, paymentStatus)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
            }

            booking.paymentStatus = paymentStatus;
            booking.updatedAt = LocalDateTime.now();
            bookingMapper.updateById(booking);

            BookingPaymentStatusLog log = new BookingPaymentStatusLog();
            log.bookingId = booking.id;
            log.beforeStatus = beforeStatus;
            log.afterStatus = paymentStatus;
            log.operatorAdminId = operatorAdminId;
            log.operatorEmployeeId = operatorEmployeeId;
            log.reason = normalizedReason;
            log.createdAt = LocalDateTime.now();
            bookingPaymentStatusLogMapper.insert(log);
            return booking;
        });
    }

    /**
     * 功能描述: 将未开始的线下预约原子迁移至本场馆同价开放场次。
     *
     * @author gjm
     */
    @Override
    public Booking rescheduleOffline(long venueId, Long operatorAdminId, Long operatorEmployeeId, long bookingId,
                                     long sessionSlotId, String reason) {
        String normalizedReason = requireText(reason);
        Booking candidate = requiredBooking(bookingId);
        List<String> lockKeys = List.of(RedisLockType.BOOKING.key(bookingId),
                RedisLockType.SESSION_SLOT.key(candidate.sessionSlotId),
                RedisLockType.SESSION_SLOT.key(sessionSlotId));
        return redisLockService.execute(lockKeys, () -> {
            Booking booking = requiredLockedBooking(bookingId);
            requireUnchangedSessionSlot(booking, candidate.sessionSlotId);
            List<Long> slotIds = new ArrayList<>(new TreeSet<>(List.of(booking.sessionSlotId, sessionSlotId)));
            Map<Long, SessionSlot> slotsById = new LinkedHashMap<>();
            for (SessionSlot slot : bookingMapper.selectSlotsForUpdate(slotIds)) {
                slotsById.put(slot.id, slot);
            }
            SessionSlot originalSlot = slotsById.get(booking.sessionSlotId);
            SessionSlot targetSlot = slotsById.get(sessionSlotId);
            Instant now = Instant.now();
            if (booking.venueId != venueId || targetSlot == null || targetSlot.venueId != venueId) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
            }
            if (booking.source != BookingSource.OFFLINE || booking.status != BookingStatus.CONFIRMED
                    || !booking.startAt.isAfter(now) || originalSlot == null
                    || originalSlot.status != SessionSlotStatus.BOOKED
                    || !booking.id.equals(originalSlot.holdBookingId)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
            }
            if (targetSlot.status != SessionSlotStatus.OPEN || !targetSlot.startAt.isAfter(now)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
            }
            if (!booking.amountFen.equals(targetSlot.priceFen)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
            }

            // 复用既有释放规则；周模板已停用时原场次必须保持关闭。
            scheduleService.releaseBookingSlot(originalSlot.id);

            targetSlot.status = SessionSlotStatus.BOOKED;
            targetSlot.holdBookingId = booking.id;
            targetSlot.holdExpiresAt = null;
            sessionSlotMapper.updateById(targetSlot);

            long originalSessionSlotId = booking.sessionSlotId;
            Court court = courtMapper.selectById(targetSlot.courtId);
            SportType sport = sportTypeMapper.selectById(targetSlot.sportTypeId);
            booking.sessionSlotId = targetSlot.id;
            booking.courtId = targetSlot.courtId;
            booking.sportTypeId = targetSlot.sportTypeId;
            booking.courtName = court.name;
            booking.sportName = sport.name;
            booking.startAt = targetSlot.startAt;
            booking.endAt = targetSlot.endAt;
            booking.amountFen = targetSlot.priceFen;
            booking.updatedAt = LocalDateTime.now();
            bookingMapper.updateById(booking);

            BookingRescheduleLog log = new BookingRescheduleLog();
            log.bookingId = booking.id;
            log.originalSessionSlotId = originalSessionSlotId;
            log.newSessionSlotId = targetSlot.id;
            log.operatorAdminId = operatorAdminId;
            log.operatorEmployeeId = operatorEmployeeId;
            log.reason = normalizedReason;
            log.createdAt = LocalDateTime.now();
            bookingRescheduleLogMapper.insert(log);
            return booking;
        });
    }

    /**
     * 功能描述: 校验取消截止时间后取消用户预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Booking cancelByUser(long userId, long bookingId) {
        Booking candidate = requiredBooking(bookingId);
        requireOwner(candidate, userId);
        if (candidate.status == BookingStatus.PENDING_PAYMENT) {
            paymentService.queryPayment(userId, bookingId);
            candidate = requiredBooking(bookingId);
            if (candidate.status == BookingStatus.PENDING_PAYMENT) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
            }
            if (candidate.status == BookingStatus.CANCELLED) {
                return candidate;
            }
        }
        Booking lockedCandidate = candidate;
        Booking cancelled = redisLockService.execute(List.of(
                RedisLockType.BOOKING.key(bookingId), RedisLockType.SESSION_SLOT.key(candidate.sessionSlotId),
                RedisLockType.MEMBER_BALANCE.key(candidate.venueCustomerId)), () -> {
            Booking booking = requiredLockedBooking(bookingId);
            requireUnchangedSessionSlot(booking, lockedCandidate.sessionSlotId);
            requireOwner(booking, userId);
            Venue venue = venueService.getVenue(booking.venueId);
            Instant cutoff = booking.startAt.minus(Duration.ofMinutes(venue.cancelCutoffMinutes));
            if (!Instant.now().isBefore(cutoff)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CANCEL_CUTOFF);
            }
            return cancel(booking, CancelledBy.USER, "用户取消");
        });
        return finishExternalRefund(cancelled);
    }

    /**
     * 功能描述: 校验场馆归属和取消原因后取消预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Booking cancelByVenue(long venueId, long bookingId, String reason,
                                 boolean canCancelOnline, boolean canEditOffline) {
        Booking candidate = requiredBooking(bookingId);
        requireVenueCancelPermission(candidate.source, canCancelOnline, canEditOffline);
        if (candidate.source == BookingSource.ONLINE && candidate.status == BookingStatus.PENDING_PAYMENT) {
            paymentService.queryPayment(bookingId);
            candidate = requiredBooking(bookingId);
            if (candidate.status == BookingStatus.PENDING_PAYMENT) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
            }
            if (candidate.status == BookingStatus.CANCELLED) {
                return candidate;
            }
        }
        Booking lockedCandidate = candidate;
        List<String> keys = new ArrayList<>(List.of(
                RedisLockType.BOOKING.key(bookingId), RedisLockType.SESSION_SLOT.key(candidate.sessionSlotId)));
        if (candidate.venueCustomerId != null) {
            keys.add(RedisLockType.MEMBER_BALANCE.key(candidate.venueCustomerId));
        }
        Booking cancelled = redisLockService.execute(keys, () -> {
            Booking booking = requiredLockedBooking(bookingId);
            requireUnchangedSessionSlot(booking, lockedCandidate.sessionSlotId);
            if (booking.venueId != venueId) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
            }
            requireVenueCancelPermission(booking.source, canCancelOnline, canEditOffline);
            return cancel(booking, CancelledBy.VENUE, requireText(reason));
        });
        return finishExternalRefund(cancelled);
    }

    /** 线上取消与线下编辑是独立权限，必须按服务端预约来源判定。 */
    private void requireVenueCancelPermission(BookingSource source,
                                              boolean canCancelOnline, boolean canEditOffline) {
        if ((source == BookingSource.ONLINE && !canCancelOnline)
                || (source == BookingSource.OFFLINE && !canEditOffline)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 功能描述: 查询当前用户的全部预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<UserBookingVO> listUserBookings(long userId) {
        return bookingMapper.selectUserBookings(userId).stream().map(UserBookingVO::from).toList();
    }

    /**
     * 功能描述: 查询当前用户单笔预约详情和核销凭证。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public BookingDetailVO getUserBooking(long userId, long bookingId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null || booking.userId == null || booking.userId != userId) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        Payment payment = paymentService.findPayment(booking.id);
        Refund refund = paymentService.findRefund(booking.id);
        CredentialVO credential = null;
        if (booking.source == BookingSource.ONLINE && booking.status == BookingStatus.CONFIRMED) {
            SignedCredentialVO signedCredential = credentialService.createCredential(booking.id);
            credential = new CredentialVO(signedCredential.getQrDataUrl(), signedCredential.getShortCode());
        }
        return new BookingDetailVO(BookingVO.from(booking), PaymentVO.from(payment), RefundVO.from(refund),
                memberFundsService.getComposition(booking), credential);
    }

    /** 评价只接受本人已核销完成的线上预约，线下登记和未到场均不具备资格。 */
    @Override
    public Booking requireReviewEligibleBooking(long userId, long bookingId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null || booking.userId == null || booking.userId != userId) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        if (booking.source != BookingSource.ONLINE || booking.status != BookingStatus.COMPLETED
                || booking.completedAt == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REVIEW_NOT_ELIGIBLE);
        }
        return booking;
    }

    /**
     * 功能描述: 查询场馆指定日期的预约列表。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<VenueBookingVO> listVenueBookings(long venueId, LocalDate date) {
        return bookingMapper.selectVenueBookings(venueId, date).stream().map(VenueBookingVO::from).toList();
    }

    /**
     * 功能描述: 按场次日期查询并汇总全平台或单个场馆经营报表。
     * 创建时间: 2026/09/03 10:35
     *
     * @author gjm
     */
    @Override
    public OperationReportVO getOperationReport(Long venueId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        if (venueId != null) {
            venueService.getVenue(venueId);
        }
        Instant startAt = startDate.atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        Instant endAt = endDate.plusDays(1).atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        List<OperationReportDailyBO> dailyRows = bookingMapper.selectOperationReport(venueId, startAt, endAt);

        long bookingCount = 0;
        long effectiveBookingCount = 0;
        long completedCount = 0;
        long cancelledCount = 0;
        long noShowCount = 0;
        long amountFen = 0;
        List<OperationReportDailyVO> daily = new ArrayList<>();
        for (OperationReportDailyBO row : dailyRows) {
            bookingCount += row.getBookingCount();
            effectiveBookingCount += row.getEffectiveBookingCount();
            completedCount += row.getCompletedCount();
            cancelledCount += row.getCancelledCount();
            noShowCount += row.getNoShowCount();
            amountFen += row.getAmountFen();
            daily.add(new OperationReportDailyVO(row.getReportDate(), row.getBookingCount(),
                    row.getEffectiveBookingCount(), row.getCompletedCount(), row.getCancelledCount(),
                    row.getNoShowCount(), row.getAmountFen()));
        }
        return new OperationReportVO(bookingCount, effectiveBookingCount, completedCount,
                cancelledCount, noShowCount, amountFen, daily);
    }

    /**
     * 功能描述: 按场次结束月份计算线上预约平台服务费。
     * 创建时间: 2026/09/08
     *
     * @author gjm
     */
    @Override
    public ServiceFeeSettlementVO getServiceFeeSettlement(Long venueId, YearMonth billingMonth) {
        if (billingMonth == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        Venue scopedVenue = venueId == null ? null : venueService.getVenue(venueId);
        Instant startAt = billingMonth.atDay(1).atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        Instant endAt = billingMonth.plusMonths(1).atDay(1)
                .atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        List<ServiceFeeBookingBO> rows = bookingMapper.selectServiceFeeBookings(venueId, startAt, endAt);

        long bookingAmountFen = 0;
        long serviceFeeMicroYuan = 0;
        Map<Long, ServiceFeeVenueSummaryVO> venueSummaries = new LinkedHashMap<>();
        Map<Long, Long> venueFeeMicroYuan = new LinkedHashMap<>();
        List<ServiceFeeBookingVO> bookings = new ArrayList<>();
        for (ServiceFeeBookingBO row : rows) {
            long bookingFeeMicroYuan = Math.multiplyExact(
                    row.getAmountFen().longValue(), row.getServiceFeeRateBasisPoints().longValue());
            bookingAmountFen = Math.addExact(bookingAmountFen, row.getAmountFen());
            serviceFeeMicroYuan = Math.addExact(serviceFeeMicroYuan, bookingFeeMicroYuan);

            ServiceFeeVenueSummaryVO venueSummary = venueSummaries.get(row.getVenueId());
            if (venueSummary == null) {
                venueSummary = new ServiceFeeVenueSummaryVO(row.getVenueId(), row.getVenueName(),
                        row.getCurrentServiceFeeRateBasisPoints(), 0L, 0L, "0");
                venueSummaries.put(row.getVenueId(), venueSummary);
                venueFeeMicroYuan.put(row.getVenueId(), 0L);
            }
            venueSummary.setBillableBookingCount(venueSummary.getBillableBookingCount() + 1);
            venueSummary.setBookingAmountFen(Math.addExact(
                    venueSummary.getBookingAmountFen(), row.getAmountFen()));
            venueFeeMicroYuan.put(row.getVenueId(), Math.addExact(
                    venueFeeMicroYuan.get(row.getVenueId()), bookingFeeMicroYuan));

            bookings.add(new ServiceFeeBookingVO(row.getBookingNo(), row.getVenueId(), row.getVenueName(),
                    row.getCourtName(), row.getStartAt(), row.getEndAt(), row.getStatus(), row.getAmountFen(),
                    row.getServiceFeeRateBasisPoints(), formatServiceFee(bookingFeeMicroYuan)));
        }
        for (ServiceFeeVenueSummaryVO venueSummary : venueSummaries.values()) {
            venueSummary.setServiceFeeYuan(formatServiceFee(venueFeeMicroYuan.get(venueSummary.getVenueId())));
        }
        if (scopedVenue != null && venueSummaries.isEmpty()) {
            venueSummaries.put(scopedVenue.id, new ServiceFeeVenueSummaryVO(scopedVenue.id, scopedVenue.name,
                    scopedVenue.serviceFeeRateBasisPoints, 0L, 0L, "0"));
        }
        return new ServiceFeeSettlementVO(billingMonth.toString(),
                scopedVenue == null ? null : scopedVenue.serviceFeeRateBasisPoints,
                (long) rows.size(), bookingAmountFen, formatServiceFee(serviceFeeMicroYuan),
                new ArrayList<>(venueSummaries.values()), bookings);
    }

    /**
     * 功能描述: 取消已确认预约并在同一事务释放场次、模拟退款和记录通知。
     * 创建时间: 2026/08/28 16:29
     *
     * @param booking 已锁定预约
     * @param cancelledBy 取消操作方
     * @param reason 取消原因
     * @return 已取消预约
     * @author gjm
     */
    private Booking cancel(Booking booking, CancelledBy cancelledBy, String reason) {
        if (!BookingStatus.CONFIRMED.equals(booking.status)
                && !BookingStatus.PENDING_PAYMENT.equals(booking.status)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NOT_CANCELLABLE);
        }
        // 取消状态与场次释放共用当前事务，任一步失败都不得留下半完成状态。
        booking.status = BookingStatus.CANCELLED;
        booking.cancelledBy = cancelledBy;
        booking.cancelReason = reason;
        booking.cancelledAt = Instant.now();
        booking.updatedAt = LocalDateTime.now();
        bookingMapper.updateById(booking);
        scheduleService.releaseBookingSlot(booking.sessionSlotId);

        // 线下预约仅登记实际收款事实，取消时不得伪造平台退款或改写登记状态。
        if (booking.source == BookingSource.ONLINE) {
            if (booking.paymentStatus == BookingPaymentStatus.PAYING) {
                memberFundsService.release(booking.id, booking.cancelledAt);
                paymentService.closePending(booking.id, booking.cancelledAt);
                booking.paymentStatus = BookingPaymentStatus.PAYMENT_FAILED;
                bookingMapper.updateById(booking);
            } else {
                memberFundsService.refund(booking.id, booking.cancelledAt);
                paymentService.refundAndNotify(booking, reason);
            }
        }
        return booking;
    }

    /** Redis 锁与数据库事务结束后才调用外部退款渠道。 */
    private Booking finishExternalRefund(Booking booking) {
        if (booking.paymentStatus == BookingPaymentStatus.REFUNDING) {
            paymentService.processRefund(booking.id);
            return bookingMapper.selectById(booking.id);
        }
        return booking;
    }

    /** 加锁前读取场馆 ID，仅用于确定会员余额锁。 */
    private long sessionSlotVenueId(long sessionSlotId) {
        SessionSlot slot = sessionSlotMapper.selectById(sessionSlotId);
        if (slot == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE);
        }
        return slot.venueId;
    }

    /** 在线下预约事务中写预约并把已锁定场次直接转为已订。 */
    private Booking createOfflineLocked(SessionSlot slot, Venue venue, Court court, SportType sport,
                                        String contactName, String contactPhone,
                                        BookingPaymentStatus paymentStatus, Long venueCustomerId) {
        Booking booking = baseBooking(slot, venue, court, sport, contactName, contactPhone);
        booking.venueCustomerId = venueCustomerId;
        booking.source = BookingSource.OFFLINE;
        booking.status = BookingStatus.CONFIRMED;
        booking.paymentStatus = paymentStatus;
        bookingMapper.insert(booking);

        // 线下预约不产生支付占位，创建后直接占用具体场次。
        slot.status = SessionSlotStatus.BOOKED;
        slot.holdBookingId = booking.id;
        slot.holdExpiresAt = null;
        sessionSlotMapper.updateById(slot);
        return booking;
    }

    /** 对批量日期校验非空、去重并排序，保证请求和加锁结果稳定。 */
    private List<LocalDate> normalizeDates(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty() || dates.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        return new ArrayList<>(new TreeSet<>(dates));
    }

    /** 汇总缺失、已占用或已超过最晚登记时间的全部冲突日期。 */
    private List<LocalDate> findConflictDates(List<LocalDate> dates, List<SessionSlot> slots,
                                              Venue venue, Instant now) {
        Map<LocalDate, SessionSlot> slotsByDate = slotsByDate(slots);
        Instant latestAllowedStart = now.plus(Duration.ofMinutes(venue.minBookingMinutes));
        List<LocalDate> conflicts = new ArrayList<>();
        for (LocalDate date : dates) {
            SessionSlot slot = slotsByDate.get(date);
            if (slot == null || slot.status != SessionSlotStatus.OPEN
                    || !slot.startAt.isAfter(latestAllowedStart)) {
                conflicts.add(date);
            }
        }
        return conflicts;
    }

    /** 按业务日期整理场次，调用方已经通过查询条件限定场地、运动和时段。 */
    private Map<LocalDate, SessionSlot> slotsByDate(List<SessionSlot> slots) {
        Map<LocalDate, SessionSlot> result = new LinkedHashMap<>();
        if (slots != null) {
            for (SessionSlot slot : slots) {
                result.putIfAbsent(slot.slotDate, slot);
            }
        }
        return result;
    }

    /** 新线下预约只能明确登记为未付或已付。 */
    private void validateOfflinePaymentStatus(BookingPaymentStatus paymentStatus) {
        if (paymentStatus != BookingPaymentStatus.UNPAID && paymentStatus != BookingPaymentStatus.PAID) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
    }

    /** 可选顾客档案必须由顾客服务按当前场馆边界确认。 */
    private void validateVenueCustomer(long venueId, Long venueCustomerId) {
        if (venueCustomerId != null) {
            venueCustomerService.getVenueCustomer(venueId, venueCustomerId);
        }
    }

    /** 线下登记收款状态只允许产品定义的单向人工确认路径。 */
    private boolean isAllowedOfflinePaymentTransition(BookingStatus bookingStatus,
                                                       BookingPaymentStatus beforeStatus,
                                                       BookingPaymentStatus afterStatus) {
        if (afterStatus == null) {
            return false;
        }
        if (beforeStatus == BookingPaymentStatus.UNRECORDED) {
            return afterStatus == BookingPaymentStatus.UNPAID || afterStatus == BookingPaymentStatus.PAID;
        }
        if (beforeStatus == BookingPaymentStatus.UNPAID) {
            return afterStatus == BookingPaymentStatus.PAID;
        }
        return bookingStatus == BookingStatus.CANCELLED
                && beforeStatus == BookingPaymentStatus.PAID
                && afterStatus == BookingPaymentStatus.REFUNDED;
    }

    /**
     * 功能描述: 从具体场次和当前资料构造不可变预约快照。
     * 创建时间: 2026/08/28 16:29
     *
     * @param slot 具体场次
     * @param venue 场馆
     * @param court 场地
     * @param sport 运动类型
     * @param name 联系人
     * @param phone 联系电话
     * @return 预约快照
     * @author gjm
     */
    private Booking baseBooking(SessionSlot slot, Venue venue, Court court, SportType sport, String name,
                                String phone) {
        Booking booking = new Booking();
        booking.bookingNo = "YC" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, BusinessConstants.BOOKING_NO_RANDOM_LENGTH).toUpperCase();
        booking.venueId = venue.id;
        booking.courtId = court.id;
        booking.sportTypeId = sport.id;
        booking.sessionSlotId = slot.id;
        booking.contactName = name;
        booking.contactPhone = phone;
        booking.venueName = venue.name;
        booking.courtName = court.name;
        booking.sportName = sport.name;
        booking.startAt = slot.startAt;
        booking.endAt = slot.endAt;
        booking.amountFen = slot.priceFen;
        booking.serviceFeeRateBasisPoints = 0;
        booking.createdAt = LocalDateTime.now();
        booking.updatedAt = booking.createdAt;
        return booking;
    }

    /**
     * 功能描述: 将整数微元格式化为无舍入的平台服务费金额。
     * 创建时间: 2026/09/08
     *
     * @param microYuan 平台服务费，单位为百万分之一元
     * @return 最多六位小数的元金额
     * @author gjm
     */
    private String formatServiceFee(long microYuan) {
        return BigDecimal.valueOf(microYuan, 6).stripTrailingZeros().toPlainString();
    }

    /**
     * 功能描述: 锁定并返回预约，供支付、取消和核销状态转换使用。
     * 创建时间: 2026/08/28 16:29
     *
     * @param bookingId 预约 ID
     * @return 已锁定预约
     * @author gjm
     */
    private Booking requiredLockedBooking(long bookingId) {
        Booking booking = bookingMapper.selectForUpdate(bookingId);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return booking;
    }

    /**
     * 功能描述: 加锁前读取预约，以确定需要共同保护的具体场次资源。
     *
     * @param bookingId 预约 ID
     * @return 预约
     * @author gjm
     */
    private Booking requiredBooking(long bookingId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return booking;
    }

    /**
     * 功能描述: 校验预约归属，越权访问统一表现为不存在。
     * 创建时间: 2026/08/28 16:29
     *
     * @param booking 预约
     * @param userId 当前用户 ID
     * @author gjm
     */
    private void requireOwner(Booking booking, long userId) {
        if (booking.userId == null || booking.userId != userId) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
    }

    /** 加锁等待期间若预约已被改期，拒绝使用未锁定的新场次继续写入。 */
    private void requireUnchangedSessionSlot(Booking booking, long expectedSessionSlotId) {
        if (booking.sessionSlotId != expectedSessionSlotId) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
    }

    /**
     * 功能描述: 使用 Redis 固定窗口限制用户频繁创建占位预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param userId 当前用户 ID
     * @author gjm
     */
    private void enforceCreateLimit(long userId) {
        String key = AuthConstants.BOOKING_LIMIT_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, BusinessConstants.BOOKING_CREATE_LIMIT_WINDOW);
        }
        if (count != null && count > createLimit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.CREATE_LIMIT);
        }
    }

    /**
     * 功能描述: 校验信任边界上的必填文本。
     * 创建时间: 2026/08/28 16:29
     *
     * @param value 原始文本
     * @return 去除首尾空格的文本
     * @author gjm
     */
    private String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        return value.trim();
    }
}
