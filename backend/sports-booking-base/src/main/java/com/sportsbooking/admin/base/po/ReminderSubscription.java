package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 用户本人对一个场馆、运动、日期和时段的提醒登记。 */
@TableName("s_reminder_subscription")
public class ReminderSubscription {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Long venueId;
    public Long sportTypeId;
    public LocalDate targetDate;
    public LocalTime startTime;
    public LocalTime endTime;
    public String type;
    public String status;
    public Boolean hadOpenAtRegistration;
    public LocalDateTime openingNotifiedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
