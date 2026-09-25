package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.request.ExtraSlotCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.WeeklySlotSaveRequest;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.po.WeeklySlot;
import com.sportsbooking.admin.biz.pojo.vo.SessionSlotVO;
import java.time.LocalDate;
import java.util.List;

/**
 * 功能描述: 周场次模板和具体日期场次业务接口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface ScheduleService {

    /**
     * 功能描述: 查询场馆全部周场次模板。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @return 周场次模板列表
     * @author gjm
     */
    List<WeeklySlot> listWeekly(long venueId);

    /**
     * 功能描述: 新增或编辑周场次并同步未来开放场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param weeklySlotId 周场次 ID，新建时为空
     * @param body 周场次保存请求
     * @return 保存后的周场次
     * @author gjm
     */
    WeeklySlot saveWeekly(long venueId, Long weeklySlotId, WeeklySlotSaveRequest body);

    /**
     * 功能描述: 停用周场次并关闭未占用的未来具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param weeklySlotId 周场次 ID
     * @author gjm
     */
    void disableWeekly(long venueId, long weeklySlotId);

    /**
     * 功能描述: 将源星期有效模板复制到目标星期。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param sourceDay 源星期
     * @param targetDays 目标星期列表
     * @return 复制后的全部周场次
     * @author gjm
     */
    List<WeeklySlot> copyDay(long venueId, int sourceDay, List<Integer> targetDays);

    /**
     * 功能描述: 查询并同步场馆某天的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param date 日期
     * @return 具体场次列表
     * @author gjm
     */
    List<SessionSlotVO> listDay(long venueId, LocalDate date);

    /**
     * 功能描述: 按运动类型查询并同步场馆某天的具体场次。
     *
     * @param venueId 场馆 ID
     * @param date 日期
     * @param sportTypeId 运动类型 ID，为空时查询全部运动
     * @return 具体场次列表
     * @author gjm
     */
    List<SessionSlotVO> listDay(long venueId, LocalDate date, Long sportTypeId);

    /**
     * 功能描述: 创建不属于周模板的临时场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param body 临时场次请求
     * @return 新建具体场次
     * @author gjm
     */
    SessionSlot createExtra(long venueId, ExtraSlotCreateRequest body);

    /**
     * 功能描述: 编辑任意状态的具体场次，不改写已有预约快照。
     * 创建时间: 2026/09/11
     *
     * @param venueId 场馆 ID
     * @param sessionSlotId 具体场次 ID
     * @param body 场次保存请求
     * @return 更新后的具体场次
     * @author gjm
     */
    SessionSlot updateSession(long venueId, long sessionSlotId, ExtraSlotCreateRequest body);

    /**
     * 功能描述: 关闭一条未被占用的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param sessionSlotId 具体场次 ID
     * @author gjm
     */
    void closeSlot(long venueId, long sessionSlotId);

    /**
     * 功能描述: 重新开启一条未来且未冲突的已关闭具体场次。
     * 创建时间: 2026/09/03 10:20
     *
     * @param venueId 场馆 ID
     * @param sessionSlotId 具体场次 ID
     * @author gjm
     */
    void openSlot(long venueId, long sessionSlotId);

    /**
     * 功能描述: 关闭指定日期全部未占用具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param date 日期
     * @author gjm
     */
    void closeDay(long venueId, LocalDate date);

    /**
     * 功能描述: 同步一个场馆未来预约窗口的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @author gjm
     */
    void syncVenue(long venueId);

    /**
     * 功能描述: 定时同步全部已上架场馆。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    void syncAllActiveVenues();

    /**
     * 功能描述: 预约取消后释放具体场次占用。
     * 创建时间: 2026/08/28 16:29
     *
     * @param sessionSlotId 具体场次 ID
     * @author gjm
     */
    void releaseBookingSlot(long sessionSlotId);
}
