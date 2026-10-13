package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.bo.RecruitmentDiscoveryBO;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 找搭子公开发现查询。 */
public interface RecruitmentDiscoveryMapper {
    List<RecruitmentDiscoveryBO> select(@Param("userId") long userId,
            @Param("dayStart") Instant dayStart, @Param("nextDayStart") Instant nextDayStart,
            @Param("sportTypeId") Long sportTypeId, @Param("venueId") Long venueId,
            @Param("levelTag") String levelTag, @Param("minRemaining") Integer minRemaining,
            @Param("offset") int offset, @Param("size") int size);
}
