package com.sportsbooking.admin.base.bo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

/** 本人提醒登记和当前场馆预约规则的一次查询投影。 */
@Data
public class ReminderSubscriptionBO {
    private Long id;
    private Long userId;
    private Long venueId;
    private Long sportTypeId;
    private LocalDate targetDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String type;
    private String status;
    private Boolean hadOpenAtRegistration;
    private LocalDateTime openingNotifiedAt;
    private Integer bookingDaysAhead;
    private Integer minBookingMinutes;
}
