package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.ReminderSubscription;
import com.sportsbooking.admin.base.bo.ReminderSubscriptionBO;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 提醒登记数据访问。 */
public interface ReminderSubscriptionMapper extends BaseMapper<ReminderSubscription> {
    List<ReminderSubscriptionBO> selectByUser(@Param("userId") long userId);
    int markOpeningNotifiedForMessages();
    int cancelActive(@Param("id") long id, @Param("userId") long userId);
    int expirePast();
}
