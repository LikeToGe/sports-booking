package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.Booking;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.sportsbooking.admin.base.bo.UserBookingBO;
import com.sportsbooking.admin.base.bo.VenueBookingBO;
import com.sportsbooking.admin.base.bo.OperationReportDailyBO;
import com.sportsbooking.admin.base.bo.ServiceFeeBookingBO;
import org.apache.ibatis.annotations.Param;

/**
 * 功能描述: 预约数据访问及列表聚合查询。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface BookingMapper extends BaseMapper<Booking> {

    /**
     * 功能描述: 按预约 ID 加排他锁查询，供状态转换事务使用。
     * 创建时间: 2026/08/28 16:29
     *
     * @param id 预约 ID
     * @return 已锁定预约
     * @author gjm
     */
    Booking selectForUpdate(long id);

    /** 在物理场地锁内当前读取有效预约快照；排除项仅用于编辑自身或同价改期。 */
    List<Long> selectOverlappingActiveForUpdate(@Param("courtId") long courtId,
                                                @Param("startAt") Instant startAt,
                                                @Param("endAt") Instant endAt,
                                                @Param("excludedBookingId") Long excludedBookingId,
                                                @Param("excludedSessionSlotId") Long excludedSessionSlotId);

    /** 批量预约在场地锁内一次当前读取区间内快照，再逐日期在内存计算冲突。 */
    List<Booking> selectActiveSnapshotsForUpdate(@Param("courtIds") List<Long> courtIds,
                                                @Param("startAt") Instant startAt,
                                                @Param("endAt") Instant endAt);

    /**
     * 功能描述: 按模板场次批量解析相同场地、运动和时段的具体日期场次。
     *
     * @param templateSessionSlotId 模板具体场次 ID
     * @param dates 目标日期
     * @return 按具体场次 ID 排序的匹配场次
     * @author gjm
     */
    List<com.sportsbooking.admin.base.po.SessionSlot> selectTemplateSlots(
            @Param("templateSessionSlotId") long templateSessionSlotId,
            @Param("dates") List<LocalDate> dates);

    /**
     * 功能描述: 按 ID 稳定排序并加排他锁查询多个具体场次。
     *
     * @param ids 具体场次 ID
     * @return 已锁定场次
     * @author gjm
     */
    List<com.sportsbooking.admin.base.po.SessionSlot> selectSlotsForUpdate(@Param("ids") List<Long> ids);

    /**
     * 功能描述: 查询用户预约列表并聚合支付和退款状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @param userId 用户 ID
     * @return 用户预约列表
     * @author gjm
     */
    List<UserBookingBO> selectUserBookings(@Param("userId") long userId);

    /**
     * 功能描述: 查询场馆预约列表，可按业务日期筛选。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param date 业务日期，为空时不限制日期
     * @return 场馆预约列表
     * @author gjm
     */
    List<VenueBookingBO> selectVenueBookings(@Param("venueId") long venueId,
                                              @Param("date") LocalDate date);

    /**
     * 功能描述: 按场次业务日期聚合经营报表，可查询全平台或单个场馆。
     * 创建时间: 2026/09/03 10:35
     *
     * @param venueId 场馆 ID，为空时查询全平台
     * @param startAt 开始时间，包含
     * @param endAt 结束时间，不包含
     * @return 每日经营数据
     * @author gjm
     */
    List<OperationReportDailyBO> selectOperationReport(@Param("venueId") Long venueId,
                                                       @Param("startAt") Instant startAt,
                                                       @Param("endAt") Instant endAt);

    /**
     * 功能描述: 查询自然月内可计费的线上预约明细。
     * 创建时间: 2026/09/08
     *
     * @param venueId 场馆 ID，为空时查询全平台
     * @param startAt 账期开始时间，包含
     * @param endAt 下一账期开始时间，不包含
     * @return 可计费预约明细
     * @author gjm
     */
    List<ServiceFeeBookingBO> selectServiceFeeBookings(@Param("venueId") Long venueId,
                                                       @Param("startAt") Instant startAt,
                                                       @Param("endAt") Instant endAt);

    /**
     * 功能描述: 查询核销窗口与给定时间区间相交的已确认预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param earliest 核销窗口下界
     * @param latest 核销窗口上界
     * @return 可参与短码匹配的预约
     * @author gjm
     */
    List<Booking> selectVerifiable(@Param("venueId") long venueId, @Param("earliest") Instant earliest,
                                   @Param("latest") Instant latest);

    /**
     * 功能描述: 分批查询支付占位已过期的待支付预约 ID。
     * 创建时间: 2026/08/28 16:29
     *
     * @param now 当前时间
     * @return 过期预约 ID
     * @author gjm
     */
    List<Long> selectExpiredPendingIds(@Param("now") Instant now);

    /**
     * 功能描述: 分批查询场次已结束但仍未核销的预约 ID。
     * 创建时间: 2026/08/28 16:29
     *
     * @param now 当前时间
     * @return 未到场候选预约 ID
     * @author gjm
     */
    List<Long> selectNoShowIds(@Param("now") Instant now);

    /**
     * 功能描述: 分批查询已结束但仍待收敛的线下预约 ID。
     *
     * @param now 当前时间
     * @return 履约未跟踪候选预约 ID
     * @author gjm
     */
    List<Long> selectEndedUntrackedIds(@Param("now") Instant now);
}
