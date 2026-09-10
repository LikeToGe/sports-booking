package com.sportsbooking.admin.server.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.mapper.BookingMapper;
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
import org.junit.jupiter.api.Test;

class DemoDataRunnerTest {

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

        verify(bookingService).createOffline(venue.id, 12L, "本地演示用户", "13800000000");
    }

    private SessionSlotVO openSlot(long id, Instant startAt) {
        SessionSlotVO slot = new SessionSlotVO();
        slot.setId(id);
        slot.setStartAt(startAt.atOffset(ZoneOffset.ofHours(8)).toString());
        slot.setStatus(SessionSlotStatus.OPEN);
        return slot;
    }
}
