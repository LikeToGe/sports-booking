package com.sportsbooking.admin.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.biz.pojo.vo.SessionSlotVO;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.biz.service.VenueCatalogService;
import com.sportsbooking.admin.biz.service.VenueService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DemoDataRunnerTest {

    @Test
    void seedsDistinctCourtNamesWhenBothCourtsUseTheSameSport() {
        VenueService venueService = mock(VenueService.class);
        VenueCatalogService venueCatalogService = mock(VenueCatalogService.class);
        BookingMapper bookingMapper = mock(BookingMapper.class);
        when(venueService.listPlatformVenues()).thenReturn(List.of());
        List<SportType> sports = new java.util.ArrayList<>();
        for (String name : List.of("篮球", "羽毛球", "网球", "乒乓球")) {
            SportType sport = new SportType();
            sport.id = (long) sports.size() + 1;
            sport.name = name;
            sports.add(sport);
        }
        when(venueCatalogService.listGlobalSports()).thenReturn(sports);
        when(venueService.createVenue(any(), anyString(), anyString())).thenAnswer(invocation -> {
            Venue venue = new Venue();
            venue.id = Long.valueOf(invocation.<String>getArgument(1).substring(5));
            return venue;
        });
        Set<String> courtNames = new HashSet<>();
        when(venueCatalogService.createCourt(anyLong(), anyString(), anyList())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0) + ":" + invocation.getArgument(1);
            assertThat(courtNames.add(key)).as("同一场馆的演示场地名称必须唯一").isTrue();
            Court court = new Court();
            court.id = (long) courtNames.size();
            return court;
        });

        new DemoDataRunner(venueService, venueCatalogService, mock(ScheduleService.class),
                mock(BookingService.class), bookingMapper, true).run();

        assertThat(courtNames).hasSize(6);
    }

    @Test
    void skipsExpiredOpenSlotsWhenCreatingDemoBooking() throws Exception {
        VenueService venueService = mock(VenueService.class);
        VenueCatalogService venueCatalogService = mock(VenueCatalogService.class);
        ScheduleService scheduleService = mock(ScheduleService.class);
        BookingService bookingService = mock(BookingService.class);
        BookingMapper bookingMapper = mock(BookingMapper.class);
        Venue venue = new Venue();
        venue.id = 1L;
        venue.minBookingMinutes = 30;
        LocalDate today = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        when(venueService.listPlatformVenues()).thenReturn(List.of(venue));
        when(bookingMapper.selectCount(null)).thenReturn(0L);
        when(scheduleService.listDay(venue.id, today)).thenReturn(List.of(
                openSlot(11L, Instant.now().minusSeconds(3600)),
                openSlot(12L, Instant.now().plusSeconds(7200))
        ));

        new DemoDataRunner(venueService, venueCatalogService, scheduleService, bookingService, bookingMapper, true)
                .run();

        verify(bookingService).createOffline(venue.id, 12L, "本地演示用户", "13800000000",
                BookingPaymentStatus.UNPAID, null);
    }

    private SessionSlotVO openSlot(long id, Instant startAt) {
        SessionSlotVO slot = new SessionSlotVO();
        slot.setId(id);
        slot.setStartAt(startAt.atOffset(ZoneOffset.ofHours(8)).toString());
        slot.setStatus(SessionSlotStatus.OPEN);
        return slot;
    }
}
