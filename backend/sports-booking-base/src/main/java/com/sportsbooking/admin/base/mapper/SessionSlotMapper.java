package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.SessionSlot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.sportsbooking.admin.base.bo.SessionSlotBO;
import org.apache.ibatis.annotations.Param;

/**
 * 功能描述: 具体场次数据访问及并发锁定操作。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface SessionSlotMapper extends BaseMapper<SessionSlot> {
    List<SessionSlot> selectOpenMatching(@Param("venueId") long venueId,
                                         @Param("sportTypeId") long sportTypeId,
                                         @Param("date") LocalDate date,
                                         @Param("startTime") java.time.LocalTime startTime,
                                         @Param("endTime") java.time.LocalTime endTime);
    /**
     * 功能描述: 按 ID 加排他锁查询具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param id 具体场次 ID
     * @return 已锁定具体场次
     * @author gjm
     */
    SessionSlot selectForUpdate(long id);

    /**
     * 功能描述: 忽略唯一键冲突插入具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param slot 具体场次
     * @return 受影响行数
     * @author gjm
     */
    int insertIgnore(SessionSlot slot);

    /**
     * 功能描述: 更新周模板对应且仍开放的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param slot 新具体场次数据
     * @return 受影响行数
     * @author gjm
     */
    int updateOpenForTemplate(SessionSlot slot);

    /**
     * 功能描述: 判断模板在原生成日期是否已有人工覆盖场次。
     * 创建时间: 2026/09/11
     *
     * @param weeklySlotId 周场次 ID
     * @param templateSlotDate 原生成日期
     * @return 人工覆盖场次数量
     * @author gjm
     */
    int countManualOverride(@Param("weeklySlotId") long weeklySlotId,
                            @Param("templateSlotDate") LocalDate templateSlotDate);

    /**
     * 功能描述: 统计同一物理场地的重叠可售库存。
     * 创建时间: 2026/08/28 16:29
     *
     * @param courtId 场地 ID
     * @param startAt 开始时间
     * @param endAt 结束时间
     * @param weeklySlotId 需排除的同模板 ID
     * @return 重叠场次数量
     * @author gjm
     */
    int countOverlapping(@Param("courtId") long courtId, @Param("startAt") Instant startAt,
                         @Param("endAt") Instant endAt, @Param("weeklySlotId") Long weeklySlotId);

    /**
     * 功能描述: 编辑具体场次时统计除自身外的重叠可售库存。
     * 创建时间: 2026/09/11
     *
     * @param courtId 场地 ID
     * @param startAt 开始时间
     * @param endAt 结束时间
     * @param excludedSlotId 当前具体场次 ID
     * @return 重叠场次数量
     * @author gjm
     */
    int countOverlappingExcludingSlot(@Param("courtId") long courtId, @Param("startAt") Instant startAt,
                                      @Param("endAt") Instant endAt,
                                      @Param("excludedSlotId") long excludedSlotId);

    /**
     * 功能描述: 关闭模板对应且未被占用的未来具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param weeklySlotId 周模板 ID
     * @param now 当前时间
     * @return 受影响行数
     * @author gjm
     */
    int closeOpenForTemplate(@Param("weeklySlotId") long weeklySlotId, @Param("now") Instant now);

    /**
     * 功能描述: 统计指定日期已有占位或预约的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param date 日期
     * @return 忙碌场次数量
     * @author gjm
     */
    int countBusyForDate(@Param("venueId") long venueId, @Param("date") LocalDate date);

    /**
     * 功能描述: 关闭指定日期全部开放场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param date 日期
     * @return 受影响行数
     * @author gjm
     */
    int closeOpenForDate(@Param("venueId") long venueId, @Param("date") LocalDate date);

    /**
     * 功能描述: 查询场馆指定日期的具体场次及当前关联预约展示列表。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param date 日期
     * @param sportTypeId 运动类型 ID，为空时查询全部运动
     * @return 具体场次列表
     * @author gjm
     */
    List<SessionSlotBO> selectVenueDay(@Param("venueId") long venueId, @Param("date") LocalDate date,
                                       @Param("sportTypeId") Long sportTypeId);
}
