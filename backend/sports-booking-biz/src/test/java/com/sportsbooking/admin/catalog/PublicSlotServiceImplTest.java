package com.sportsbooking.admin.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sportsbooking.admin.base.bo.PublicSlotBO;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.base.mapper.PublicSlotMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.impl.PublicSlotServiceImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicSlotServiceImplTest {
    private final PublicSlotMapper slots = mock(PublicSlotMapper.class);
    private final VenueMapper venues = mock(VenueMapper.class);
    private final PublicSlotServiceImpl service = new PublicSlotServiceImpl(slots, venues);

    @Test
    void preservesWholeIntervalsPricesAndAllPublicStatuses() {
        Venue venue = new Venue();
        venue.status = VenueStatus.ACTIVE;
        when(venues.selectById(1L)).thenReturn(venue);
        var rows = List.of("OPEN", "HELD", "BOOKED", "CLOSED", "UNAVAILABLE").stream().map(status -> {
            PublicSlotBO row = new PublicSlotBO();
            row.setId(5L); row.setCourtId(2L); row.setStatus(status);
            row.setStartAt("2026-10-13T19:00:00+08:00");
            row.setEndAt("2026-10-13T21:30:00+08:00");
            row.setCancelDeadline("2026-10-13T17:00:00+08:00"); row.setPriceFen(12345);
            return row;
        }).toList();
        when(slots.selectComparison(eq(1L), eq(3L), any(), any(), any(), any(), any(), eq(100), eq(20000)))
                .thenReturn(rows);
        var result = service.compare(1L, 3L, LocalDate.of(2026, 10, 13),
                LocalTime.of(19, 0), LocalTime.of(22, 0), 100, 20000);
        assertThat(result).extracting("status").containsExactly("OPEN", "HELD", "BOOKED", "CLOSED", "UNAVAILABLE");
        assertThat(result).allSatisfy(row -> {
            assertThat(row.getEndAt()).isEqualTo("2026-10-13T21:30:00+08:00");
            assertThat(row.getPriceFen()).isEqualTo(12345);
            assertThat(row.getCancelDeadline()).isEqualTo("2026-10-13T17:00:00+08:00");
        });
        verify(slots).selectComparison(eq(1L), eq(3L), eq(LocalDate.of(2026, 10, 13)), any(), any(),
                eq(Instant.parse("2026-10-13T11:00:00Z")), eq(Instant.parse("2026-10-13T14:00:00Z")), eq(100), eq(20000));
        verifyNoMoreInteractions(slots);
    }

    @Test
    void rejectsInvalidFiltersBeforeReadingDataAndHidesInactiveVenues() {
        assertThatThrownBy(() -> service.compare(1L, null, null, LocalTime.NOON, null, null, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.compare(1L, null, null, null, null, 200, 100))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.compare(1L, null, null, null, null, -1, null))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(venues, slots);
        Venue venue = new Venue(); venue.status = VenueStatus.DRAFT;
        when(venues.selectById(1L)).thenReturn(venue);
        assertThatThrownBy(() -> service.compare(1L, null, null, null, null, null, null))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(slots);
    }
}
