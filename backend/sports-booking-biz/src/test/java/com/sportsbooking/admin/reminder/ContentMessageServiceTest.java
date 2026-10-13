package com.sportsbooking.admin.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.mapper.NotificationDeliveryAttemptMapper;
import com.sportsbooking.admin.base.mapper.NotificationDeliveryMapper;
import com.sportsbooking.admin.base.mapper.NotificationMapper;
import com.sportsbooking.admin.base.po.Notification;
import com.sportsbooking.admin.base.po.NotificationDelivery;
import com.sportsbooking.admin.base.po.NotificationDeliveryAttempt;
import com.sportsbooking.admin.biz.service.ContentMessageService;
import com.sportsbooking.admin.biz.service.NotificationDeliveryAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Mock 投递只在领取成功、当前仍有效且无已持久化成功时调用。 */
class ContentMessageServiceTest {
    private final NotificationMapper notifications = mock(NotificationMapper.class);
    private final NotificationDeliveryMapper deliveries = mock(NotificationDeliveryMapper.class);
    private final NotificationDeliveryAttemptMapper attempts = mock(NotificationDeliveryAttemptMapper.class);
    private final NotificationDeliveryAdapter adapter = mock(NotificationDeliveryAdapter.class);
    private final ContentMessageService service = new ContentMessageService(notifications, deliveries, attempts, adapter);

    @Test
    void restartAfterPersistedSuccessOnlyFinishesNotification() {
        Notification row = new Notification();
        row.id = 10L;
        NotificationDelivery success = new NotificationDelivery();
        success.status = "SUCCESS";
        when(notifications.selectUndeliveredContent()).thenReturn(List.of(row));
        when(notifications.claimDelivery(10L)).thenReturn(1);
        when(deliveries.selectOne(any())).thenReturn(success);

        service.deliverPending();

        verify(notifications).markMockSent(10L);
        verify(adapter, never()).deliver(any());
        verifyNoInteractions(attempts);
    }

    @Test
    void failedAdapterLeavesSameMessagePendingAndRecordsAttempt() {
        Notification row = new Notification();
        row.id = 11L;
        when(notifications.selectUndeliveredContent()).thenReturn(List.of(row));
        when(notifications.claimDelivery(11L)).thenReturn(1);
        when(notifications.countDeliveryEligible(11L)).thenReturn(1);
        when(adapter.deliver(row)).thenThrow(new IllegalStateException("本地模拟失败"));

        service.deliverPending();

        ArgumentCaptor<NotificationDeliveryAttempt> attempt = ArgumentCaptor.forClass(NotificationDeliveryAttempt.class);
        verify(attempts).insert(attempt.capture());
        assertThat(attempt.getValue().notificationId).isEqualTo(11L);
        assertThat(attempt.getValue().status).isEqualTo("FAILED");
        verify(notifications).markDeliveryPending(11L);
        verify(notifications, never()).markMockSent(11L);
    }

    @Test
    void withdrawnReminderNeverCallsAdapter() {
        Notification row = new Notification();
        row.id = 12L;
        when(notifications.selectUndeliveredContent()).thenReturn(List.of(row));
        when(notifications.claimDelivery(12L)).thenReturn(1);

        service.deliverPending();

        verify(notifications).markSkipped(12L);
        verify(adapter, never()).deliver(any());
        verifyNoInteractions(attempts);
    }
}
