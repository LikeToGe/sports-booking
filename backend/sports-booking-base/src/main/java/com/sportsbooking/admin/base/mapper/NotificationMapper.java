package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.Notification;

/**
 * 功能描述: 消息触发记录数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface NotificationMapper extends BaseMapper<Notification> {
    java.util.List<Notification> selectUserNotifications(@org.apache.ibatis.annotations.Param("userId") long userId);
    int markRead(@org.apache.ibatis.annotations.Param("id") long id, @org.apache.ibatis.annotations.Param("userId") long userId);
    java.util.List<Notification> selectUndeliveredContent();
    int markMockSent(@org.apache.ibatis.annotations.Param("id") long id);
}
