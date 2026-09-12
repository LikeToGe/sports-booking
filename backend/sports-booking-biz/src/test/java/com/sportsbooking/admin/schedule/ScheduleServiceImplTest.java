package com.sportsbooking.admin.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.biz.pojo.request.ExtraSlotCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.WeeklySlotSaveRequest;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.base.mapper.WeeklySlotMapper;
import com.sportsbooking.admin.biz.service.impl.ScheduleServiceImpl;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.base.po.WeeklySlot;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import com.sportsbooking.admin.biz.service.VenueService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 功能描述: 同一物理场地重叠排期规则单元测试。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
class ScheduleServiceImplTest {

    @Test
    void reopensFutureClosedSlot() {
        WeeklySlotMapper weeklySlotMapper = mock(WeeklySlotMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        CourtMapper courtMapper = mock(CourtMapper.class);
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        VenueService venueService = mock(VenueService.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl(
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, venueService);
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
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, venueService);
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
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, venueService);
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
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, venueService);
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
                weeklySlotMapper, sessionSlotMapper, courtMapper, sportTypeMapper, mock(VenueService.class));
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
                mock(SportTypeMapper.class), venueService);
        LocalDate date = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        WeeklySlot weekly = new WeeklySlot();
        weekly.id = 7L;
        weekly.dayOfWeek = date.getDayOfWeek().getValue();
        Venue venue = new Venue();
        venue.id = 2L;
        venue.bookingDaysAhead = 1;
        when(venueService.getVenue(2L)).thenReturn(venue);
        when(weeklySlotMapper.selectByVenue(2L, EnableStatus.ACTIVE)).thenReturn(List.of(weekly));
        when(sessionSlotMapper.countManualOverride(7L, date)).thenReturn(1);

        service.syncVenue(2L);

        verify(sessionSlotMapper, never()).updateOpenForTemplate(any());
        verify(sessionSlotMapper, never()).insertIgnore(any());
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
}
