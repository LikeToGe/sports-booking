package com.sportsbooking.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.bo.OperationReportDailyBO;
import com.sportsbooking.admin.base.bo.ServiceFeeBookingBO;
import com.sportsbooking.admin.biz.pojo.vo.OperationReportVO;
import com.sportsbooking.admin.biz.pojo.vo.ServiceFeeSettlementVO;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.service.PaymentService;
import com.sportsbooking.admin.biz.service.impl.BookingServiceImpl;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import com.sportsbooking.admin.biz.service.VenueService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private final SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final CourtMapper courtMapper = mock(CourtMapper.class);
    private final SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
    private final VenueService venueService = mock(VenueService.class);
    private final CredentialService credentialService = mock(CredentialService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final ScheduleService scheduleService = mock(ScheduleService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private BookingServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(1L);
        when(bookingMapper.selectCount(any())).thenReturn(0L);
        service = new BookingServiceImpl(bookingMapper, sessionSlotMapper, userAccountMapper,
                courtMapper, sportTypeMapper, venueService, credentialService, paymentService,
                scheduleService, redisTemplate, 10, 2, 5);
    }

    @Test
    void rejectsBookingInsideVenueMinimumLeadTime() {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(20 * 60L));
        Venue venue = venue(30);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
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
        sport.id = slot.sportTypeId;
        sport.name = "篮球";
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
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
    }

    @Test
    void offlineBookingDoesNotSnapshotServiceFeeRate() {
        SessionSlot slot = openSlot(Instant.now().plusSeconds(2 * 60 * 60L));
        Venue venue = venue(30);
        venue.serviceFeeRateBasisPoints = 525;
        Court court = new Court();
        court.id = slot.courtId;
        court.name = "篮球 1 号场";
        SportType sport = new SportType();
        sport.id = slot.sportTypeId;
        sport.name = "篮球";
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(venueService.getVenue(slot.venueId)).thenReturn(venue);
        when(courtMapper.selectById(slot.courtId)).thenReturn(court);
        when(sportTypeMapper.selectById(slot.sportTypeId)).thenReturn(sport);

        service.createOffline(slot.venueId, 11L, "线下用户", "13800000002");

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingMapper).insert(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().source).isEqualTo(BookingSource.OFFLINE);
        assertThat(bookingCaptor.getValue().serviceFeeRateBasisPoints).isZero();
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

    private SessionSlot openSlot(Instant startAt) {
        SessionSlot slot = new SessionSlot();
        slot.id = 11L;
        slot.venueId = 21L;
        slot.courtId = 31L;
        slot.sportTypeId = 41L;
        slot.startAt = startAt;
        slot.endAt = startAt.plusSeconds(3600);
        slot.priceFen = 6000;
        slot.status = SessionSlotStatus.OPEN;
        return slot;
    }

    private Venue venue(int minBookingMinutes) {
        Venue venue = new Venue();
        venue.id = 21L;
        venue.name = "测试场馆";
        venue.minBookingMinutes = minBookingMinutes;
        venue.serviceFeeRateBasisPoints = 0;
        return venue;
    }
}
