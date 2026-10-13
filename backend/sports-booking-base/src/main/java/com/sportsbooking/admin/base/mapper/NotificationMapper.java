package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.Notification;
import java.util.List;

/**
 * 功能描述: 消息触发记录数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface NotificationMapper extends BaseMapper<Notification> {
    java.util.List<Notification> selectUserNotifications(@org.apache.ibatis.annotations.Param("userId") long userId);
    int markRead(@org.apache.ibatis.annotations.Param("id") long id, @org.apache.ibatis.annotations.Param("userId") long userId);
    int markMockSent(@org.apache.ibatis.annotations.Param("id") long id);
    int markSkipped(@org.apache.ibatis.annotations.Param("id") long id);
    int claimDelivery(@org.apache.ibatis.annotations.Param("id") long id);
    int markDeliveryPending(@org.apache.ibatis.annotations.Param("id") long id);
    int countDeliveryEligible(@org.apache.ibatis.annotations.Param("id") long id);
    int insertArrivalIfEligible(@org.apache.ibatis.annotations.Param("bookingId") long bookingId,
                                @org.apache.ibatis.annotations.Param("key") String key,
                                @org.apache.ibatis.annotations.Param("title") String title,
                                @org.apache.ibatis.annotations.Param("content") String content);
    int insertReadyOpeningAlerts();
    int insertReleasedAlerts(@org.apache.ibatis.annotations.Param("ids") List<Long> ids);
    List<Notification> selectUndeliveredContent();
    int insertDueArrivalAlerts();
}
