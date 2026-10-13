package com.sportsbooking.admin.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbooking.admin.base.po.Notification;
import com.sportsbooking.admin.base.po.ReminderSubscription;
import com.sportsbooking.admin.base.bo.ReminderSubscriptionBO;
import com.sportsbooking.admin.biz.pojo.vo.ContentNotificationVO;
import com.sportsbooking.admin.biz.pojo.vo.ReminderSubscriptionVO;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 消息时间按 UTC Instant 出口，手机端再按郑州时区展示。 */
class ReminderMessageVOTest {
    @Test
    void convertsPersistedUtcTimesToInstants() {
        Notification notification = new Notification();
        notification.createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        notification.readAt = LocalDateTime.of(2026, 10, 13, 10, 1);
        assertThat(ContentNotificationVO.from(notification).getCreatedAt())
                .isEqualTo(Instant.parse("2026-10-13T10:00:00Z"));
        assertThat(ContentNotificationVO.from(notification).getReadAt())
                .isEqualTo(Instant.parse("2026-10-13T10:01:00Z"));

        ReminderSubscription subscription = new ReminderSubscription();
        subscription.openingNotifiedAt = LocalDateTime.of(2026, 10, 13, 10, 2);
        assertThat(ReminderSubscriptionVO.from(subscription).getOpeningNotifiedAt())
                .isEqualTo(Instant.parse("2026-10-13T10:02:00Z"));

        ReminderSubscriptionBO listed = new ReminderSubscriptionBO();
        listed.setBookingDaysAhead(7);
        listed.setMinBookingMinutes(30);
        assertThat(ReminderSubscriptionVO.from(listed).getBookingDaysAhead()).isEqualTo(7);
        assertThat(ReminderSubscriptionVO.from(listed).getMinBookingMinutes()).isEqualTo(30);
    }
}
