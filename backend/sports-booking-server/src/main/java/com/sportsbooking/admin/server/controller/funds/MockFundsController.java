package com.sportsbooking.admin.server.controller.funds;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.MockFundsNotificationRequest;
import com.sportsbooking.admin.biz.pojo.vo.BookingVO;
import com.sportsbooking.admin.biz.pojo.vo.MemberRechargeVO;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.service.PaymentService;
import com.sportsbooking.admin.server.api.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅用于本地闭环验收的签名替身通知入口。 */
@RestController
@RequestMapping("/mock")
public class MockFundsController {
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private MemberFundsService memberFundsService;
    @Value("${app.mock.signature:local-mock-signature}")
    private String expectedSignature;

    @PostMapping("/payments/notify")
    public ApiResponse<BookingVO> notifyPayment(@RequestBody MockFundsNotificationRequest body) {
        validate(body);
        Booking booking = paymentService.handleMockPaymentNotification(
                body.getBusinessNo(), body.getAmountFen(), body.getResult());
        return ApiResponse.success(BookingVO.from(booking));
    }

    @PostMapping("/recharges/notify")
    public ApiResponse<MemberRechargeVO> notifyRecharge(@RequestBody MockFundsNotificationRequest body) {
        validate(body);
        return ApiResponse.success(memberFundsService.handleMockRechargeNotification(
                body.getBusinessNo(), body.getAmountFen(), body.getResult()));
    }

    private void validate(MockFundsNotificationRequest body) {
        if (body == null || body.getBusinessNo() == null || body.getAmountFen() == null
                || body.getResult() == null || !expectedSignature.equals(body.getSignature())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
    }
}
