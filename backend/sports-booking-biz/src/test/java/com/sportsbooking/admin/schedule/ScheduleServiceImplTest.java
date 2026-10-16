package com.sportsbooking.admin.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.base.bo.SessionSlotBO;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.biz.pojo.request.ExtraSlotCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.WeeklySlotSaveRequest;
import com.sportsbooking.admin.biz.pojo.vo.SessionSlotVO;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.WeeklySlotMapper;
import com.sportsbooking.admin.biz.service.impl.ScheduleServiceImpl;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.base.po.WeeklySlot;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * 功能描述: 同一物理场地重叠排期规则单元测试。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
class ScheduleServiceImplTest {

    @Test
    void explainsUnsupportedSportAndMissingSelectionWithoutWritingInventory() {
        CourtMapper courts = mock(CourtMapper.class);
        SportTypeMapper sports = mock(SportTypeMapper.class);
        SessionSlotMapper slots = mock(SessionSlotMapper.class);
        Court court = new Court();
        court.id = 1L;
        court.venueId = 1L;
        court.status = EnableStatus.ACTIVE;
        SportType sport = new SportType();
        sport.id = 3L;
        sport.status = EnableStatus.ACTIVE;
        when(courts.selectById(1L)).thenReturn(court);
        when(sports.selectById(3L)).thenReturn(sport);
        when(courts.selectSportIds(1L)).thenReturn(List.of(2L));
        ScheduleServiceImpl service = new ScheduleServiceImpl(mock(WeeklySlotMapper.class), slots,
                courts, sports, mock(VenueService.class), lockService(), mock(BookingMapper.class));
        var body = new com.sportsbooking.admin.biz.pojo.request.ExtraSlotCreateRequest(
                1L, 3L, LocalDate.now().plusDays(1), LocalTime.of(9, 2), LocalTime.of(10, 0), 6000);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createExtra(1L, body))
                .isInstanceOf(ApiException.class).hasMessage("所选场地不支持该运动，请重新选择运动类型");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createExtra(1L, null))
                .isInstanceOf(ApiException.class).hasMessage("请选择场地");
        verify(slots, never()).insertIgnore(any());
    }

    @Test
    void returnsFilteredDayBoardWithBookingDetailsInOneMapperQuery() {
        WeeklySlotMapper weeklySlotMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        VenueService venueService = mock(VenueService.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl(
                weeklySlotMapper, sessionSlotMapper, mock(CourtMapper.class),
                mock(SportTypeMapper.class), venueService, lockService(), mock(BookingMapper.class));
        LocalDate date = LocalDate.of(2026, 9, 20);
        SessionSlotBO slot = new SessionSlotBO();
        slot.setId(11L);
        slot.setCourtId(12L);
        slot.setCourtName("1号场");
        slot.setSportTypeId(3L);
        slot.setSportName("羽毛球");
        slot.setStartAt("2026-09-20T10:00:00+08:00");
        slot.setEndAt("2026-09-20T11:00:00+08:00");
        slot.setPriceFen(6000);
        slot.setStatus(SessionSlotStatus.BOOKED);
        slot.setBookingId(21L);
        slot.setBookingSource(BookingSource.OFFLINE);
        slot.setBookingStatus(BookingStatus.CONFIRMED);
        slot.setPaymentStatus(BookingPaymentStatus.UNPAID);
        slot.setContactName("测试顾客");
        slot.setContactPhone("13800000000");
        when(weeklySlotMapper.selectByVenue(2L, EnableStatus.ACTIVE)).thenReturn(List.of());
        when(sessionSlotMapper.selectVenueDay(2L, date, 3L)).thenReturn(List.of(slot));

        List<SessionSlotVO> result = service.listDay(2L, date, 3L);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(11L);
            assertThat(item.getCourtId()).isEqualTo(12L);
            assertThat(item.getCourtName()).isEqualTo("1号场");
            assertThat(item.getSportTypeId()).isEqualTo(3L);
            assertThat(item.getSportName()).isEqualTo("羽毛球");
            assertThat(item.getStartAt()).isEqualTo("2026-09-20T10:00:00+08:00");
            assertThat(item.getEndAt()).isEqualTo("2026-09-20T11:00:00+08:00");
            assertThat(item.getPriceFen()).isEqualTo(6000);
            assertThat(item.getStatus()).isEqualTo(SessionSlotStatus.BOOKED);
            assertThat(item.getBookingId()).isEqualTo(21L);
            assertThat(item.getBookingSource()).isEqualTo(BookingSource.OFFLINE);
            assertThat(item.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(item.getPaymentStatus()).isEqualTo(BookingPaymentStatus.UNPAID);
            assertThat(item.getContactName()).isEqualTo("测试顾客");
            assertThat(item.getContactPhone()).isEqualTo("13800000000");
        });
        verify(sessionSlotMapper).selectVenueDay(2L, date, 3L);
    }

    @Test
    void keepsUnfilteredListDayBehavior() {
        WeeklySlotMapper weeklySlotMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl(
                weeklySlotMapper, sessionSlotMapper, mock(CourtMapper.class), mock(SportTypeMapper.class),
                mock(VenueService.class), lockService(), mock(BookingMapper.class));
        LocalDate date = LocalDate.of(2026, 9, 20);
        when(weeklySlotMapper.selectByVenue(2L, EnableStatus.ACTIVE)).thenReturn(List.of());
        when(sessionSlotMapper.selectVenueDay(2L, date, null)).thenReturn(List.of());

        assertThat(service.listDay(2L, date)).isEmpty();

        verify(sessionSlotMapper).selectVenueDay(2L, date, null);
    }

    @Test
    void reopensFutureClosedSlot() {
        WeeklySlotMapper weeklySlotMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        CourtMapper courtMapper = mock(CourtMapper.class);
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        VenueService venueService = mock(VenueService.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl(
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, venueService, lockService(), mock(BookingMapper.class));
        SessionSlot slot = closedSlot();
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);

        service.openSlot(2L, 11L);

        assertThat(slot.status).isEqualTo(SessionSlotStatus.OPEN);
        verify(sessionSlotMapper).countOverlappingExcludingSlot(1L, slot.startAt, slot.endAt, 11L);
        verify(sessionSlotMapper).updateById(slot);
    }

    @Test
    void rejectsReopeningOverlappingClosedSlot() {
        WeeklySlotMapper weeklySlotMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        CourtMapper courtMapper = mock(CourtMapper.class);
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        VenueService venueService = mock(VenueService.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl(
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, venueService, lockService(), mock(BookingMapper.class));
        SessionSlot slot = closedSlot();
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(sessionSlotMapper.countOverlappingExcludingSlot(1L, slot.startAt, slot.endAt, 11L)).thenReturn(1);

        assertThatThrownBy(() -> service.openSlot(2L, 11L))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.SLOT_CONFLICT.getDescription());
    }

    @Test
    void rejectsOverlappingWeeklySlot() {
        WeeklySlotMapper weeklySlotMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        CourtMapper courtMapper = mock(CourtMapper.class);
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        VenueService venueService = mock(VenueService.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl(
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, venueService, lockService(), mock(BookingMapper.class));
        Court court = new Court();
        court.id = 1L;
        court.venueId = 2L;
        court.status = EnableStatus.ACTIVE;
        SportType sport = new SportType();
        sport.id = 3L;
        sport.status = EnableStatus.ACTIVE;
        when(courtMapper.selectById(1L)).thenReturn(court);
        when(sportTypeMapper.selectById(3L)).thenReturn(sport);
        when(courtMapper.selectSportIds(1L)).thenReturn(List.of(3L));
        when(courtMapper.selectForUpdate(1L)).thenReturn(court);
        when(weeklySlotMapper.countOverlapping(
                1L, 1, LocalTime.of(10, 30), LocalTime.of(11, 30), null)).thenReturn(1);

        WeeklySlotSaveRequest body = new WeeklySlotSaveRequest(
                1L, 3L, 1, LocalTime.of(10, 30), LocalTime.of(11, 30), 6000);

        assertThatThrownBy(() -> service.saveWeekly(2L, null, body))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.SLOT_CONFLICT.getDescription());
    }

    @Test
    void preservesExistingDisabledSportWhenEditingWeeklySlot() {
        WeeklySlotMapper weeklySlotMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        CourtMapper courtMapper = mock(CourtMapper.class);
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        VenueService venueService = mock(VenueService.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl(
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, venueService, lockService(), mock(BookingMapper.class));
        Court court = new Court();
        court.id = 1L;
        court.venueId = 2L;
        court.status = EnableStatus.ACTIVE;
        SportType sport = new SportType();
        sport.id = 3L;
        sport.status = EnableStatus.DISABLED;
        WeeklySlot weekly = new WeeklySlot();
        weekly.id = 7L;
        weekly.courtId = 1L;
        weekly.sportTypeId = 3L;
        Venue venue = new Venue();
        venue.id = 2L;
        venue.bookingDaysAhead = 0;
        when(weeklySlotMapper.selectById(7L)).thenReturn(weekly);
        when(courtMapper.selectById(1L)).thenReturn(court);
        when(sportTypeMapper.selectById(3L)).thenReturn(sport);
        when(courtMapper.selectSportIds(1L)).thenReturn(List.of(3L));
        when(courtMapper.selectForUpdate(1L)).thenReturn(court);
        when(venueService.getVenue(2L)).thenReturn(venue);
        WeeklySlotSaveRequest body = new WeeklySlotSaveRequest(
                1L, 3L, 1, LocalTime.of(9, 0), LocalTime.of(10, 0), 6000);

        service.saveWeekly(2L, 7L, body);

        verify(weeklySlotMapper).updateById(weekly);
    }

    @Test
    void editsBookedSessionWithoutChangingInventoryStateOrBookingLink() {
        WeeklySlotMapper weeklySlotMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        CourtMapper courtMapper = mock(CourtMapper.class);
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl(
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper,
                mock(VenueService.class), lockService(), mock(BookingMapper.class));
        SessionSlot slot = new SessionSlot();
        slot.id = 11L;
        slot.venueId = 2L;
        slot.courtId = 1L;
        slot.sportTypeId = 3L;
        slot.status = SessionSlotStatus.BOOKED;
        slot.holdBookingId = 99L;
        Court court = new Court();
        court.id = 1L;
        court.venueId = 2L;
        court.status = EnableStatus.ACTIVE;
        SportType sport = new SportType();
        sport.id = 3L;
        sport.status = EnableStatus.ACTIVE;
        when(sessionSlotMapper.selectById(11L)).thenReturn(slot);
        when(sessionSlotMapper.selectForUpdate(11L)).thenReturn(slot);
        when(courtMapper.selectById(1L)).thenReturn(court);
        when(sportTypeMapper.selectById(3L)).thenReturn(sport);
        when(courtMapper.selectSportIds(1L)).thenReturn(List.of(3L));
        ExtraSlotCreateRequest body = new ExtraSlotCreateRequest(
                1L, 3L, LocalDate.of(2026, 9, 20), LocalTime.of(15, 0), LocalTime.of(16, 0), 8000);

        SessionSlot result = service.updateSession(2L, 11L, body);

        assertThat(result.status).isEqualTo(SessionSlotStatus.BOOKED);
        assertThat(result.holdBookingId).isEqualTo(99L);
        assertThat(result.manualOverride).isTrue();
        assertThat(result.priceFen).isEqualTo(8000);
        verify(sessionSlotMapper).updateById(slot);
    }

    @Test
    void doesNotRegenerateTemplateOccurrenceAfterManualDateOverride() {
        WeeklySlotMapper weeklySlotMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        VenueService venueService = mock(VenueService.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl(
                weeklySlotMapper, sessionSlotMapper, mock(CourtMapper.class),
                mock(SportTypeMapper.class), venueService, lockService(), mock(BookingMapper.class));
        LocalDate date = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        WeeklySlot weekly = new WeeklySlot();
        weekly.id = 7L;
        weekly.courtId = 1L;
        weekly.dayOfWeek = date.getDayOfWeek().getValue();
        Venue venue = new Venue();
        venue.id = 2L;
        venue.bookingDaysAhead = 1;
        when(venueService.getVenue(2L)).thenReturn(venue);
        when(weeklySlotMapper.selectByVenue(2L, EnableStatus.ACTIVE)).thenReturn(List.of(weekly));
        SessionSlot override = new SessionSlot();
        override.manualOverride = true;
        when(sessionSlotMapper.selectTemplateForUpdate(7L, date)).thenReturn(override);

        service.syncVenue(2L);

        verify(sessionSlotMapper, never()).updateOpenForTemplate(any());
        verify(sessionSlotMapper, never()).insertIgnore(any());
    }

    @Test
    void rejectsExtraInventoryOverlappingAnUnchangedBookingSnapshot() {
        SessionSlotMapper slots = mock(SessionSlotMapper.class);
        BookingMapper bookings = mock(BookingMapper.class);
        CourtMapper courts = mock(CourtMapper.class);
        SportTypeMapper sports = mock(SportTypeMapper.class);
        Court court = new Court();
        court.id = 1L;
        court.venueId = 2L;
        court.status = EnableStatus.ACTIVE;
        SportType sport = new SportType();
        sport.id = 3L;
        sport.status = EnableStatus.ACTIVE;
        when(courts.selectById(1L)).thenReturn(court);
        when(courts.selectSportIds(1L)).thenReturn(List.of(3L));
        when(sports.selectById(3L)).thenReturn(sport);
        LocalDate day = LocalDate.now(BusinessConstants.BUSINESS_ZONE).plusDays(1);
        Instant start = day.atTime(19, 0).atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        when(bookings.selectOverlappingActiveForUpdate(1L, start, start.plusSeconds(3600), null, null))
                .thenReturn(List.of(99L));
        ScheduleServiceImpl service = new ScheduleServiceImpl(mock(WeeklySlotMapper.class), slots, courts,
                sports, mock(VenueService.class), lockService(), bookings);

        assertThatThrownBy(() -> service.createExtra(2L, new ExtraSlotCreateRequest(
                1L, 3L, day, LocalTime.of(19, 0), LocalTime.of(20, 0), 1000)))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.SLOT_CONFLICT.getDescription());
        verify(slots, never()).insertIgnore(any());
    }

    @Test
    void rejectsReopeningSlotOverAnExistingBookingSnapshot() {
        SessionSlotMapper slots = mock(SessionSlotMapper.class);
        BookingMapper bookings = mock(BookingMapper.class);
        SessionSlot slot = closedSlot();
        when(slots.selectById(11L)).thenReturn(slot);
        when(slots.selectForUpdate(11L)).thenReturn(slot);
        when(bookings.selectOverlappingActiveForUpdate(1L, slot.startAt, slot.endAt, null, null))
                .thenReturn(List.of(99L));
        ScheduleServiceImpl service = new ScheduleServiceImpl(mock(WeeklySlotMapper.class), slots,
                mock(CourtMapper.class), mock(SportTypeMapper.class), mock(VenueService.class), lockService(), bookings);

        assertThatThrownBy(() -> service.openSlot(2L, 11L))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.SLOT_CONFLICT.getDescription());
        verify(slots, never()).updateById(any(SessionSlot.class));
    }

    @Test
    void skipsOccupiedTemplateOccurrenceWithoutMakingDuplicateOrSelfConflict() {
        WeeklySlotMapper weeklyMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper slots = mock(SessionSlotMapper.class);
        BookingMapper bookings = mock(BookingMapper.class);
        VenueService venues = mock(VenueService.class);
        LocalDate day = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        WeeklySlot weekly = new WeeklySlot();
        weekly.id = 7L;
        weekly.courtId = 1L;
        weekly.dayOfWeek = day.getDayOfWeek().getValue();
        Venue venue = new Venue();
        venue.id = 2L;
        venue.bookingDaysAhead = 1;
        SessionSlot occupied = new SessionSlot();
        occupied.id = 11L;
        occupied.status = SessionSlotStatus.BOOKED;
        when(venues.getVenue(2L)).thenReturn(venue);
        when(weeklyMapper.selectByVenue(2L, EnableStatus.ACTIVE)).thenReturn(List.of(weekly));
        when(slots.selectTemplateForUpdate(7L, day)).thenReturn(occupied);
        Booking ownSnapshot = new Booking();
        ownSnapshot.courtId = 1L;
        ownSnapshot.startAt = day.atTime(19, 0).atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        ownSnapshot.endAt = ownSnapshot.startAt.plusSeconds(3600);
        when(bookings.selectActiveSnapshotsForUpdate(List.of(1L),
                day.atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant(),
                day.plusDays(1).atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant()))
                .thenReturn(List.of(ownSnapshot));
        ScheduleServiceImpl service = new ScheduleServiceImpl(weeklyMapper, slots, mock(CourtMapper.class),
                mock(SportTypeMapper.class), venues, lockService(), bookings);

        service.syncVenue(2L);

        verify(slots, never()).insertIgnore(any());
        verify(slots, never()).updateOpenForTemplate(any());
        verify(bookings, never()).selectOverlappingActiveForUpdate(
                org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), any());
    }

    @Test
    void usesOneWindowSnapshotReadAndRejectsConflictOnLastGeneratedDay() {
        WeeklySlotMapper weeklies = mock(WeeklySlotMapper.class);
        SessionSlotMapper slots = mock(SessionSlotMapper.class);
        BookingMapper bookings = mock(BookingMapper.class);
        VenueService venues = mock(VenueService.class);
        CourtMapper courts = mock(CourtMapper.class);
        LocalDate day = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        Venue venue = new Venue();
        venue.id = 2L;
        venue.bookingDaysAhead = 8;
        WeeklySlot first = new WeeklySlot();
        first.id = 7L;
        first.courtId = 1L;
        first.sportTypeId = 3L;
        first.dayOfWeek = day.getDayOfWeek().getValue();
        first.startTime = LocalTime.of(19, 0);
        first.endTime = LocalTime.of(20, 0);
        first.priceFen = 1000;
        WeeklySlot second = new WeeklySlot();
        second.id = 8L;
        second.courtId = 1L;
        second.sportTypeId = 3L;
        second.dayOfWeek = first.dayOfWeek;
        second.startTime = LocalTime.of(20, 0);
        second.endTime = LocalTime.of(21, 0);
        second.priceFen = 1000;
        Booking adjacent = new Booking();
        adjacent.courtId = 1L;
        adjacent.startAt = day.atTime(18, 0).atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        adjacent.endAt = day.atTime(19, 0).atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        Booking overlapping = new Booking();
        overlapping.courtId = 1L;
        overlapping.startAt = day.plusDays(7).atTime(20, 30)
                .atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        overlapping.endAt = overlapping.startAt.plusSeconds(3600);
        Instant start = day.atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        Instant end = day.plusDays(8).atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        when(venues.getVenue(2L)).thenReturn(venue);
        when(weeklies.selectByVenue(2L, EnableStatus.ACTIVE)).thenReturn(List.of(first, second));
        when(bookings.selectActiveSnapshotsForUpdate(List.of(1L), start, end))
                .thenReturn(List.of(adjacent, overlapping));
        ScheduleServiceImpl service = new ScheduleServiceImpl(weeklies, slots, courts,
                mock(SportTypeMapper.class), venues, lockService(), bookings);

        assertThatThrownBy(() -> service.syncVenue(2L))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.SLOT_CONFLICT.getDescription());

        verify(bookings).selectActiveSnapshotsForUpdate(List.of(1L), start, end);
        verify(bookings, never()).selectOverlappingActiveForUpdate(
                org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), any());
        org.mockito.InOrder order = inOrder(courts, bookings, slots);
        order.verify(courts).selectVenueForUpdate(2L);
        order.verify(bookings).selectActiveSnapshotsForUpdate(List.of(1L), start, end);
        order.verify(slots).selectTemplateForUpdate(7L, day);
    }

    @Test
    void locksOriginalAndTargetCourtsBeforeEditingAndExcludesOnlyOwnSlotSnapshot() {
        SessionSlotMapper slots = mock(SessionSlotMapper.class);
        CourtMapper courts = mock(CourtMapper.class);
        SportTypeMapper sports = mock(SportTypeMapper.class);
        BookingMapper bookings = mock(BookingMapper.class);
        SessionSlot slot = closedSlot();
        slot.courtId = 2L;
        slot.sportTypeId = 3L;
        slot.status = SessionSlotStatus.BOOKED;
        slot.holdBookingId = 99L;
        Court target = new Court();
        target.id = 1L;
        target.venueId = 2L;
        target.status = EnableStatus.ACTIVE;
        SportType sport = new SportType();
        sport.id = 3L;
        sport.status = EnableStatus.ACTIVE;
        when(slots.selectById(11L)).thenReturn(slot);
        when(slots.selectForUpdate(11L)).thenReturn(slot);
        when(courts.selectById(1L)).thenReturn(target);
        when(courts.selectSportIds(1L)).thenReturn(List.of(3L));
        when(sports.selectById(3L)).thenReturn(sport);
        ScheduleServiceImpl service = new ScheduleServiceImpl(mock(WeeklySlotMapper.class), slots, courts,
                sports, mock(VenueService.class), lockService(), bookings);
        LocalDate day = LocalDate.now(BusinessConstants.BUSINESS_ZONE).plusDays(1);

        service.updateSession(2L, 11L, new ExtraSlotCreateRequest(
                1L, 3L, day, LocalTime.of(19, 0), LocalTime.of(20, 0), 1000));

        org.mockito.InOrder order = inOrder(courts, slots);
        order.verify(courts).selectIdsForUpdate(List.of(1L, 2L));
        order.verify(slots).selectForUpdate(11L);
        verify(bookings).selectOverlappingActiveForUpdate(
                eq(1L), any(), any(), eq(null), eq(11L));
        assertThat(slot.holdBookingId).isEqualTo(99L);
        assertThat(slot.status).isEqualTo(SessionSlotStatus.BOOKED);
    }

    @Test
    void explicitlyClearsPersistedBookingHoldWhenReleased() {
        SessionSlotMapper slots = mock(SessionSlotMapper.class);
        SessionSlot slot = closedSlot();
        slot.status = SessionSlotStatus.HELD;
        slot.holdBookingId = 99L;
        slot.holdExpiresAt = Instant.now().plusSeconds(30);
        when(slots.selectForUpdate(11L)).thenReturn(slot);
        ScheduleServiceImpl service = new ScheduleServiceImpl(mock(WeeklySlotMapper.class), slots,
                mock(CourtMapper.class), mock(SportTypeMapper.class), mock(VenueService.class), lockService(),
                mock(BookingMapper.class));

        service.releaseBookingSlot(11L);

        verify(slots).clearBookingHold(slot);
        verify(slots, never()).updateById(any(SessionSlot.class));
        assertThat(slot.holdBookingId).isNull();
        assertThat(slot.holdExpiresAt).isNull();
        assertThat(slot.status).isEqualTo(SessionSlotStatus.OPEN);
    }

    /**
     * 功能描述: 构造未来可重新开启的已关闭临时场次。
     *
     * @return 已关闭场次
     * @author gjm
     */
    private SessionSlot closedSlot() {
        SessionSlot slot = new SessionSlot();
        slot.id = 11L;
        slot.venueId = 2L;
        slot.courtId = 1L;
        slot.startAt = Instant.now().plusSeconds(3600);
        slot.endAt = slot.startAt.plusSeconds(3600);
        slot.status = SessionSlotStatus.CLOSED;
        return slot;
    }

    private RedisLockService lockService() {
        RedisLockService redisLockService = mock(RedisLockService.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(redisLockService).execute(any(), any());
        return redisLockService;
    }
}
