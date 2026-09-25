package com.sportsbooking.admin.server.controller.booking;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.biz.pojo.request.BookingCancelRequest;
import com.sportsbooking.admin.biz.pojo.request.BookingContactUpdateRequest;
import com.sportsbooking.admin.biz.pojo.request.OfflineBookingCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.OfflineBookingBatchCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.OfflineBookingPaymentStatusUpdateRequest;
import com.sportsbooking.admin.biz.pojo.request.OfflineBookingRescheduleRequest;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.server.support.Principals;
import com.sportsbooking.admin.biz.pojo.vo.BookingVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueBookingVO;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 功能描述: 场馆查询、创建线下预约和主动取消的协议入口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@RestController
@RequestMapping("/venue-admin")
public class VenueBookingController {
    @Autowired
    private BookingService bookingService;

    /**
     * 功能描述: 查询当前场馆指定日期预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param date 查询日期
     * @return 场馆预约列表
     * @author gjm
     */
    @GetMapping("/bookings/list")
    public ApiResponse<List<VenueBookingVO>> listBookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(bookingService.listVenueBookings(
                venuePrincipal(VenuePermission.BOOKING_VIEW).getVenueId(), date));
    }

    /**
     * 功能描述: 创建电话或到店预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 线下预约请求
     * @return 已确认预约
     * @author gjm
    */
    @PostMapping("/bookings/offline")
    public ApiResponse<BookingVO> createOfflineBooking(@RequestBody OfflineBookingCreateRequest body) {
        return createOffline(body);
    }

    /**
     * 功能描述: 兼容旧后台创建路径，请求仍必须显式携带登记收款状态。
     *
     * @param body 线下预约请求
     * @return 已确认预约
     * @author gjm
     */
    @PostMapping("/bookings/create")
    public ApiResponse<BookingVO> createOfflineBookingLegacy(@RequestBody OfflineBookingCreateRequest body) {
        return createOffline(body);
    }

    /**
     * 功能描述: 复用新旧路径的线下预约创建调用。
     *
     * @param body 线下预约请求
     * @return 已确认预约
     * @author gjm
     */
    private ApiResponse<BookingVO> createOffline(OfflineBookingCreateRequest body) {
        return ApiResponse.success(BookingVO.from(bookingService.createOffline(
                venuePrincipal(VenuePermission.BOOKING_EDIT).getVenueId(), body.getSessionSlotId(),
                body.getContactName(), body.getContactPhone(),
                body.getPaymentStatus(), body.getVenueCustomerId()
        )));
    }

    /**
     * 功能描述: 创建固定场地、运动和时段的多日期线下预约。
     *
     * @param body 批量线下预约请求
     * @return 去重日期对应的预约
     * @author gjm
     */
    @PostMapping("/bookings/offline/batch")
    public ApiResponse<List<BookingVO>> createOfflineBookings(@RequestBody OfflineBookingBatchCreateRequest body) {
        return ApiResponse.success(bookingService.createOfflineBatch(
                venuePrincipal(VenuePermission.BOOKING_EDIT).getVenueId(), body.getSessionSlotId(), body.getDates(),
                body.getContactName(), body.getContactPhone(), body.getPaymentStatus(), body.getVenueCustomerId()
        ).stream().map(BookingVO::from).toList());
    }

    /**
     * 功能描述: 修改线下预约登记收款状态，操作者只取当前后台会话。
     *
     * @param bookingId 预约 ID
     * @param body 状态和修改原因
     * @return 更新后的预约
     * @author gjm
     */
    @PutMapping("/bookings/{bookingId}/payment-status")
    public ApiResponse<BookingVO> updateOfflinePaymentStatus(
            @PathVariable long bookingId, @RequestBody OfflineBookingPaymentStatusUpdateRequest body) {
        AdminPrincipal principal = venuePrincipal(VenuePermission.OFFLINE_PAYMENT_STATUS_EDIT);
        return ApiResponse.success(BookingVO.from(bookingService.updateOfflinePaymentStatus(
                principal.getVenueId(), adminId(principal), principal.getEmployeeId(), bookingId,
                body.getPaymentStatus(), body.getReason())));
    }

    /**
     * 功能描述: 将线下预约改到本场馆同价开放场次，操作者只取当前后台会话。
     *
     * @param bookingId 预约 ID
     * @param body 目标场次和改期原因
     * @return 改期后的预约
     * @author gjm
     */
    @PostMapping("/bookings/{bookingId}/reschedule")
    public ApiResponse<BookingVO> rescheduleOfflineBooking(
            @PathVariable long bookingId, @RequestBody OfflineBookingRescheduleRequest body) {
        AdminPrincipal principal = venuePrincipal(VenuePermission.BOOKING_EDIT);
        return ApiResponse.success(BookingVO.from(bookingService.rescheduleOffline(
                principal.getVenueId(), adminId(principal), principal.getEmployeeId(), bookingId,
                body.getSessionSlotId(), body.getReason())));
    }

    /**
     * 功能描述: 修改任意状态线下预约的联系人资料。
     * 创建时间: 2026/09/11
     *
     * @param bookingId 预约 ID
     * @param body 联系人请求
     * @return 更新后的预约
     * @author gjm
     */
    @PutMapping("/bookings/{bookingId}/contact")
    public ApiResponse<BookingVO> updateOfflineContact(
            @PathVariable long bookingId, @RequestBody BookingContactUpdateRequest body) {
        return ApiResponse.success(BookingVO.from(bookingService.updateOfflineContact(
                venuePrincipal(VenuePermission.BOOKING_EDIT).getVenueId(), bookingId,
                body.getContactName(), body.getContactPhone())));
    }

    /**
     * 功能描述: 当前场馆填写原因后取消预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param bookingId 预约 ID
     * @param body 取消原因
     * @return 已取消预约
     * @author gjm
    */
    @PostMapping("/bookings/{bookingId}/cancel")
    public ApiResponse<BookingVO> cancelBooking(@PathVariable long bookingId, @RequestBody BookingCancelRequest body) {
        AdminPrincipal principal = Principals.venueAny(
                VenuePermission.BOOKING_EDIT, VenuePermission.ONLINE_BOOKING_CANCEL);
        return ApiResponse.success(BookingVO.from(bookingService.cancelByVenue(
                principal.getVenueId(), bookingId, body.getReason(),
                hasPermission(principal, VenuePermission.ONLINE_BOOKING_CANCEL),
                hasPermission(principal, VenuePermission.BOOKING_EDIT)
        )));
    }

    /**
     * 功能描述: 场馆范围始终从后台会话中获取。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 当前场馆管理员身份
     * @author gjm
     */
    private AdminPrincipal venuePrincipal(VenuePermission permission) {
        return Principals.venue(permission);
    }

    private Long adminId(AdminPrincipal principal) {
        return principal.getRole() == AdminRole.VENUE_ADMIN ? principal.getAccountId() : null;
    }

    private boolean hasPermission(AdminPrincipal principal, VenuePermission permission) {
        return principal.getRole() == AdminRole.VENUE_ADMIN || principal.getPermissions().contains(permission);
    }
}
