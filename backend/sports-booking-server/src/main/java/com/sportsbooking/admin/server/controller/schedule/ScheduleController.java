package com.sportsbooking.admin.server.controller.schedule;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.biz.pojo.request.CloseDayRequest;
import com.sportsbooking.admin.biz.pojo.request.CopyDayRequest;
import com.sportsbooking.admin.biz.pojo.request.ExtraSlotCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.WeeklySlotSaveRequest;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.server.support.Principals;
import com.sportsbooking.admin.biz.pojo.vo.SessionSlotResultVO;
import com.sportsbooking.admin.biz.pojo.vo.SessionSlotVO;
import com.sportsbooking.admin.biz.pojo.vo.WeeklySlotVO;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 功能描述: 场馆排期与具体日期场次的协议入口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@RestController
@RequestMapping("/venue-admin")
public class ScheduleController {
    @Autowired
    private ScheduleService scheduleService;

    /**
     * 功能描述: 查询当前场馆周场次模板。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 周场次列表
     * @author gjm
    */
    @GetMapping("/weekly-slots/list")
    public ApiResponse<List<WeeklySlotVO>> listWeeklySlots() {
        return ApiResponse.success(scheduleService.listWeekly(
                venuePrincipal(VenuePermission.SCHEDULE_VIEW).getVenueId()).stream()
                .map(WeeklySlotVO::from).toList());
    }

    /**
     * 功能描述: 新建周场次模板。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 周场次请求
     * @return 新建周场次
     * @author gjm
    */
    @PostMapping("/weekly-slots/create")
    public ApiResponse<WeeklySlotVO> createWeeklySlot(@RequestBody WeeklySlotSaveRequest body) {
        return ApiResponse.success(WeeklySlotVO.from(scheduleService.saveWeekly(
                venuePrincipal(VenuePermission.SCHEDULE_EDIT).getVenueId(), null, body
        )));
    }

    /**
     * 功能描述: 更新周场次模板。
     * 创建时间: 2026/08/28 16:29
     *
     * @param weeklySlotId 周场次 ID
     * @param body 周场次请求
     * @return 更新后的周场次
     * @author gjm
    */
    @PutMapping("/weekly-slots/{weeklySlotId}/update")
    public ApiResponse<WeeklySlotVO> updateWeeklySlot(
            @PathVariable long weeklySlotId, @RequestBody WeeklySlotSaveRequest body) {
        return ApiResponse.success(WeeklySlotVO.from(scheduleService.saveWeekly(
                venuePrincipal(VenuePermission.SCHEDULE_EDIT).getVenueId(), weeklySlotId, body
        )));
    }

    /**
     * 功能描述: 停用周场次模板。
     * 创建时间: 2026/08/28 16:29
     *
     * @param weeklySlotId 周场次 ID
     * @return 是否成功
     * @author gjm
    */
    @DeleteMapping("/weekly-slots/{weeklySlotId}/disable")
    public ApiResponse<Boolean> disableWeeklySlot(@PathVariable long weeklySlotId) {
        scheduleService.disableWeekly(venuePrincipal(VenuePermission.SCHEDULE_EDIT).getVenueId(), weeklySlotId);
        return ApiResponse.success(true);
    }

    /**
     * 功能描述: 将源星期模板复制到目标星期。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 复制日期请求
     * @return 复制后的周场次列表
     * @author gjm
    */
    @PostMapping("/weekly-slots/copy-day")
    public ApiResponse<List<WeeklySlotVO>> copyDay(@RequestBody CopyDayRequest body) {
        return ApiResponse.success(scheduleService.copyDay(
                venuePrincipal(VenuePermission.SCHEDULE_EDIT).getVenueId(), body.getSourceDay(), body.getTargetDays()
        ).stream().map(WeeklySlotVO::from).toList());
    }

    /**
     * 功能描述: 查询指定日期具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param date 日期
     * @param sportTypeId 运动类型 ID，为空时查询全部运动
     * @return 具体场次列表
     * @author gjm
     */
    @GetMapping("/sessions/list")
    public ApiResponse<List<SessionSlotVO>> listSessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long sportTypeId) {
        return ApiResponse.success(scheduleService.listDay(
                venuePrincipal(VenuePermission.SESSION_VIEW).getVenueId(), date, sportTypeId));
    }

    /**
     * 功能描述: 创建临时具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 临时场次请求
     * @return 新建具体场次
     * @author gjm
    */
    @PostMapping("/sessions/create")
    public ApiResponse<SessionSlotResultVO> createExtraSession(@RequestBody ExtraSlotCreateRequest body) {
        return ApiResponse.success(SessionSlotResultVO.from(
                scheduleService.createExtra(venuePrincipal(VenuePermission.SESSION_EDIT).getVenueId(), body)));
    }

    /**
     * 功能描述: 编辑任意状态的具体场次。
     * 创建时间: 2026/09/11
     *
     * @param sessionSlotId 具体场次 ID
     * @param body 场次保存请求
     * @return 更新后的具体场次
     * @author gjm
     */
    @PutMapping("/sessions/{sessionSlotId}")
    public ApiResponse<SessionSlotResultVO> updateSession(
            @PathVariable long sessionSlotId, @RequestBody ExtraSlotCreateRequest body) {
        return ApiResponse.success(SessionSlotResultVO.from(scheduleService.updateSession(
                venuePrincipal(VenuePermission.SESSION_EDIT).getVenueId(), sessionSlotId, body)));
    }

    /**
     * 功能描述: 关闭未被占用的具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param sessionSlotId 具体场次 ID
     * @return 是否成功
     * @author gjm
    */
    @PostMapping("/sessions/{sessionSlotId}/close")
    public ApiResponse<Boolean> closeSession(@PathVariable long sessionSlotId) {
        scheduleService.closeSlot(venuePrincipal(VenuePermission.SESSION_EDIT).getVenueId(), sessionSlotId);
        return ApiResponse.success(true);
    }

    /**
     * 功能描述: 重新开启未来且未冲突的已关闭具体场次。
     * 创建时间: 2026/09/03 10:20
     *
     * @param sessionSlotId 具体场次 ID
     * @return 是否成功
     * @author gjm
     */
    @PostMapping("/sessions/{sessionSlotId}/open")
    public ApiResponse<Boolean> openSession(@PathVariable long sessionSlotId) {
        scheduleService.openSlot(venuePrincipal(VenuePermission.SESSION_EDIT).getVenueId(), sessionSlotId);
        return ApiResponse.success(true);
    }

    /**
     * 功能描述: 关闭指定日期全部未占用场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 关闭日期请求
     * @return 是否成功
     * @author gjm
    */
    @PostMapping("/sessions/close-day")
    public ApiResponse<Boolean> closeDay(@RequestBody CloseDayRequest body) {
        scheduleService.closeDay(venuePrincipal(VenuePermission.SESSION_EDIT).getVenueId(), body.getDate());
        return ApiResponse.success(true);
    }

    /**
     * 功能描述: 场馆范围始终取自登录会话，避免客户端伪造 venueId。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 当前场馆管理员身份
     * @author gjm
     */
    private AdminPrincipal venuePrincipal(VenuePermission permission) {
        return Principals.venue(permission);
    }
}
