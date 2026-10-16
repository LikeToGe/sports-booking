package com.sportsbooking.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.BookingPaymentStatusLogMapper;
import com.sportsbooking.admin.base.mapper.BookingRescheduleLogMapper;
import com.sportsbooking.admin.base.bo.OperationReportDailyBO;
import com.sportsbooking.admin.base.bo.ServiceFeeBookingBO;
import com.sportsbooking.admin.biz.pojo.vo.OperationReportVO;
import com.sportsbooking.admin.biz.pojo.vo.ServiceFeeSettlementVO;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.service.PaymentService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.biz.service.VenueCustomerService;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.service.NotificationService;
import com.sportsbooking.admin.biz.service.impl.BookingServiceImpl;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.base.po.VenueCustomer;
import com.sportsbooking.admin.base.po.BookingPaymentStatusLog;
import com.sportsbooking.admin.base.po.BookingRescheduleLog;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 功能描述: 预约创建截止和场次独占规则单元测试。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@SuppressWarnings("unchecked")
class BookingServiceImplTest {
    private final BookingMapper bookingMapper = mock(BookingMapper.class);
    private final BookingPaymentStatusLogMapper bookingPaymentStatusLogMapper =
            mock(BookingPaymentStatusLogMapper.class);
    private final BookingRescheduleLogMapper bookingRescheduleLogMapper = mock(BookingRescheduleLogMapper.class);
    private final SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final CourtMapper courtMapper = mock(CourtMapper.class);
    private final SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
    private final VenueService venueService = mock(VenueService.class);
    private final VenueCustomerService venueCustomerService = mock(VenueCustomerService.class);
    private final MemberFundsService memberFundsService = mock(MemberFundsService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final CredentialService credentialService = mock(CredentialService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final ScheduleService scheduleService = mock(ScheduleService.class);
    private final RedisLockService redisLockService = mock(RedisLockService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private BookingServiceImpl service;

    @BeforeEach
    void setUp() {
        com.sportsbooking.admin.base.po.UserAccount activeUser = new com.sportsbooking.admin.base.po.UserAccount();
        activeUser.id = 1L;
        activeUser.status = EnableStatus.ACTIVE;
        when(userAccountMapper.selectForUpdate(anyLong())).thenReturn(activeUser);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(1L);
        when(bookingMapper.selectCount(any())).thenReturn(0L);
        when(courtMapper.selectSportIds(anyLong())).thenReturn(List.of(41L));
        VenueCustomer venueCustomer = new VenueCustomer();
        venueCustomer.id = 501L;
        when(venueCustomerService.ensureCustomer(anyLong(), anyLong())).thenReturn(venueCustomer);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(redisLockService).execute(any(), any());
        service = new BookingServiceImpl(bookingMapper, bookingPaymentStatusLogMapper,
                bookingRescheduleLogMapper, sessionSlotMapper, userAccountMapper,
                courtMapper, sportTypeMapper, venueService, venueCustomerService, memberFundsService,
                notificationService, credentialService, paymentService,
                scheduleService, redisLockService, redisTemplate, 10, 2, 5);
    }

    @Test
    void retryCancelledBookingReturnsExistingFactWithoutMoreSideEffects() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.REFUNDED);
        booking.source = BookingSource.ONLINE;
        booking.userId = 1L;
        booking.status = BookingStatus.CANCELLED;
        booking.cancelReason = "原取消原因";
        when(bookingMapper.selectById(7L)).thenReturn(booking);

        assertThat(service.cancelByUser(1L, 7L)).isSameAs(booking);
        assertThat(service.cancelByVenue(21L, 7L, "重试取消", true, false)).isSameAs(booking);
        assertThat(booking.cancelReason).isEqualTo("原取消原因");
        assertThatThrownBy(() -> service.cancelByUser(2L, 7L)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.cancelByVenue(22L, 7L, "重试取消", true, false))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.cancelByVenue(21L, 7L, "重试取消", false, false))
                .isInstanceOf(ApiException.class);
        org.mockito.Mockito.verifyNoInteractions(paymentService, memberFundsService, scheduleService);
    }

    @Test
    void employeeCannotConfirmHistoricalUnrecordedOfflinePayment() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.UNRECORDED);
        when(bookingMapper.selectForUpdate(7L)).thenReturn(booking);

        assertThatThrownBy(() -> service.updateOfflinePaymentStatus(
                21L, null, 19L, 7L, BookingPaymentStatus.PAID, "历史补录"))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.FORBIDDEN.getDescription());
        assertThat(booking.paymentStatus).isEqualTo(BookingPaymentStatus.UNRECORDED);
        verify(bookingPaymentStatusLogMapper, never()).insert(any(BookingPaymentStatusLog.class));
        verify(bookingMapper, never()).updateById(any(Booking.class));
    }

    @Test
    void rejectsDisabledVenueBeforeWritingOrFreezingFunds() {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(7200));
        Venue venue = venue(30);
        venue.status = com.sportsbooking.admin.base.enums.VenueStatus.SUSPENDED;
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(venueService.getVenue(21L)).thenReturn(venue);
        when(courtMapper.selectById(31L)).thenReturn(court(31L));
        when(sportTypeMapper.selectById(41L)).thenReturn(sport(41L));

        assertThatThrownBy(() -> service.createOnline(1L, 11L, "测试联系人", "13800000000"))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.SLOT_UNAVAILABLE.getDescription());
        verify(bookingMapper, never()).insert(any(Booking.class));
        verify(memberFundsService, never()).freeze(any(), anyLong(), any());
    }

    @Test
    void rejectsChangedPriceWithoutCreatingBooking() {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(7200));
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(venueService.getVenue(21L)).thenReturn(venue(30));
        when(courtMapper.selectById(31L)).thenReturn(court(31L));
        when(sportTypeMapper.selectById(41L)).thenReturn(sport(41L));
        assertThatThrownBy(() -> service.createOnline(1L, 11L, "测试联系人", "13800000000",
                "p5-price-test", 5000, "TIME_SEARCH"))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.SLOT_PRICE_CHANGED.getDescription());
        assertThat(slot.status).isEqualTo(SessionSlotStatus.OPEN);
        verify(bookingMapper, never()).insert(any(Booking.class));
    }

    @Test
    void onlyCompletedOwnOnlineBookingCanBeReviewed() {
        Booking booking = new Booking();
        booking.id = 15L;
        booking.userId = 9L;
        booking.source = BookingSource.ONLINE;
        booking.status = BookingStatus.COMPLETED;
        booking.completedAt = Instant.now();
        when(bookingMapper.selectById(15L)).thenReturn(booking);

        assertThat(service.requireReviewEligibleBooking(9L, 15L)).isSameAs(booking);
        assertThatThrownBy(() -> service.requireReviewEligibleBooking(8L, 15L))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.NOT_FOUND.getDescription());
        booking.status = BookingStatus.NO_SHOW;
        assertThatThrownBy(() -> service.requireReviewEligibleBooking(9L, 15L))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.REVIEW_NOT_ELIGIBLE.getDescription());
    }

    @Test
    void rejectsBookingInsideVenueMinimumLeadTime() {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(20 * 60L));
        Venue venue = venue(30);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(venueService.getVenue(slot.venueId)).thenReturn(venue);

        assertThatThrownBy(() -> service.createOnline(1L, 11L, "张三", "13800000000"))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.SLOT_UNAVAILABLE.getDescription());
        verify(bookingMapper, never()).insert(any(Booking.class));
    }

    @Test
    void onlyFirstRequestCanMoveOpenSlotToHeld() {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(2 * 60 * 60L));
        Venue venue = venue(30);
        venue.serviceFeeRateBasisPoints = 525;
        Court court = new Court();
        court.id = slot.courtId;
        court.name = "篮球 1 号场";
        court.status = EnableStatus.ACTIVE;
        SportType sport = new SportType();
        sport.status = EnableStatus.ACTIVE;
        sport.id = slot.sportTypeId;
        sport.name = "篮球";
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(venueService.getVenue(slot.venueId)).thenReturn(venue);
        when(courtMapper.selectById(slot.courtId)).thenReturn(court);
        when(sportTypeMapper.selectById(slot.sportTypeId)).thenReturn(sport);

        service.createOnline(1L, 11L, "张三", "13800000000");
        assertThatThrownBy(() -> service.createOnline(2L, 11L, "李四", "13800000001"))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.SLOT_UNAVAILABLE.getDescription());
        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingMapper).insert(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().serviceFeeRateBasisPoints).isEqualTo(525);
        assertThat(bookingCaptor.getValue().venueCustomerId).isEqualTo(501L);
        assertThat(bookingCaptor.getValue().paymentStatus).isEqualTo(BookingPaymentStatus.PAYING);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"TIME_SEARCH", "BOOK_AGAIN", "REMINDER_ENTRY"})
    void fullBalanceBookingConfirmsWithoutExternalPaymentRecord(String entryType) {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(2 * 60 * 60L));
        Venue venue = venue(30);
        Court court = new Court();
        court.status = EnableStatus.ACTIVE;
        court.id = slot.courtId;
        court.name = "篮球 1 号场";
        SportType sport = new SportType();
        sport.status = EnableStatus.ACTIVE;
        sport.id = slot.sportTypeId;
        sport.name = "篮球";
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(venueService.getVenue(slot.venueId)).thenReturn(venue);
        when(courtMapper.selectById(slot.courtId)).thenReturn(court);
        when(sportTypeMapper.selectById(slot.sportTypeId)).thenReturn(sport);
        doAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.balanceAmountFen = booking.amountFen;
            booking.paidBalanceAmountFen = booking.amountFen;
            booking.externalAmountFen = 0;
            return null;
        }).when(memberFundsService).freeze(any(Booking.class), anyLong(), any());
        doAnswer(invocation -> {
            ((Booking) invocation.getArgument(0)).id = 99L;
            return 1;
        }).when(bookingMapper).insert(any(Booking.class));

        Booking booking = service.createOnline(1L, 11L, "张三", "13800000000", null, slot.priceFen, entryType);

        assertThat(booking.entryType).isEqualTo(entryType);
        assertThat(booking.amountFen).isEqualTo(slot.priceFen);
        assertThat(booking.status).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.paymentStatus).isEqualTo(BookingPaymentStatus.PAID);
        verify(paymentService, never()).createPending(any());
        verify(memberFundsService).consume(org.mockito.ArgumentMatchers.eq(booking.id),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ownBookingKeepsSnapshotAndProvidesCurrentVenueArrivalInformation() {
        Booking booking = new Booking();
        booking.id = 77L;
        booking.userId = 1L;
        booking.venueId = 21L;
        booking.venueName = "预约时场馆名称";
        booking.status = BookingStatus.CANCELLED;
        booking.source = BookingSource.ONLINE;
        booking.amountFen = 5050;
        Venue current = venue(30);
        current.name = "当前场馆名称";
        current.entryNotice = "请穿运动鞋";
        current.latitude = new BigDecimal("34.7");
        current.longitude = new BigDecimal("113.7");
        current.status = com.sportsbooking.admin.base.enums.VenueStatus.SUSPENDED;
        when(bookingMapper.selectById(77L)).thenReturn(booking);
        when(venueService.getVenue(21L)).thenReturn(current);
        var detail = service.getUserBooking(1L, 77L);
        assertThat(detail.getBooking().getVenueName()).isEqualTo("预约时场馆名称");
        assertThat(detail.getBooking().getAmountFen()).isEqualTo(5050);
        assertThat(detail.getVenue().getEntryNotice()).isEqualTo("请穿运动鞋");
        assertThat(detail.getVenue().getLatitude()).isEqualByComparingTo("34.7");
        assertThatThrownBy(() -> service.getUserBooking(2L, 77L)).isInstanceOf(ApiException.class);
    }

    @Test
    void refusesToCancelPendingBookingWhileChannelResultIsUnknown() {
        Booking booking = new Booking();
        booking.id = 77L;
        booking.userId = 1L;
        booking.venueCustomerId = 501L;
        booking.sessionSlotId = 11L;
        booking.status = BookingStatus.PENDING_PAYMENT;
        when(bookingMapper.selectById(77L)).thenReturn(booking);
        when(paymentService.closeUnpaid(1L, 77L)).thenReturn(booking);

        assertThatThrownBy(() -> service.cancelByUser(1L, 77L))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.OPERATION_IN_PROGRESS.getDescription());
        verify(scheduleService, never()).releaseBookingSlot(anyLong());
        verify(redisLockService, never()).execute(any(), any());
    }

    @Test
    void returnsClosedUnpaidBookingWithoutCancellingTwice() {
        Booking booking = new Booking();
        booking.id = 77L;
        booking.userId = 1L;
        booking.status = BookingStatus.PENDING_PAYMENT;
        when(bookingMapper.selectById(77L)).thenReturn(booking);
        when(paymentService.closeUnpaid(1L, 77L)).thenAnswer(invocation -> {
            booking.status = BookingStatus.CANCELLED;
            booking.paymentStatus = BookingPaymentStatus.PAYMENT_FAILED;
            return booking;
        });

        assertThat(service.cancelByUser(1L, 77L)).isSameAs(booking);
        assertThat(booking.status).isEqualTo(BookingStatus.CANCELLED);
        verify(redisLockService, never()).execute(any(), any());
    }

    @Test
    void rejectsOnlineSlotOverlappingAnExistingBookingSnapshotBeforeFreezing() {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(7200));
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(venueService.getVenue(21L)).thenReturn(venue(30));
        when(courtMapper.selectById(31L)).thenReturn(court(31L));
        when(sportTypeMapper.selectById(41L)).thenReturn(sport(41L));
        when(bookingMapper.selectOverlappingActiveForUpdate(31L, slot.startAt, slot.endAt, null, null))
                .thenReturn(List.of(99L));

        assertThatThrownBy(() -> service.createOnline(1L, 11L, "本地Mock", "MOCK_PHONE"))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.SLOT_CONFLICT.getDescription());
        verify(bookingMapper, never()).insert(any(Booking.class));
        verify(memberFundsService, never()).freeze(any(), anyLong(), any());
        org.mockito.InOrder order = inOrder(courtMapper, sessionSlotMapper);
        order.verify(courtMapper).selectForUpdate(31L);
        order.verify(sessionSlotMapper).selectForUpdate(11L);
    }

    @Test
    void rejectsDifferentOfflineSlotIdOverlappingAnExistingBookingSnapshot() {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(7200));
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(bookingMapper.selectOverlappingActiveForUpdate(31L, slot.startAt, slot.endAt, null, null))
                .thenReturn(List.of(99L));

        assertThatThrownBy(() -> service.createOffline(21L, 11L, "本地Mock", "MOCK_PHONE",
                BookingPaymentStatus.PAID, null))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.SLOT_CONFLICT.getDescription());
        verify(bookingMapper, never()).insert(any(Booking.class));
    }

    @Test
    void offlineBookingDoesNotSnapshotServiceFeeRate() {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(2 * 60 * 60L));
        Venue venue = venue(30);
        venue.serviceFeeRateBasisPoints = 525;
        Court court = new Court();
        court.status = EnableStatus.ACTIVE;
        court.id = slot.courtId;
        court.name = "篮球 1 号场";
        SportType sport = new SportType();
        sport.status = EnableStatus.ACTIVE;
        sport.id = slot.sportTypeId;
        sport.name = "篮球";
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(venueService.getVenue(slot.venueId)).thenReturn(venue);
        when(courtMapper.selectById(slot.courtId)).thenReturn(court);
        when(sportTypeMapper.selectById(slot.sportTypeId)).thenReturn(sport);

        service.createOffline(slot.venueId, 11L, "线下用户", "13800000002",
                BookingPaymentStatus.PAID, null);

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingMapper).insert(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().source).isEqualTo(BookingSource.OFFLINE);
        assertThat(bookingCaptor.getValue().serviceFeeRateBasisPoints).isZero();
        assertThat(bookingCaptor.getValue().paymentStatus).isEqualTo(BookingPaymentStatus.PAID);
    }

    @Test
    void requiresOfflinePaymentStatusAndPaymentChangeReason() {
        assertThatThrownBy(() -> service.createOffline(21L, 11L, "线下用户", "13800000002", null, null))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.INVALID_ARGUMENT.getDescription());
        assertThatThrownBy(() -> service.updateOfflinePaymentStatus(
                21L, 99L, 7L, BookingPaymentStatus.PAID, " "))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.INVALID_ARGUMENT.getDescription());
        verify(redisLockService, never()).execute(any(), any());
    }

    @Test
    void updatesContactForCompletedOfflineBooking() {
        Booking booking = new Booking();
        booking.id = 7L;
        booking.venueId = 21L;
        booking.source = BookingSource.OFFLINE;
        booking.status = BookingStatus.COMPLETED;
        when(bookingMapper.selectForUpdate(7L)).thenReturn(booking);

        Booking result = service.updateOfflineContact(21L, 7L, " 新联系人 ", " 13800000009 ");

        assertThat(result.contactName).isEqualTo("新联系人");
        assertThat(result.contactPhone).isEqualTo("13800000009");
        verify(redisLockService).execute(any(), any());
        verify(bookingMapper).updateById(booking);
    }

    @Test
    void rejectsContactUpdateForOnlineBooking() {
        Booking booking = new Booking();
        booking.id = 7L;
        booking.venueId = 21L;
        booking.source = BookingSource.ONLINE;
        when(bookingMapper.selectForUpdate(7L)).thenReturn(booking);

        assertThatThrownBy(() -> service.updateOfflineContact(21L, 7L, "联系人", "13800000009"))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.NOT_FOUND.getDescription());
        verify(bookingMapper, never()).updateById(booking);
    }

    @Test
    void updatesOfflinePaymentStatusThroughAllowedPathsAndWritesLogs() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.UNRECORDED);
        when(bookingMapper.selectForUpdate(7L)).thenReturn(booking);

        service.updateOfflinePaymentStatus(21L, 99L, 7L, BookingPaymentStatus.UNPAID, " 补录未收款 ");
        service.updateOfflinePaymentStatus(21L, 99L, 7L, BookingPaymentStatus.PAID, " 确认已收款 ");
        booking.status = BookingStatus.CANCELLED;
        service.updateOfflinePaymentStatus(21L, 99L, 7L, BookingPaymentStatus.REFUNDED, " 线下已退款 ");

        assertThat(booking.paymentStatus).isEqualTo(BookingPaymentStatus.REFUNDED);
        ArgumentCaptor<BookingPaymentStatusLog> logCaptor = ArgumentCaptor.forClass(BookingPaymentStatusLog.class);
        verify(bookingPaymentStatusLogMapper, times(3)).insert(logCaptor.capture());
        assertThat(logCaptor.getAllValues()).extracting(log -> log.reason)
                .containsExactly("补录未收款", "确认已收款", "线下已退款");
        assertThat(logCaptor.getAllValues().get(0).operatorAdminId).isEqualTo(99L);
    }

    @Test
    void rejectsIllegalOfflinePaymentStatusTransitionWithoutLog() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.PAID);
        when(bookingMapper.selectForUpdate(7L)).thenReturn(booking);

        assertThatThrownBy(() -> service.updateOfflinePaymentStatus(
                21L, 99L, 7L, BookingPaymentStatus.REFUNDED, "尚未取消"))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.INVALID_ARGUMENT.getDescription());
        verify(bookingPaymentStatusLogMapper, never()).insert((BookingPaymentStatusLog) any());
    }

    @Test
    void cancelsOfflineBookingWithoutPaymentOrRefundSideEffects() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.PAID);
        when(bookingMapper.selectById(7L)).thenReturn(booking);
        when(bookingMapper.selectForUpdate(7L)).thenReturn(booking);

        service.cancelByVenue(21L, 7L, "客户取消", false, true);

        assertThat(booking.status).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.paymentStatus).isEqualTo(BookingPaymentStatus.PAID);
        verify(scheduleService).releaseBookingSlot(11L);
        verify(paymentService, never()).refundAndNotify(any(), any());
    }

    @Test
    void rejectsOfflineCancellationWithOnlyOnlineCancellationPermission() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.PAID);
        when(bookingMapper.selectById(7L)).thenReturn(booking);

        assertThatThrownBy(() -> service.cancelByVenue(21L, 7L, "客户取消", true, false))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.FORBIDDEN.getDescription());
        verify(redisLockService, never()).execute(any(), any());
    }

    @Test
    void allowsOnlineCancellationWithOnlyOnlineCancellationPermission() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.PAID);
        booking.source = BookingSource.ONLINE;
        when(bookingMapper.selectById(7L)).thenReturn(booking);
        when(bookingMapper.selectForUpdate(7L)).thenReturn(booking);

        service.cancelByVenue(21L, 7L, "客户取消", true, false);

        assertThat(booking.status).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void rejectsOnlineCancellationWithOnlyOfflineEditPermission() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.PAID);
        booking.source = BookingSource.ONLINE;
        when(bookingMapper.selectById(7L)).thenReturn(booking);

        assertThatThrownBy(() -> service.cancelByVenue(21L, 7L, "客户取消", false, true))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.FORBIDDEN.getDescription());
        verify(redisLockService, never()).execute(any(), any());
    }

    @Test
    void rejectsCrossVenuePendingCancellationBeforeClosingChannelPayment() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.PAYING);
        booking.source = BookingSource.ONLINE;
        booking.status = BookingStatus.PENDING_PAYMENT;
        booking.userId = 1L;
        when(bookingMapper.selectById(7L)).thenReturn(booking);

        assertThatThrownBy(() -> service.cancelByVenue(22L, 7L, "取消", true, false))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.NOT_FOUND.getDescription());
        verify(paymentService, never()).closeUnpaid(anyLong(), anyLong(), any(), any());
    }

    @Test
    void rejectsBlankVenueCancellationReasonBeforeClosingChannelPayment() {
        assertThatThrownBy(() -> service.cancelByVenue(21L, 7L, " ", true, false))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.INVALID_ARGUMENT.getDescription());
        verify(paymentService, never()).closeUnpaid(anyLong(), anyLong(), any(), any());
    }

    @Test
    void recordsVenueCancellationActorAndReasonWhenClosingUnpaidBooking() {
        Booking booking = offlineBooking(7L, 11L, BookingPaymentStatus.PAYING);
        booking.source = BookingSource.ONLINE;
        booking.status = BookingStatus.PENDING_PAYMENT;
        booking.userId = 1L;
        when(bookingMapper.selectById(7L)).thenReturn(booking);
        when(paymentService.closeUnpaid(1L, 7L, com.sportsbooking.admin.base.enums.CancelledBy.VENUE, "场馆取消"))
                .thenAnswer(invocation -> {
                    booking.status = BookingStatus.CANCELLED;
                    return booking;
                });

        assertThat(service.cancelByVenue(21L, 7L, " 场馆取消 ", true, false)).isSameAs(booking);
        verify(paymentService).closeUnpaid(1L, 7L, com.sportsbooking.admin.base.enums.CancelledBy.VENUE, "场馆取消");
    }

    @Test
    void deduplicatesAndLocksBatchSlotsInStableOrder() {
        LocalDate firstDate = LocalDate.now().plusDays(2);
        LocalDate secondDate = firstDate.plusDays(1);
        SessionSlot template = datedSlot(90L, firstDate, SessionSlotStatus.OPEN, 6000);
        SessionSlot firstSlot = datedSlot(30L, firstDate, SessionSlotStatus.OPEN, 6000);
        SessionSlot secondSlot = datedSlot(20L, secondDate, SessionSlotStatus.OPEN, 6000);
        when(sessionSlotMapper.selectById(90L)).thenReturn(template);
        when(venueService.getVenue(21L)).thenReturn(venue(0));
        when(bookingMapper.selectTemplateSlots(anyLong(), any())).thenReturn(List.of(firstSlot, secondSlot));
        when(bookingMapper.selectSlotsForUpdate(List.of(20L, 30L))).thenReturn(List.of(secondSlot, firstSlot));
        when(courtMapper.selectById(31L)).thenReturn(court(31L));
        when(sportTypeMapper.selectById(41L)).thenReturn(sport(41L));
        long[] bookingId = {100L};
        doAnswer(invocation -> {
            ((Booking) invocation.getArgument(0)).id = bookingId[0]++;
            return 1;
        }).when(bookingMapper).insert((Booking) any(Booking.class));

        List<Booking> result = service.createOfflineBatch(21L, 90L,
                List.of(secondDate, firstDate, secondDate), "客户", "13800000000",
                BookingPaymentStatus.UNPAID, null);

        assertThat(result).extracting(booking -> booking.startAt)
                .containsExactly(firstSlot.startAt, secondSlot.startAt);
        verify(bookingMapper).selectTemplateSlots(90L, List.of(firstDate, secondDate));
        verify(bookingMapper).selectSlotsForUpdate(List.of(20L, 30L));
        ArgumentCaptor<List<String>> lockCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisLockService).execute(lockCaptor.capture(), any());
        assertThat(lockCaptor.getValue()).containsExactly(
                "sports-booking:lock:session-slot:20", "sports-booking:lock:session-slot:30");
        org.mockito.InOrder order = inOrder(courtMapper, bookingMapper);
        order.verify(courtMapper).selectIdsForUpdate(List.of(31L));
        order.verify(bookingMapper).selectSlotsForUpdate(List.of(20L, 30L));
    }

    @Test
    void rejectsEntireOfflineBatchWhenOneDateHasAnotherBookingSnapshot() {
        LocalDate firstDate = LocalDate.now().plusDays(2);
        LocalDate secondDate = firstDate.plusDays(1);
        SessionSlot template = datedSlot(90L, firstDate, SessionSlotStatus.OPEN, 6000);
        SessionSlot first = datedSlot(30L, firstDate, SessionSlotStatus.OPEN, 6000);
        SessionSlot second = datedSlot(20L, secondDate, SessionSlotStatus.OPEN, 6000);
        when(sessionSlotMapper.selectById(90L)).thenReturn(template);
        when(venueService.getVenue(21L)).thenReturn(venue(0));
        when(bookingMapper.selectTemplateSlots(90L, List.of(firstDate, secondDate))).thenReturn(List.of(first, second));
        when(bookingMapper.selectSlotsForUpdate(List.of(20L, 30L))).thenReturn(List.of(second, first));
        Booking overlapping = offlineBooking(99L, 999L, BookingPaymentStatus.PAID);
        overlapping.startAt = second.startAt;
        overlapping.endAt = second.endAt;
        when(bookingMapper.selectActiveSnapshotsForUpdate(List.of(31L), first.startAt, second.endAt))
                .thenReturn(List.of(overlapping));

        ApiException error = catchThrowableOfType(() -> service.createOfflineBatch(21L, 90L,
                List.of(firstDate, secondDate), "本地Mock", "MOCK_PHONE", BookingPaymentStatus.PAID, null),
                ApiException.class);

        assertThat(error.getCode()).isEqualTo(ErrorCode.BATCH_SLOT_CONFLICT);
        assertThat(error.getData()).isEqualTo(List.of(secondDate));
        verify(bookingMapper, never()).insert(any(Booking.class));
    }

    @Test
    void returnsEveryBatchConflictBeforeCreatingAnything() {
        LocalDate firstDate = LocalDate.now().plusDays(2);
        LocalDate missingDate = firstDate.plusDays(1);
        LocalDate thirdDate = firstDate.plusDays(2);
        SessionSlot template = datedSlot(90L, firstDate, SessionSlotStatus.OPEN, 6000);
        SessionSlot busySlot = datedSlot(30L, firstDate, SessionSlotStatus.BOOKED, 6000);
        SessionSlot openSlot = datedSlot(40L, thirdDate, SessionSlotStatus.OPEN, 6000);
        when(sessionSlotMapper.selectById(90L)).thenReturn(template);
        when(venueService.getVenue(21L)).thenReturn(venue(0));
        when(bookingMapper.selectTemplateSlots(anyLong(), any())).thenReturn(List.of(busySlot, openSlot));

        ApiException exception = catchThrowableOfType(() -> service.createOfflineBatch(21L, 90L,
                List.of(firstDate, missingDate, thirdDate), "客户", "13800000000",
                BookingPaymentStatus.PAID, null), ApiException.class);

        assertThat(exception.getCode()).isEqualTo(ErrorCode.BATCH_SLOT_CONFLICT);
        assertThat(exception.getData()).isEqualTo(List.of(firstDate, missingDate));
        verify(redisLockService, never()).execute(any(), any());
        verify(bookingMapper, never()).insert(any(Booking.class));
    }

    @Test
    void reschedulesOfflineBookingToSamePriceAndKeepsPaymentStatus() {
        Booking booking = offlineBooking(7L, 30L, BookingPaymentStatus.PAID);
        booking.amountFen = 6000;
        booking.startAt = Instant.now().plusSeconds(7200);
        SessionSlot original = datedSlot(30L, LocalDate.now().plusDays(1), SessionSlotStatus.BOOKED, 6000);
        original.holdBookingId = 7L;
        SessionSlot target = datedSlot(20L, LocalDate.now().plusDays(2), SessionSlotStatus.OPEN, 6000);
        when(bookingMapper.selectById(7L)).thenReturn(booking);
        when(bookingMapper.selectForUpdate(7L)).thenReturn(booking);
        when(sessionSlotMapper.selectById(30L)).thenReturn(original);
        when(sessionSlotMapper.selectById(20L)).thenReturn(target);
        when(bookingMapper.selectSlotsForUpdate(List.of(20L, 30L))).thenReturn(List.of(target, original));
        when(courtMapper.selectById(31L)).thenReturn(court(31L));
        when(sportTypeMapper.selectById(41L)).thenReturn(sport(41L));
        doAnswer(invocation -> {
            original.status = SessionSlotStatus.OPEN;
            original.holdBookingId = null;
            return null;
        }).when(scheduleService).releaseBookingSlot(30L);

        service.rescheduleOffline(21L, 99L, 7L, 20L, "客户调整日期");

        assertThat(booking.sessionSlotId).isEqualTo(20L);
        assertThat(booking.paymentStatus).isEqualTo(BookingPaymentStatus.PAID);
        assertThat(original.status).isEqualTo(SessionSlotStatus.OPEN);
        assertThat(target.status).isEqualTo(SessionSlotStatus.BOOKED);
        verify(scheduleService).releaseBookingSlot(30L);
        ArgumentCaptor<BookingRescheduleLog> logCaptor = ArgumentCaptor.forClass(BookingRescheduleLog.class);
        verify(bookingRescheduleLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().originalSessionSlotId).isEqualTo(30L);
        assertThat(logCaptor.getValue().newSessionSlotId).isEqualTo(20L);
        assertThat(logCaptor.getValue().operatorAdminId).isEqualTo(99L);
        verify(bookingMapper).selectOverlappingActiveForUpdate(
                target.courtId, target.startAt, target.endAt, 7L, null);
    }

    @Test
    void rejectsRescheduleToSnapshotConflictBeforeReleasingOriginalInventory() {
        Booking booking = offlineBooking(7L, 30L, BookingPaymentStatus.PAID);
        SessionSlot target = datedSlot(20L, LocalDate.now().plusDays(2), SessionSlotStatus.OPEN, 6000);
        stubReschedule(booking, target);
        when(bookingMapper.selectOverlappingActiveForUpdate(31L, target.startAt, target.endAt, 7L, null))
                .thenReturn(List.of(99L));

        assertThatThrownBy(() -> service.rescheduleOffline(21L, 99L, 7L, 20L, "调整日期"))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.SLOT_CONFLICT.getDescription());
        verify(scheduleService, never()).releaseBookingSlot(anyLong());
        verify(bookingRescheduleLogMapper, never()).insert(any(BookingRescheduleLog.class));
        assertThat(booking.sessionSlotId).isEqualTo(30L);
    }

    @Test
    void rejectsOnlineOfflineReschedule() {
        Booking booking = offlineBooking(7L, 30L, BookingPaymentStatus.PAID);
        booking.source = BookingSource.ONLINE;
        stubReschedule(booking, datedSlot(20L, LocalDate.now().plusDays(2), SessionSlotStatus.OPEN, 6000));

        assertThatThrownBy(() -> service.rescheduleOffline(21L, 99L, 7L, 20L, "调整"))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.INVALID_ARGUMENT.getDescription());
    }

    @Test
    void rejectsCrossVenueOfflineReschedule() {
        Booking booking = offlineBooking(7L, 30L, BookingPaymentStatus.PAID);
        SessionSlot target = datedSlot(20L, LocalDate.now().plusDays(2), SessionSlotStatus.OPEN, 6000);
        target.venueId = 22L;
        stubReschedule(booking, target);

        assertThatThrownBy(() -> service.rescheduleOffline(21L, 99L, 7L, 20L, "调整"))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.NOT_FOUND.getDescription());
    }

    @Test
    void rejectsDifferentPriceOfflineReschedule() {
        Booking booking = offlineBooking(7L, 30L, BookingPaymentStatus.PAID);
        booking.amountFen = 6000;
        stubReschedule(booking, datedSlot(20L, LocalDate.now().plusDays(2), SessionSlotStatus.OPEN, 7000));

        assertThatThrownBy(() -> service.rescheduleOffline(21L, 99L, 7L, 20L, "调整"))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.INVALID_ARGUMENT.getDescription());
    }

    @Test
    void aggregatesDailyOperationReport() {
        when(bookingMapper.selectOperationReport(any(), any(), any())).thenReturn(List.of(
                new OperationReportDailyBO("2026-09-01", 3L, 2L, 1L, 1L, 0L, 10000L),
                new OperationReportDailyBO("2026-09-02", 4L, 3L, 2L, 0L, 1L, 18000L)
        ));

        OperationReportVO report = service.getOperationReport(null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));

        assertThat(report.getBookingCount()).isEqualTo(7L);
        assertThat(report.getEffectiveBookingCount()).isEqualTo(5L);
        assertThat(report.getCompletedCount()).isEqualTo(3L);
        assertThat(report.getCancelledCount()).isEqualTo(1L);
        assertThat(report.getNoShowCount()).isEqualTo(1L);
        assertThat(report.getAmountFen()).isEqualTo(28000L);
        assertThat(report.getDaily()).hasSize(2);
    }

    @Test
    void calculatesExactMonthlyServiceFeesWithoutRounding() {
        when(bookingMapper.selectServiceFeeBookings(any(), any(), any())).thenReturn(List.of(
                new ServiceFeeBookingBO("YC1", 1L, "一号场馆", "1 号场",
                        "2026-09-01T10:00:00+08:00", "2026-09-01T11:00:00+08:00",
                        com.sportsbooking.admin.base.enums.BookingStatus.COMPLETED, 9999, 525, 525),
                new ServiceFeeBookingBO("YC2", 2L, "二号场馆", "2 号场",
                        "2026-09-02T10:00:00+08:00", "2026-09-02T11:00:00+08:00",
                        com.sportsbooking.admin.base.enums.BookingStatus.NO_SHOW, 10000, 500, 500)
        ));

        ServiceFeeSettlementVO settlement = service.getServiceFeeSettlement(null, YearMonth.of(2026, 9));

        assertThat(settlement.getBillableBookingCount()).isEqualTo(2L);
        assertThat(settlement.getBookingAmountFen()).isEqualTo(19999L);
        assertThat(settlement.getServiceFeeYuan()).isEqualTo("10.249475");
        assertThat(settlement.getBookings().get(0).getServiceFeeYuan()).isEqualTo("5.249475");
        assertThat(settlement.getVenues()).hasSize(2);

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(bookingMapper).selectServiceFeeBookings(isNull(), startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(Instant.parse("2026-08-31T16:00:00Z"));
        assertThat(endCaptor.getValue()).isEqualTo(Instant.parse("2026-09-30T16:00:00Z"));
    }

    private void stubReschedule(Booking booking, SessionSlot target) {
        booking.amountFen = booking.amountFen == null ? 6000 : booking.amountFen;
        booking.startAt = Instant.now().plusSeconds(7200);
        SessionSlot original = datedSlot(booking.sessionSlotId, LocalDate.now().plusDays(1),
                SessionSlotStatus.BOOKED, booking.amountFen);
        original.holdBookingId = booking.id;
        when(bookingMapper.selectById(booking.id)).thenReturn(booking);
        when(bookingMapper.selectForUpdate(booking.id)).thenReturn(booking);
        when(sessionSlotMapper.selectById(original.id)).thenReturn(original);
        when(sessionSlotMapper.selectById(target.id)).thenReturn(target);
        when(bookingMapper.selectSlotsForUpdate(List.of(target.id, original.id))).thenReturn(List.of(target, original));
    }

    private Booking offlineBooking(long id, long sessionSlotId, BookingPaymentStatus paymentStatus) {
        Booking booking = new Booking();
        booking.id = id;
        booking.venueId = 21L;
        booking.courtId = 31L;
        booking.sessionSlotId = sessionSlotId;
        booking.source = BookingSource.OFFLINE;
        booking.status = BookingStatus.CONFIRMED;
        booking.paymentStatus = paymentStatus;
        booking.amountFen = 6000;
        booking.startAt = Instant.now().plusSeconds(7200);
        return booking;
    }

    private SessionSlot datedSlot(long id, LocalDate date, SessionSlotStatus status, int priceFen) {
        SessionSlot slot = openSlot(date.atTime(10, 0).atZone(BusinessConstants.BUSINESS_ZONE).toInstant());
        slot.id = id;
        slot.slotDate = date;
        slot.status = status;
        slot.priceFen = priceFen;
        return slot;
    }

    private Court court(long id) {
        Court court = new Court();
        court.status = EnableStatus.ACTIVE;
        court.id = id;
        court.name = "篮球 1 号场";
        return court;
    }

    private SportType sport(long id) {
        SportType sport = new SportType();
        sport.status = EnableStatus.ACTIVE;
        sport.id = id;
        sport.name = "篮球";
        return sport;
    }

    private SessionSlot openSlot(Instant startAt) {
        SessionSlot slot = new SessionSlot();
        slot.id = 11L;
        slot.venueId = 21L;
        slot.courtId = 31L;
        slot.sportTypeId = 41L;
        slot.startAt = startAt;
        slot.slotDate = startAt.atZone(BusinessConstants.BUSINESS_ZONE).toLocalDate();
        slot.endAt = startAt.plusSeconds(3600);
        slot.priceFen = 6000;
        slot.status = SessionSlotStatus.OPEN;
        return slot;
    }

    private Venue venue(int minBookingMinutes) {
        Venue venue = new Venue();
        venue.id = 21L;
        venue.status = com.sportsbooking.admin.base.enums.VenueStatus.ACTIVE;
        venue.bookingDaysAhead = 7;
        venue.name = "测试场馆";
        venue.minBookingMinutes = minBookingMinutes;
        venue.serviceFeeRateBasisPoints = 0;
        return venue;
    }
}
