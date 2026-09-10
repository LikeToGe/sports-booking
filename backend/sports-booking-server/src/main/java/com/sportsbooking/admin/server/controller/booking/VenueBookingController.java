package com.sportsbooking.admin.server.controller.booking;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.biz.pojo.request.BookingCancelRequest;
import com.sportsbooking.admin.biz.pojo.request.OfflineBookingCreateRequest;
import com.sportsbooking.admin.base.model.AdminPrincipal;
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
        return ApiResponse.success(bookingService.listVenueBookings(venuePrincipal().getVenueId(), date));
    }

    /**
     * 功能描述: 创建电话或到店预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 线下预约请求
     * @return 已确认预约
     * @author gjm
    */
    @PostMapping("/bookings/create")
    public ApiResponse<BookingVO> createOfflineBooking(@RequestBody OfflineBookingCreateRequest body) {
        return ApiResponse.success(BookingVO.from(bookingService.createOffline(
                venuePrincipal().getVenueId(), body.getSessionSlotId(), body.getContactName(), body.getContactPhone()
        )));
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
        return ApiResponse.success(BookingVO.from(bookingService.cancelByVenue(
                venuePrincipal().getVenueId(), bookingId, body.getReason()
        )));
    }

    /**
     * 功能描述: 场馆范围始终从后台会话中获取。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 当前场馆管理员身份
     * @author gjm
     */
    private AdminPrincipal venuePrincipal() {
        return Principals.venue();
    }
}
