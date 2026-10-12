package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.bo.UserActionEventBO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 最小用户动作原始事件。 */
public interface UserActionEventMapper {
    int insert(@Param("userId") Long userId, @Param("eventType") String eventType,
               @Param("targetType") String targetType, @Param("targetId") long targetId);
    List<UserActionEventBO> selectRecent();
}
