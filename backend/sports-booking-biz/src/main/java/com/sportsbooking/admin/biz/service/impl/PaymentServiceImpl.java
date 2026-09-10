package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.po.Refund;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PaymentMapper;
import com.sportsbooking.admin.base.mapper.RefundMapper;
import com.sportsbooking.admin.biz.service.PaymentService;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.base.enums.PaymentProvider;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.base.enums.RefundStatus;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.biz.service.NotificationService;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能描述: 模拟支付、退款和支付记录查询业务实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class PaymentServiceImpl implements PaymentService {
    private final BookingMapper bookingMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final SessionSlotMapper sessionSlotMapper;
    private final NotificationService notificationService;

    public PaymentServiceImpl(BookingMapper bookingMapper, PaymentMapper paymentMapper, RefundMapper refundMapper,
                              SessionSlotMapper sessionSlotMapper, NotificationService notificationService) {
        this.bookingMapper = bookingMapper;
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.sessionSlotMapper = sessionSlotMapper;
        this.notificationService = notificationService;
    }

    /**
     * 功能描述: 为线上预约创建待支付记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Payment createPending(Booking booking) {
        Payment payment = new Payment();
        payment.bookingId = booking.id;
        payment.provider = PaymentProvider.MOCK;
        payment.amountFen = booking.amountFen;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = LocalDateTime.now();
        payment.updatedAt = payment.createdAt;
        paymentMapper.insert(payment);
        return payment;
    }

    /**
     * 功能描述: 模拟支付成功并原子确认预约与具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public Booking mockPay(long userId, long bookingId) {
        Booking booking = requiredLockedBooking(bookingId);
        if (booking.userId == null || booking.userId != userId) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        if (!BookingStatus.PENDING_PAYMENT.equals(booking.status)
                || booking.paymentExpiresAt.isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_EXPIRED);
        }
        SessionSlot slot = sessionSlotMapper.selectForUpdate(booking.sessionSlotId);
        if (slot == null || !SessionSlotStatus.HELD.equals(slot.status)
                || !booking.id.equals(slot.holdBookingId)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.HOLD_LOST);
        }

        // 支付、预约确认和场次占用必须原子提交，避免出现已支付但场次仍开放。
        booking.status = BookingStatus.CONFIRMED;
        booking.updatedAt = LocalDateTime.now();
        bookingMapper.updateById(booking);
        slot.status = SessionSlotStatus.BOOKED;
        slot.holdExpiresAt = null;
        sessionSlotMapper.updateById(slot);

        Payment payment = requiredPayment(booking.id);
        payment.status = PaymentStatus.SUCCESS;
        payment.providerNo = "MOCK-" + UUID.randomUUID();
        payment.paidAt = Instant.now();
        payment.updatedAt = LocalDateTime.now();
        paymentMapper.updateById(payment);
        notificationService.record(booking, NotificationType.BOOKING_CONFIRMED);
        return booking;
    }

    /**
     * 功能描述: 预约取消后执行模拟全额退款并记录通知。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void refundAndNotify(Booking booking, String reason) {
        Payment payment = findPayment(booking.id);
        if (payment != null && PaymentStatus.SUCCESS.equals(payment.status)) {
            // 退款记录与支付状态加入调用方事务，确保取消失败时不会留下孤立退款。
            Refund refund = new Refund();
            refund.bookingId = booking.id;
            refund.paymentId = payment.id;
            refund.amountFen = payment.amountFen;
            refund.reason = reason;
            refund.status = RefundStatus.SUCCESS;
            refund.refundedAt = Instant.now();
            refund.createdAt = LocalDateTime.now();
            refund.updatedAt = refund.createdAt;
            refundMapper.insert(refund);
            payment.status = PaymentStatus.REFUNDED;
            payment.updatedAt = LocalDateTime.now();
            paymentMapper.updateById(payment);
        }
        notificationService.record(booking, NotificationType.BOOKING_CANCELLED);
    }

    /**
     * 功能描述: 查询指定预约的支付记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Payment findPayment(long bookingId) {
        return paymentMapper.selectOne(new QueryWrapper<Payment>().eq("booking_id", bookingId));
    }

    /**
     * 功能描述: 查询指定预约的退款记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Refund findRefund(long bookingId) {
        return refundMapper.selectOne(new QueryWrapper<Refund>().eq("booking_id", bookingId));
    }

    /**
     * 功能描述: 锁定并查询预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param bookingId 预约 ID
     * @return 已锁定预约
     * @author gjm
     */
    private Booking requiredLockedBooking(long bookingId) {
        Booking booking = bookingMapper.selectForUpdate(bookingId);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return booking;
    }

    /**
     * 功能描述: 查询预约唯一支付记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @param bookingId 预约 ID
     * @return 支付记录
     * @author gjm
     */
    private Payment requiredPayment(long bookingId) {
        Payment payment = findPayment(bookingId);
        if (payment == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_MISSING);
        }
        return payment;
    }
}
