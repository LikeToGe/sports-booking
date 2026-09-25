package com.sportsbooking.admin.funds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.mapper.NotificationDeliveryMapper;
import com.sportsbooking.admin.base.mapper.NotificationMapper;
import com.sportsbooking.admin.base.po.Notification;
import com.sportsbooking.admin.base.po.NotificationDelivery;
import com.sportsbooking.admin.biz.service.NotificationDeliveryAdapter;
import com.sportsbooking.admin.biz.service.impl.NotificationServiceImpl;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/** 赠送到期提醒和 Mock 投递幂等测试。 */
class NotificationServiceImplTest {
    @Test
    void repeatedExpiryReminderCreatesOneNotificationAndDelivery() {
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        NotificationDeliveryMapper deliveryMapper = mock(NotificationDeliveryMapper.class);
        NotificationDeliveryAdapter adapter = mock(NotificationDeliveryAdapter.class);
        NotificationServiceImpl service = new NotificationServiceImpl(notificationMapper, deliveryMapper, adapter);
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
