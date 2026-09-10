package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.WeeklySlot;
import java.time.LocalTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.sportsbooking.admin.base.enums.EnableStatus;

/**
 * 功能描述: 周场次模板数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface WeeklySlotMapper extends BaseMapper<WeeklySlot> {
    /**
     * 功能描述: 查询场馆周场次模板并可按状态筛选。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param status 模板状态，为空时查询全部
     * @return 周场次模板列表
     * @author gjm
     */
    List<WeeklySlot> selectByVenue(@Param("venueId") long venueId, @Param("status") EnableStatus status);

    /**
     * 功能描述: 统计同场地同星期的重叠有效模板。
     * 创建时间: 2026/08/28 16:29
     *
     * @param courtId 场地 ID
     * @param dayOfWeek 星期
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param excludedId 编辑时排除的模板 ID
     * @return 重叠模板数量
     * @author gjm
     */
    int countOverlapping(@Param("courtId") long courtId, @Param("dayOfWeek") int dayOfWeek,
                         @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
                         @Param("excludedId") Long excludedId);
}
