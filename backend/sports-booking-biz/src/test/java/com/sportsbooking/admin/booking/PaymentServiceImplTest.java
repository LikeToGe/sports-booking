package com.sportsbooking.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PaymentMapper;
import com.sportsbooking.admin.base.mapper.RefundMapper;
import com.sportsbooking.admin.biz.service.impl.PaymentServiceImpl;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.base.enums.PaymentResult;
import com.sportsbooking.admin.base.enums.RefundStatus;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.biz.service.NotificationService;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.service.PaymentChannelAdapter;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.po.Refund;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 功能描述: 模拟支付原子状态流单元测试。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
class PaymentServiceImplTest {

    @Test
    void unknownQueryKeepsPendingAndExplicitFailureReleasesBalance() {
        BookingMapper bookingMapper = mock(BookingMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        RefundMapper refundMapper = mock(RefundMapper.class);
        SessionSlotMapper slotMapper = mock(SessionSlotMapper.class);
        NotificationService notifications = mock(NotificationService.class);
        MemberFundsService funds = mock(MemberFundsService.class);
        RedisLockService locks = mock(RedisLockService.class);
        PaymentChannelAdapter adapter = mock(PaymentChannelAdapter.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get()).when(locks)
                .execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        PaymentServiceImpl service = new PaymentServiceImpl(bookingMapper, paymentMapper, refundMapper,
                slotMapper, notifications, funds, locks, adapter);
        Booking booking = new Booking();
        booking.id = 10L;
        booking.userId = 20L;
        booking.venueCustomerId = 50L;
        booking.sessionSlotId = 30L;
        booking.status = BookingStatus.PENDING_PAYMENT;
        Payment payment = new Payment();
        payment.status = PaymentStatus.PENDING;
        SessionSlot slot = new SessionSlot();
        slot.status = SessionSlotStatus.HELD;
        slot.holdBookingId = booking.id;
        when(bookingMapper.selectById(10L)).thenReturn(booking);
        when(bookingMapper.selectForUpdate(10L)).thenReturn(booking);
        when(paymentMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(payment);
        when(paymentMapper.selectByBookingForUpdate(10L)).thenReturn(payment);
        when(slotMapper.selectForUpdate(30L)).thenReturn(slot);
        when(adapter.queryPayment(payment)).thenReturn(PaymentResult.UNKNOWN, PaymentResult.FAILED);

        assertThat(service.queryPayment(20L, 10L).status).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(funds, never()).release(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
        assertThat(service.queryPayment(20L, 10L).status).isEqualTo(BookingStatus.CANCELLED);
        verify(funds).release(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any());
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void retriesFailedRefundWithSameBusinessRecord() {
        BookingMapper bookingMapper = mock(BookingMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        RefundMapper refundMapper = mock(RefundMapper.class);
        SessionSlotMapper slotMapper = mock(SessionSlotMapper.class);
        NotificationService notifications = mock(NotificationService.class);
        MemberFundsService funds = mock(MemberFundsService.class);
        RedisLockService locks = mock(RedisLockService.class);
        PaymentChannelAdapter adapter = mock(PaymentChannelAdapter.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get()).when(locks)
                .execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        PaymentServiceImpl service = new PaymentServiceImpl(bookingMapper, paymentMapper, refundMapper,
                slotMapper, notifications, funds, locks, adapter);
        Booking booking = new Booking();
        booking.id = 10L;
        booking.paymentStatus = BookingPaymentStatus.REFUNDING;
        Refund refund = new Refund();
        refund.id = 40L;
        refund.bookingId = 10L;
        refund.refundNo = "REF-40";
        refund.status = RefundStatus.PROCESSING;
        refund.retryCount = 0;
        when(refundMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(refund);
        when(refundMapper.selectByBookingForUpdate(10L)).thenReturn(refund);
        when(bookingMapper.selectForUpdate(10L)).thenReturn(booking);
        when(adapter.refund(refund)).thenReturn(PaymentResult.FAILED, PaymentResult.SUCCESS);

        service.processRefund(10L);
        assertThat(refund.status).isEqualTo(RefundStatus.FAILED);
        assertThat(booking.paymentStatus).isEqualTo(BookingPaymentStatus.REFUND_FAILED);
        service.processRefund(10L);
        assertThat(refund.status).isEqualTo(RefundStatus.SUCCESS);
        assertThat(refund.retryCount).isEqualTo(2);
        assertThat(booking.paymentStatus).isEqualTo(BookingPaymentStatus.REFUNDED);
    }

    @Test
    void lateSuccessAfterCloseCreatesOneRefundWithoutRestoringBooking() {
        BookingMapper bookingMapper = mock(BookingMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        RefundMapper refundMapper = mock(RefundMapper.class);
        SessionSlotMapper slotMapper = mock(SessionSlotMapper.class);
        NotificationService notifications = mock(NotificationService.class);
        MemberFundsService funds = mock(MemberFundsService.class);
        RedisLockService locks = mock(RedisLockService.class);
        PaymentChannelAdapter adapter = mock(PaymentChannelAdapter.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get()).when(locks)
                .execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        PaymentServiceImpl service = new PaymentServiceImpl(bookingMapper, paymentMapper, refundMapper,
                slotMapper, notifications, funds, locks, adapter);
        Booking booking = new Booking();
        booking.id = 10L;
        booking.userId = 20L;
        booking.venueCustomerId = 50L;
        booking.sessionSlotId = 30L;
        booking.status = BookingStatus.CANCELLED;
        booking.paymentStatus = BookingPaymentStatus.PAYMENT_FAILED;
        Payment payment = new Payment();
        payment.id = 40L;
        payment.paymentNo = "PAY-40";
        payment.amountFen = 1000;
        payment.status = PaymentStatus.CLOSED;
        AtomicReference<Refund> storedRefund = new AtomicReference<>();
        when(bookingMapper.selectById(10L)).thenReturn(booking);
        when(bookingMapper.selectForUpdate(10L)).thenReturn(booking);
        when(paymentMapper.selectByBookingForUpdate(10L)).thenReturn(payment);
        when(refundMapper.selectByBookingForUpdate(10L)).thenAnswer(invocation -> storedRefund.get());
        when(refundMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> storedRefund.get());
        doAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            refund.id = 60L;
            storedRefund.set(refund);
            return 1;
        }).when(refundMapper).insert(org.mockito.ArgumentMatchers.any(Refund.class));
        when(adapter.refund(org.mockito.ArgumentMatchers.any(Refund.class))).thenReturn(PaymentResult.UNKNOWN);

        service.handleMockPaymentResult(20L, 10L, PaymentResult.SUCCESS);
        service.handleMockPaymentResult(20L, 10L, PaymentResult.SUCCESS);

        assertThat(booking.status).isEqualTo(BookingStatus.CANCELLED);
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(booking.paymentStatus).isEqualTo(BookingPaymentStatus.REFUNDING);
        verify(refundMapper, org.mockito.Mockito.times(1)).insert(
                org.mockito.ArgumentMatchers.any(Refund.class));
        verify(funds, never()).consume(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmsBookingSlotAndPaymentTogether() {
        BookingMapper bookingMapper = mock(BookingMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        RefundMapper refundMapper = mock(RefundMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        RedisLockService redisLockService = mock(RedisLockService.class);
        MemberFundsService memberFundsService = mock(MemberFundsService.class);
        PaymentChannelAdapter channelAdapter = mock(PaymentChannelAdapter.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(redisLockService).execute(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        PaymentServiceImpl service = new PaymentServiceImpl(
                bookingMapper, paymentMapper, refundMapper, sessionSlotMapper, notificationService,
                memberFundsService, redisLockService, channelAdapter);
        Booking booking = new Booking();
        booking.id = 10L;
        booking.userId = 20L;
        booking.sessionSlotId = 30L;
        booking.venueCustomerId = 50L;
        booking.status = BookingStatus.PENDING_PAYMENT;
        booking.paymentExpiresAt = Instant.now().plusSeconds(60);
        SessionSlot slot = new SessionSlot();
        slot.id = 30L;
        slot.status = SessionSlotStatus.HELD;
        slot.holdBookingId = 10L;
        Payment payment = new Payment();
        payment.id = 40L;
        payment.status = PaymentStatus.PENDING;
        when(bookingMapper.selectForUpdate(10L)).thenReturn(booking);
        when(bookingMapper.selectById(10L)).thenReturn(booking);
        when(sessionSlotMapper.selectForUpdate(30L)).thenReturn(slot);
        when(paymentMapper.selectByBookingForUpdate(10L)).thenReturn(payment);

        Booking result = service.mockPay(20L, 10L);
        service.mockPay(20L, 10L);

        assertThat(result.status).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.paymentStatus).isEqualTo(BookingPaymentStatus.PAID);
        assertThat(slot.status).isEqualTo(SessionSlotStatus.BOOKED);
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS);
        verify(bookingMapper).updateById(booking);
        verify(sessionSlotMapper).updateById(slot);
        verify(paymentMapper).updateById(payment);
        verify(notificationService).record(booking, NotificationType.BOOKING_CONFIRMED);
        verify(memberFundsService, org.mockito.Mockito.times(1)).consume(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordsRefundedBookingPaymentStatus() {
        BookingMapper bookingMapper = mock(BookingMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        RefundMapper refundMapper = mock(RefundMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        RedisLockService redisLockService = mock(RedisLockService.class);
        MemberFundsService memberFundsService = mock(MemberFundsService.class);
        PaymentChannelAdapter channelAdapter = mock(PaymentChannelAdapter.class);
        PaymentServiceImpl service = new PaymentServiceImpl(
                bookingMapper, paymentMapper, refundMapper, sessionSlotMapper, notificationService,
                memberFundsService, redisLockService, channelAdapter);
        Booking booking = new Booking();
        booking.id = 10L;
        booking.paymentStatus = BookingPaymentStatus.PAID;
        Payment payment = new Payment();
        payment.id = 40L;
        payment.amountFen = 6000;
        payment.status = PaymentStatus.SUCCESS;
        when(paymentMapper.selectByBookingForUpdate(10L)).thenReturn(payment);

        service.refundAndNotify(booking, "取消");

        assertThat(booking.paymentStatus).isEqualTo(BookingPaymentStatus.REFUNDING);
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS);
        org.mockito.ArgumentCaptor<Refund> refundCaptor = org.mockito.ArgumentCaptor.forClass(Refund.class);
        verify(refundMapper).insert(refundCaptor.capture());
        assertThat(refundCaptor.getValue().status).isEqualTo(RefundStatus.PROCESSING);
        verify(bookingMapper).updateById(booking);
    }
}
