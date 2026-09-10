package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.po.UserAccount;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.po.Refund;
import com.sportsbooking.admin.base.mapper.BookingMapper;
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
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.CancelledBy;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.Venue;
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
    private final SessionSlotMapper sessionSlotMapper;
    private final UserAccountMapper userAccountMapper;
    private final CourtMapper courtMapper;
    private final SportTypeMapper sportTypeMapper;
    private final VenueService venueService;
    private final CredentialService credentialService;
    private final PaymentService paymentService;
    private final ScheduleService scheduleService;
    private final StringRedisTemplate redisTemplate;
    private final int paymentTimeoutMinutes;
    private final int pendingLimit;
    private final int createLimit;

    public BookingServiceImpl(BookingMapper bookingMapper, SessionSlotMapper sessionSlotMapper,
                          UserAccountMapper userAccountMapper,
                          CourtMapper courtMapper, SportTypeMapper sportTypeMapper, VenueService venueService,
                          CredentialService credentialService, PaymentService paymentService,
                          ScheduleService scheduleService,
                          StringRedisTemplate redisTemplate,
                          @Value("${app.booking.payment-timeout-minutes}") int paymentTimeoutMinutes,
                          @Value("${app.booking.pending-limit}") int pendingLimit,
                          @Value("${app.booking.create-limit-per-ten-minutes}") int createLimit) {
        this.bookingMapper = bookingMapper;
        this.sessionSlotMapper = sessionSlotMapper;
        this.userAccountMapper = userAccountMapper;
        this.courtMapper = courtMapper;
        this.sportTypeMapper = sportTypeMapper;
        this.venueService = venueService;
        this.credentialService = credentialService;
        this.paymentService = paymentService;
        this.scheduleService = scheduleService;
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
    @Transactional
    @Override
    public Booking createOnline(long userId, long sessionSlotId, String contactName, String contactPhone) {
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
        UserAccount user = userAccountMapper.selectById(userId);
        String name = requireText(contactName);
        String phone = requireText(contactPhone);

        Booking booking = baseBooking(slot, venue, court, sport, name, phone);
        booking.userId = userId;
        booking.source = BookingSource.ONLINE;
        // 保存创建时费率，后续场馆调价不得改变已有预约的计费口径。
        booking.serviceFeeRateBasisPoints = venue.serviceFeeRateBasisPoints == null
                ? 0 : venue.serviceFeeRateBasisPoints;
        booking.status = BookingStatus.PENDING_PAYMENT;
        booking.paymentExpiresAt = Instant.now().plus(Duration.ofMinutes(paymentTimeoutMinutes));
        bookingMapper.insert(booking);

        // 预约和具体场次在同一事务内写入；行锁保证并发请求只有一个能从 OPEN 转为 HELD。
        slot.status = SessionSlotStatus.HELD;
        slot.holdBookingId = booking.id;
        slot.holdExpiresAt = booking.paymentExpiresAt;
        sessionSlotMapper.updateById(slot);

        paymentService.createPending(booking);

        if (user != null) {
            user.contactName = name;
            user.phone = phone;
            user.updatedAt = LocalDateTime.now();
            userAccountMapper.updateById(user);
        }
        return booking;
    }

    /**
     * 功能描述: 为场馆创建立即确认的电话或到店预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Transactional
    @Override
    public Booking createOffline(long venueId, long sessionSlotId, String contactName, String contactPhone) {
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
        Booking booking = baseBooking(slot, venue, courtMapper.selectById(slot.courtId),
                sportTypeMapper.selectById(slot.sportTypeId), requireText(contactName),
                requireText(contactPhone));
        booking.source = BookingSource.OFFLINE;
        booking.status = BookingStatus.CONFIRMED;
        bookingMapper.insert(booking);
        // 线下预约无需支付占位，预约创建和场次锁定在同一事务直接进入已确认状态。
        slot.status = SessionSlotStatus.BOOKED;
        slot.holdBookingId = booking.id;
        sessionSlotMapper.updateById(slot);
        return booking;
    }

    /**
     * 功能描述: 校验取消截止时间后取消用户预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Transactional
    @Override
    public Booking cancelByUser(long userId, long bookingId) {
        Booking booking = requiredLockedBooking(bookingId);
        requireOwner(booking, userId);
        Venue venue = venueService.getVenue(booking.venueId);
        Instant cutoff = booking.startAt.minus(Duration.ofMinutes(venue.cancelCutoffMinutes));
        if (!Instant.now().isBefore(cutoff)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CANCEL_CUTOFF);
        }
        return cancel(booking, CancelledBy.USER, "用户取消");
    }

    /**
     * 功能描述: 校验场馆归属和取消原因后取消预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Transactional
    @Override
    public Booking cancelByVenue(long venueId, long bookingId, String reason) {
        Booking booking = requiredLockedBooking(bookingId);
        if (booking.venueId != venueId) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return cancel(booking, CancelledBy.VENUE, requireText(reason));
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
        if (booking.status == BookingStatus.CONFIRMED) {
            SignedCredentialVO signedCredential = credentialService.createCredential(booking.id);
            credential = new CredentialVO(signedCredential.getQrDataUrl(), signedCredential.getShortCode());
        }
        return new BookingDetailVO(BookingVO.from(booking), PaymentVO.from(payment), RefundVO.from(refund), credential);
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
        if (!BookingStatus.CONFIRMED.equals(booking.status)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NOT_CANCELLABLE);
        }
        // 取消状态、场次释放和退款共用当前事务，任一步失败都不得留下半完成状态。
        booking.status = BookingStatus.CANCELLED;
        booking.cancelledBy = cancelledBy;
        booking.cancelReason = reason;
        booking.cancelledAt = Instant.now();
        booking.updatedAt = LocalDateTime.now();
        bookingMapper.updateById(booking);
        scheduleService.releaseBookingSlot(booking.sessionSlotId);

        paymentService.refundAndNotify(booking, reason);
        return booking;
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
