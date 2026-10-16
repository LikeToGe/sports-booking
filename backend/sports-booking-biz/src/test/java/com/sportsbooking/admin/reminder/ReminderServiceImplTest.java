package com.sportsbooking.admin.reminder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.mapper.NotificationMapper;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.ReminderSubscriptionMapper;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.base.mapper.SlotReleaseEventMapper;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.po.SlotReleaseEvent;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.biz.service.ContentMessageService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.impl.ReminderServiceImpl;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.ReminderSubscriptionRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 到场提醒使用预约状态查询并复用消息幂等入口。 */
class ReminderServiceImplTest {
    @Test
    void rejectsSecondsAndNanosBeforeReadingOrWritingSubscriptionFacts() {
        UserAccountMapper users = mock(UserAccountMapper.class);
        ReminderSubscriptionMapper subscriptions = mock(ReminderSubscriptionMapper.class);
        SessionSlotMapper slots = mock(SessionSlotMapper.class);
        ReminderServiceImpl service = new ReminderServiceImpl(subscriptions, mock(SlotReleaseEventMapper.class),
                slots, mock(NotificationMapper.class), mock(BookingMapper.class), mock(ContentMessageService.class),
                mock(VenueService.class), mock(VenueMapper.class), users);
        LocalTime start = LocalTime.of(19, 0), end = LocalTime.of(21, 0);
        for (LocalTime[] times : List.of(new LocalTime[]{start.plusSeconds(30), end},
                new LocalTime[]{start, end.plusSeconds(30)}, new LocalTime[]{start.plusNanos(1), end},
                new LocalTime[]{start, end.plusNanos(1)})) {
            ReminderSubscriptionRequest request = new ReminderSubscriptionRequest(
                    1L, 1L, LocalDate.now().plusDays(1), times[0], times[1], "OPENING");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.subscribe(8L, request))
                    .isInstanceOfSatisfying(ApiException.class, exception ->
                            org.assertj.core.api.Assertions.assertThat(exception.getStatus().value()).isEqualTo(400));
        }
        org.mockito.Mockito.verifyNoInteractions(users, subscriptions, slots);
    }

    @Test
    void runJobsGenerateMessagesForCommittedEventBatch() {
        NotificationMapper notifications = mock(NotificationMapper.class);
        ReminderSubscriptionMapper subscriptions = mock(ReminderSubscriptionMapper.class);
        SlotReleaseEventMapper events = mock(SlotReleaseEventMapper.class);
        ContentMessageService messages = mock(ContentMessageService.class);
        when(events.selectUnprocessedIds(200)).thenReturn(List.of(3L, 4L));
        ReminderServiceImpl service = new ReminderServiceImpl(subscriptions, events,
                mock(SessionSlotMapper.class), notifications, mock(BookingMapper.class), messages,
                mock(VenueService.class), mock(VenueMapper.class), mock(UserAccountMapper.class));

        service.runReminderJobs();

        verify(notifications).insertDueArrivalAlerts();
        verify(notifications).insertReadyOpeningAlerts();
        verify(events).selectUnprocessedIds(200);
        verify(notifications).insertReleasedAlerts(List.of(3L, 4L));
        verify(events).markProcessed(List.of(3L, 4L));
    }

    @Test
    void confirmedNearStartUsesSameArrivalIdempotencyAsScheduler() {
        BookingMapper bookings = mock(BookingMapper.class);
        ContentMessageService messages = mock(ContentMessageService.class);
        Booking booking = new Booking();
        booking.id = 7L;
        booking.userId = 8L;
        booking.source = BookingSource.ONLINE;
        booking.status = BookingStatus.CONFIRMED;
        booking.startAt = Instant.now().plusSeconds(3600);
        booking.venueName = "本地测试馆";
        when(bookings.selectById(7L)).thenReturn(booking);
        ReminderServiceImpl service = new ReminderServiceImpl(mock(ReminderSubscriptionMapper.class),
                mock(SlotReleaseEventMapper.class), mock(SessionSlotMapper.class), mock(NotificationMapper.class),
                bookings, messages, mock(VenueService.class), mock(VenueMapper.class), mock(UserAccountMapper.class));

        service.generateArrivalIfDue(7L);

        verify(messages).recordArrivalIfEligible(eq(7L), eq("到场提醒"),
                org.mockito.ArgumentMatchers.contains("本地测试馆"));
    }

    @Test
    void releaseOnlyPersistsEventUntilIndependentJobRuns() {
        ReminderSubscriptionMapper subscriptions = mock(ReminderSubscriptionMapper.class);
        SlotReleaseEventMapper events = mock(SlotReleaseEventMapper.class);
        SessionSlotMapper slots = mock(SessionSlotMapper.class);
        ContentMessageService messages = mock(ContentMessageService.class);
        SessionSlot slot = new SessionSlot();
        slot.id = 12L;
        slot.status = SessionSlotStatus.OPEN;
        slot.startAt = Instant.now().plusSeconds(3600);
        slot.updatedAt = java.time.LocalDateTime.now();
        when(slots.selectById(12L)).thenReturn(slot);
        ReminderServiceImpl service = new ReminderServiceImpl(subscriptions, events, slots,
                mock(NotificationMapper.class), mock(BookingMapper.class), messages,
                mock(VenueService.class), mock(VenueMapper.class), mock(UserAccountMapper.class));

        service.onSlotReleased(12L, "booking:34");

        verify(events).insertIdempotent(any(SlotReleaseEvent.class));
        org.mockito.Mockito.verifyNoInteractions(messages, subscriptions);
    }
}
