package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.CancelledBy;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.base.enums.PaymentProvider;
import com.sportsbooking.admin.base.enums.PaymentResult;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.base.enums.RefundStatus;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PaymentMapper;
import com.sportsbooking.admin.base.mapper.RefundMapper;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.po.Refund;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.biz.constants.RedisLockType;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.PlatformRefundVO;
import com.sportsbooking.admin.biz.pojo.vo.RefundVO;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.service.NotificationService;
import com.sportsbooking.admin.biz.service.ReminderService;
import com.sportsbooking.admin.biz.service.PaymentService;
import com.sportsbooking.admin.biz.service.PaymentChannelAdapter;
import com.sportsbooking.admin.biz.service.RedisLockService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 外部支付、查单和退款状态收敛实现。 */
@Service
public class PaymentServiceImpl implements PaymentService {
    private final BookingMapper bookingMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final SessionSlotMapper sessionSlotMapper;
    private final NotificationService notificationService;
    private final MemberFundsService memberFundsService;
    private final RedisLockService redisLockService;
    private final PaymentChannelAdapter channelAdapter;
    private final ReminderService reminderService;

    public PaymentServiceImpl(BookingMapper bookingMapper, PaymentMapper paymentMapper, RefundMapper refundMapper,
                              SessionSlotMapper sessionSlotMapper, NotificationService notificationService,
                              MemberFundsService memberFundsService, RedisLockService redisLockService,
                              PaymentChannelAdapter channelAdapter) {
        this(bookingMapper, paymentMapper, refundMapper, sessionSlotMapper, notificationService,
                memberFundsService, redisLockService, channelAdapter, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentServiceImpl(BookingMapper bookingMapper, PaymentMapper paymentMapper, RefundMapper refundMapper,
                              SessionSlotMapper sessionSlotMapper, NotificationService notificationService,
                              MemberFundsService memberFundsService, RedisLockService redisLockService,
                              PaymentChannelAdapter channelAdapter, ReminderService reminderService) {
        this.bookingMapper = bookingMapper;
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.sessionSlotMapper = sessionSlotMapper;
        this.notificationService = notificationService;
        this.memberFundsService = memberFundsService;
        this.redisLockService = redisLockService;
        this.channelAdapter = channelAdapter;
        this.reminderService = reminderService;
    }

    @Override
    public Booking startPayment(long userId, long bookingId) {
        Booking booking = requiredBooking(bookingId);
        requireOwner(booking, userId);
        Payment payment = findPayment(bookingId);
        if (payment == null) {
            return booking;
        }
        PaymentResult result = channelAdapter.startPayment(payment);
        return handleMockPaymentResult(userId, bookingId, result);
    }

    @Override
    public Payment createPending(Booking booking) {
        if (booking.externalAmountFen == null || booking.externalAmountFen <= 0) {
            return null;
        }
        Payment existing = findPayment(booking.id);
        if (existing != null) {
            return existing;
        }
        Payment payment = new Payment();
        payment.bookingId = booking.id;
        payment.paymentNo = "PAY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        payment.provider = PaymentProvider.MOCK;
        payment.amountFen = booking.externalAmountFen;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = LocalDateTime.now();
        payment.updatedAt = payment.createdAt;
        paymentMapper.insert(payment);
        return payment;
    }

    @Override
    public Booking mockPay(long userId, long bookingId) {
        // Mock 入口模拟渠道成功通知，不允许客户端直接提交预约终态。
        return handleMockPaymentResult(userId, bookingId, PaymentResult.SUCCESS);
    }

    @Override
    public Booking queryPayment(long userId, long bookingId) {
        Booking booking = requiredBooking(bookingId);
        requireOwner(booking, userId);
        return queryPayment(bookingId);
    }

    @Override
    public Booking queryPayment(long bookingId) {
        Booking booking = requiredBooking(bookingId);
        Payment payment = findPayment(bookingId);
        if (payment == null) {
            return booking;
        }
        // 渠道查单发生在事务和 Redis 锁外，随后才进入内部幂等收敛。
        PaymentResult result = payment.status == PaymentStatus.SUCCESS ? PaymentResult.SUCCESS
                : payment.status == PaymentStatus.FAILED || payment.status == PaymentStatus.CLOSED
                ? PaymentResult.FAILED : channelAdapter.queryPayment(payment);
        if (booking.userId == null) {
            return booking;
        }
        return handleMockPaymentResult(booking.userId, bookingId, result);
    }

    @Override
    public Booking closeUnpaid(long userId, long bookingId, CancelledBy cancelledBy, String reason) {
        Booking booking = requiredBooking(bookingId);
        requireOwner(booking, userId);
        if ((cancelledBy != CancelledBy.USER && cancelledBy != CancelledBy.VENUE)
                || reason == null || reason.isBlank() || reason.trim().length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        if (booking.status != BookingStatus.PENDING_PAYMENT) return booking;
        Payment payment = findPayment(bookingId);
        if (payment == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_MISSING);
        }
        // 渠道闭单与查单一样发生在业务锁外；FAILED 在这里必须是确认未付已关闭。
        PaymentResult result = payment.status == PaymentStatus.SUCCESS ? PaymentResult.SUCCESS
                : payment.status == PaymentStatus.FAILED || payment.status == PaymentStatus.CLOSED
                ? PaymentResult.FAILED : channelAdapter.closePayment(payment);
        return handlePaymentResult(userId, bookingId, result == null ? PaymentResult.UNKNOWN : result,
                cancelledBy, reason.trim());
    }

    @Override
    public Booking handleMockPaymentResult(long userId, long bookingId, PaymentResult result) {
        return handlePaymentResult(userId, bookingId, result, null, null);
    }

    /** 同一支付资源锁下收敛通知或主动闭单，外部退款仍在提交后执行。 */
    private Booking handlePaymentResult(long userId, long bookingId, PaymentResult result,
                                        CancelledBy closingActor, String closingReason) {
        Booking candidate = requiredBooking(bookingId);
        requireOwner(candidate, userId);
        List<String> keys = List.of(RedisLockType.BOOKING.key(bookingId),
                RedisLockType.SESSION_SLOT.key(candidate.sessionSlotId),
                RedisLockType.MEMBER_BALANCE.key(candidate.venueCustomerId));
        Booking converged = redisLockService.execute(keys,
                () -> convergePayment(bookingId, userId, result, Instant.now(), closingActor, closingReason));
        if (converged.paymentStatus == BookingPaymentStatus.REFUNDING) {
            processRefund(bookingId);
            return bookingMapper.selectById(bookingId);
        }
        return converged;
    }

    @Override
    public Booking handleMockPaymentNotification(String businessNo, int amountFen, PaymentResult result) {
        Payment payment = paymentMapper.selectByBusinessNo(businessNo);
        if (payment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        if (result == null || payment.amountFen != amountFen) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        Booking booking = requiredBooking(payment.bookingId);
        return handleMockPaymentResult(booking.userId, booking.id, result);
    }

    /** 支付结果只推进待支付状态，履约终态及首次成功时间不能被渠道重放覆写。 */
    private Booking convergePayment(long bookingId, long userId, PaymentResult result, Instant now,
                                    CancelledBy closingActor, String closingReason) {
        Booking booking = requiredLockedBooking(bookingId);
        requireOwner(booking, userId);
        if (booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.COMPLETED
                || booking.status == BookingStatus.NO_SHOW) {
            return booking;
        }
        Payment payment = paymentMapper.selectByBookingForUpdate(bookingId);
        if (payment == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_MISSING);
        }
        // 闭单请求和成功回调可能交错；已有成功事实始终优先，不能被未付关闭覆盖。
        if (payment.status == PaymentStatus.SUCCESS && result == PaymentResult.FAILED) result = PaymentResult.SUCCESS;
        if (result == PaymentResult.UNKNOWN) {
            payment.lastQueryAt = now;
            payment.updatedAt = LocalDateTime.now();
            paymentMapper.updateById(payment);
            return booking;
        }
        if (booking.status == BookingStatus.CANCELLED) {
            // 关闭后晚到成功只修正真实支付事实并立即原路退款，不恢复预约、余额或场次。
            if (result == PaymentResult.SUCCESS) {
                payment.status = PaymentStatus.SUCCESS;
                payment.providerNo = payment.providerNo == null ? "MOCK-" + payment.paymentNo : payment.providerNo;
                if (payment.paidAt == null) payment.paidAt = now;
                payment.updatedAt = LocalDateTime.now();
                paymentMapper.updateById(payment);
                Refund existing = refundMapper.selectByBookingForUpdate(bookingId);
                if (existing == null) {
                    refundAndNotify(booking, "支付关闭后渠道成功，原路退款");
                } else {
                    booking.paymentStatus = existing.status == RefundStatus.SUCCESS
                            ? BookingPaymentStatus.REFUNDED : BookingPaymentStatus.REFUNDING;
                    booking.updatedAt = LocalDateTime.now();
                    bookingMapper.updateById(booking);
                }
            }
            return booking;
        }
        SessionSlot slot = sessionSlotMapper.selectForUpdate(booking.sessionSlotId);
        if (result == PaymentResult.SUCCESS) {
            if (slot == null || slot.status != SessionSlotStatus.HELD || !booking.id.equals(slot.holdBookingId)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.HOLD_LOST);
            }
            memberFundsService.consume(booking.id, now);
            payment.status = PaymentStatus.SUCCESS;
            payment.providerNo = payment.providerNo == null ? "MOCK-" + payment.paymentNo : payment.providerNo;
            if (payment.paidAt == null) payment.paidAt = now;
            payment.lastQueryAt = now;
            booking.status = BookingStatus.CONFIRMED;
            booking.paymentStatus = BookingPaymentStatus.PAID;
            booking.paymentExpiresAt = null;
            slot.status = SessionSlotStatus.BOOKED;
            slot.holdExpiresAt = null;
            sessionSlotMapper.updateById(slot);
            notificationService.record(booking, NotificationType.BOOKING_CONFIRMED);
        } else {
            memberFundsService.release(booking.id, now);
            payment.status = closingActor != null ? PaymentStatus.CLOSED : PaymentStatus.FAILED;
            if (closingActor != null) payment.closedAt = now;
            payment.lastQueryAt = now;
            booking.status = BookingStatus.CANCELLED;
            booking.paymentStatus = BookingPaymentStatus.PAYMENT_FAILED;
            booking.cancelledBy = closingActor == null ? CancelledBy.SYSTEM : closingActor;
            booking.cancelReason = closingActor == null ? "支付失败" : closingReason;
            booking.cancelledAt = now;
            releaseSlot(slot, booking.id);
        }
        payment.updatedAt = LocalDateTime.now();
        booking.updatedAt = LocalDateTime.now();
        paymentMapper.updateById(payment);
        bookingMapper.updateById(booking);
        return booking;
    }

    @Override
    public void closePending(long bookingId, Instant now) {
        Payment payment = paymentMapper.selectByBookingForUpdate(bookingId);
        if (payment != null && payment.status == PaymentStatus.PENDING) {
            payment.status = PaymentStatus.CLOSED;
            payment.closedAt = now;
            payment.updatedAt = LocalDateTime.now();
            paymentMapper.updateById(payment);
        }
    }

    @Override
    public void refundAndNotify(Booking booking, String reason) {
        Payment payment = paymentMapper.selectByBookingForUpdate(booking.id);
        if (payment != null && payment.status == PaymentStatus.SUCCESS) {
            Refund refund = refundMapper.selectByBookingForUpdate(booking.id);
            if (refund == null) {
                refund = new Refund();
                refund.bookingId = booking.id;
                refund.paymentId = payment.id;
                refund.refundNo = "REF-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
                refund.amountFen = payment.amountFen;
                refund.reason = reason;
                refund.status = RefundStatus.PROCESSING;
                refund.retryCount = 0;
                refund.nextRetryAt = Instant.now();
                refund.createdAt = LocalDateTime.now();
                refund.updatedAt = refund.createdAt;
                refundMapper.insert(refund);
            }
            booking.paymentStatus = BookingPaymentStatus.REFUNDING;
            booking.updatedAt = LocalDateTime.now();
            bookingMapper.updateById(booking);
        } else {
            booking.paymentStatus = BookingPaymentStatus.REFUNDED;
            bookingMapper.updateById(booking);
        }
        notificationService.record(booking, NotificationType.BOOKING_CANCELLED);
    }

    @Override
    public Refund processRefund(long bookingId) {
        Refund candidate = findRefund(bookingId);
        if (candidate == null || candidate.status == RefundStatus.SUCCESS || candidate.status == RefundStatus.CLOSED) {
            return candidate;
        }
        PaymentResult providerResult = channelAdapter.refund(candidate);
        return redisLockService.execute(List.of(RedisLockType.BOOKING.key(bookingId)), () -> {
            Refund refund = refundMapper.selectByBookingForUpdate(bookingId);
            if (refund == null || refund.status == RefundStatus.SUCCESS || refund.status == RefundStatus.CLOSED) {
                return refund;
            }
            Booking booking = requiredLockedBooking(bookingId);
            refund.retryCount += 1;
            if (providerResult == PaymentResult.SUCCESS) {
                refund.status = RefundStatus.SUCCESS;
                refund.refundedAt = Instant.now();
                refund.nextRetryAt = null;
                refund.lastFailureReason = null;
                booking.paymentStatus = BookingPaymentStatus.REFUNDED;
            } else if (providerResult == PaymentResult.FAILED) {
                refund.status = RefundStatus.FAILED;
                refund.lastFailureReason = "Mock 退款失败";
                refund.nextRetryAt = Instant.now().plus(Duration.ofMinutes(5));
                booking.paymentStatus = BookingPaymentStatus.REFUND_FAILED;
            } else {
                refund.status = RefundStatus.PROCESSING;
                refund.nextRetryAt = Instant.now().plus(Duration.ofMinutes(5));
                booking.paymentStatus = BookingPaymentStatus.REFUNDING;
            }
            refund.updatedAt = LocalDateTime.now();
            booking.updatedAt = LocalDateTime.now();
            refundMapper.updateById(refund);
            bookingMapper.updateById(booking);
            return refund;
        });
    }

    @Override
    public List<PlatformRefundVO> listPlatformRefunds() {
        return refundMapper.selectPlatformRefunds().stream().map(PlatformRefundVO::from).toList();
    }

    @Override
    public RefundVO retryRefund(long refundId) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return RefundVO.from(processRefund(refund.bookingId));
    }

    @Override
    public void retryDueRefunds() {
        for (Long refundId : refundMapper.selectRetryIds(Instant.now())) {
            Refund refund = refundMapper.selectById(refundId);
            if (refund != null) {
                processRefund(refund.bookingId);
            }
        }
    }

    @Override
    public Payment findPayment(long bookingId) {
        return paymentMapper.selectOne(new QueryWrapper<Payment>().eq("booking_id", bookingId));
    }

    @Override
    public Refund findRefund(long bookingId) {
        return refundMapper.selectOne(new QueryWrapper<Refund>().eq("booking_id", bookingId));
    }

    private void releaseSlot(SessionSlot slot, long bookingId) {
        if (slot != null && slot.status == SessionSlotStatus.HELD
                && slot.holdBookingId != null && bookingId == slot.holdBookingId) {
            slot.status = SessionSlotStatus.OPEN;
            slot.holdBookingId = null;
            slot.holdExpiresAt = null;
            sessionSlotMapper.clearBookingHold(slot);
            if (reminderService != null) reminderService.onSlotReleased(slot.id, "booking:" + bookingId);
        }
    }

    private Booking requiredLockedBooking(long bookingId) {
        Booking booking = bookingMapper.selectForUpdate(bookingId);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return booking;
    }

    private Booking requiredBooking(long bookingId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return booking;
    }

    private void requireOwner(Booking booking, long userId) {
        if (booking.userId == null || booking.userId != userId) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
    }
}
