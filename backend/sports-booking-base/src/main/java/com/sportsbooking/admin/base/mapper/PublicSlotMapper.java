package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.bo.PublicSlotBO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 从具体场次读取安全公开比较事实。 */
@Mapper
public interface PublicSlotMapper {
    List<PublicSlotBO> selectComparison(@Param("venueId") long venueId, @Param("sportTypeId") Long sportTypeId,
            @Param("date") LocalDate date, @Param("now") Instant now, @Param("today") LocalDate today,
            @Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd,
            @Param("minPriceFen") Integer minPriceFen, @Param("maxPriceFen") Integer maxPriceFen);
}
