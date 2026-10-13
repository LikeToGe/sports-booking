package com.sportsbooking.admin.funds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.mapper.NotificationDeliveryMapper;
import com.sportsbooking.admin.base.mapper.NotificationMapper;
import com.sportsbooking.admin.base.po.Notification;
import com.sportsbooking.admin.base.po.NotificationDelivery;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.biz.service.NotificationDeliveryAdapter;
import com.sportsbooking.admin.biz.service.ReminderService;
import com.sportsbooking.admin.biz.service.impl.NotificationServiceImpl;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 赠送到期提醒和 Mock 投递幂等测试。 */
class NotificationServiceImplTest {
    @Test
    void confirmedBookingGeneratesArrivalOnlyAfterCommit() {
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ReminderService reminders = mock(ReminderService.class);
        NotificationServiceImpl service = new NotificationServiceImpl(notificationMapper,
                mock(NotificationDeliveryMapper.class), mock(NotificationDeliveryAdapter.class), reminders);
        Booking booking = new Booking();
        booking.id = 7L;
        booking.userId = 8L;
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.record(booking, NotificationType.BOOKING_CONFIRMED);
            verify(reminders, never()).generateArrivalIfDue(7L);
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
            verify(reminders).generateArrivalIfDue(7L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void repeatedExpiryReminderCreatesOneNotificationAndDelivery() {
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        NotificationDeliveryMapper deliveryMapper = mock(NotificationDeliveryMapper.class);
        NotificationDeliveryAdapter adapter = mock(NotificationDeliveryAdapter.class);
        NotificationServiceImpl service = new NotificationServiceImpl(
                notificationMapper, deliveryMapper, adapter, mock(ReminderService.class));
        AtomicInteger inserts = new AtomicInteger();
        doAnswer(invocation -> {
            if (inserts.getAndIncrement() > 0) {
                throw new DuplicateKeyException("duplicate");
            }
            ((Notification) invocation.getArgument(0)).id = 10L;
            return 1;
        }).when(notificationMapper).insert(any(Notification.class));
        when(adapter.deliver(any(Notification.class))).thenReturn("MOCK-10");

        service.recordBalanceExpiry(1L, 2L, 3000, Instant.parse("2026-09-22T16:00:00Z"));
        service.recordBalanceExpiry(1L, 2L, 3000, Instant.parse("2026-09-22T16:00:00Z"));

        verify(adapter, times(1)).deliver(any(Notification.class));
        verify(deliveryMapper, times(1)).insert(any(NotificationDelivery.class));
    }
}
