package com.sportsbooking.admin.server.controller.booking;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.biz.pojo.request.BookingCreateRequest;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.biz.service.PaymentService;
import com.sportsbooking.admin.biz.pojo.vo.BookingDetailVO;
import com.sportsbooking.admin.biz.pojo.vo.BookingVO;
import com.sportsbooking.admin.biz.pojo.vo.UserBookingVO;
import com.sportsbooking.admin.server.context.AuthContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 功能描述: 小程序用户预约、模拟支付和取消协议入口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@RestController
@RequestMapping("/mini")
public class MiniBookingController {
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;

    /**
     * 功能描述: 查询当前用户全部预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 用户预约列表
     * @author gjm
    */
    @GetMapping("/bookings/list")
    public ApiResponse<List<UserBookingVO>> listBookings() {
        return ApiResponse.success(bookingService.listUserBookings(AuthContext.getMiniUserId()));
    }

    /**
     * 功能描述: 创建当前用户的线上待支付预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 预约创建请求
     * @return 待支付预约
     * @author gjm
    */
    @PostMapping("/bookings/create")
    public ApiResponse<BookingVO> createBooking(@RequestBody BookingCreateRequest body) {
        return ApiResponse.success(BookingVO.from(bookingService.createOnline(
                AuthContext.getMiniUserId(), body.getSessionSlotId(), body.getContactName(), body.getContactPhone(),
                body.getIdempotencyKey(), body.getExpectedPriceFen(), body.getEntryType()
        )));
    }

    /**
     * 功能描述: 查询当前用户单笔预约详情。
     * 创建时间: 2026/08/28 16:29
     *
     * @param bookingId 预约 ID
     * @return 预约详情
     * @author gjm
    */
    @GetMapping("/bookings/{bookingId}")
    public ApiResponse<BookingDetailVO> getBooking(@PathVariable long bookingId) {
        return ApiResponse.success(bookingService.getUserBooking(AuthContext.getMiniUserId(), bookingId));
    }

    /**
     * 功能描述: 对当前用户预约执行模拟支付。
     * 创建时间: 2026/08/28 16:29
     *
     * @param bookingId 预约 ID
     * @return 已确认预约
     * @author gjm
    */
    @PostMapping("/bookings/{bookingId}/mock-pay")
    public ApiResponse<BookingVO> mockPay(@PathVariable long bookingId) {
        return ApiResponse.success(BookingVO.from(paymentService.mockPay(AuthContext.getMiniUserId(), bookingId)));
    }

    /** 查询渠道结果并幂等收敛当前用户预约。 */
    @PostMapping("/bookings/{bookingId}/payment-query")
    public ApiResponse<BookingVO> queryPayment(@PathVariable long bookingId) {
        return ApiResponse.success(BookingVO.from(
                paymentService.queryPayment(AuthContext.getMiniUserId(), bookingId)));
    }

    /**
     * 功能描述: 在取消截止前取消当前用户预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param bookingId 预约 ID
     * @return 已取消预约
     * @author gjm
    */
    @PostMapping("/bookings/{bookingId}/cancel")
    public ApiResponse<BookingVO> cancelBooking(@PathVariable long bookingId) {
        return ApiResponse.success(BookingVO.from(bookingService.cancelByUser(AuthContext.getMiniUserId(), bookingId)));
    }
}
