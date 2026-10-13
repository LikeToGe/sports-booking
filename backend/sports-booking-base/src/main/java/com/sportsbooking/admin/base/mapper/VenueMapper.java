package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.base.po.SportType;
import java.time.LocalDate;
import com.sportsbooking.admin.base.bo.SlotSearchBO;
import java.time.Instant;
import java.util.List;
import com.sportsbooking.admin.base.bo.AvailableSlotBO;
import com.sportsbooking.admin.base.bo.CourtBO;
import com.sportsbooking.admin.base.bo.PublicVenueBO;
import org.apache.ibatis.annotations.Param;

/**
 * 功能描述: 场馆及小程序场馆聚合查询数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface VenueMapper extends BaseMapper<Venue> {
    /**
     * 功能描述: 按名称、平台运动类型和日期查询首页场馆列表。
     * 创建时间: 2026/08/28 16:29
     *
     * @param keyword 场馆名称关键字
     * @param sportTypeId 平台运动类型 ID
     * @param date 预约日期
     * @param now 当前时间
     * @return 首页场馆列表
     * @author gjm
     */
    List<PublicVenueBO> selectPublicVenues(@Param("keyword") String keyword,
                                           @Param("sportTypeId") Long sportTypeId,
                                           @Param("date") LocalDate date,
                                           @Param("now") Instant now,
                                           @Param("district") String district);

    /**
     * 功能描述: 查询场馆场地及关联运动展示数据。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @return 场地展示列表
     * @author gjm
     */
    List<CourtBO> selectCourtViews(@Param("venueId") long venueId);

    /**
     * 功能描述: 查询场馆实际使用的运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @return 运动类型列表
     * @author gjm
     */
    List<SportType> selectUsedSports(@Param("venueId") long venueId);

    /**
     * 功能描述: 查询严格满足最晚预约、开放窗口和可选筛选的开放场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param sportTypeId 运动类型 ID
     * @param date 日期
     * @param earliest 最早允许预约时间
     * @return 开放场次列表
     * @author gjm
     */
    List<AvailableSlotBO> selectAvailableSlots(@Param("venueId") long venueId,
                                               @Param("sportTypeId") Long sportTypeId,
                                               @Param("date") LocalDate date,
                                               @Param("earliest") Instant earliest,
                                               @Param("today") LocalDate today,
                                               @Param("rangeStart") Instant rangeStart,
                                               @Param("rangeEnd") Instant rangeEnd,
                                               @Param("minPriceFen") Integer minPriceFen,
                                               @Param("maxPriceFen") Integer maxPriceFen);

    /** 按完整时间范围检索可订具体场次，每页最多 20 条。 */
    List<SlotSearchBO> selectMatchingSlots(@Param("keyword") String keyword,
            @Param("sportTypeId") Long sportTypeId, @Param("date") LocalDate date,
            @Param("district") String district, @Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd,
            @Param("minPriceFen") Integer minPriceFen, @Param("maxPriceFen") Integer maxPriceFen,
            @Param("now") Instant now, @Param("today") LocalDate today, @Param("offset") int offset);
}
