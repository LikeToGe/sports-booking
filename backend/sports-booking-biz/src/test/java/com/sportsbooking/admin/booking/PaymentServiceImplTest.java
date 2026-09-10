package com.sportsbooking.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PaymentMapper;
import com.sportsbooking.admin.base.mapper.RefundMapper;
import com.sportsbooking.admin.biz.service.impl.PaymentServiceImpl;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.NotificationType;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.biz.service.NotificationService;
import com.sportsbooking.admin.base.po.SessionSlot;
import com.sportsbooking.admin.base.mapper.SessionSlotMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 功能描述: 模拟支付原子状态流单元测试。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
class PaymentServiceImplTest {

    @Test
    void confirmsBookingSlotAndPaymentTogether() {
        BookingMapper bookingMapper = mock(BookingMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        RefundMapper refundMapper = mock(RefundMapper.class);
        SessionSlotMapper sessionSlotMapper = mock(SessionSlotMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        PaymentServiceImpl service = new PaymentServiceImpl(
                bookingMapper, paymentMapper, refundMapper, sessionSlotMapper, notificationService);
        Booking booking = new Booking();
        booking.id = 10L;
        booking.userId = 20L;
        booking.sessionSlotId = 30L;
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
        when(sessionSlotMapper.selectForUpdate(30L)).thenReturn(slot);
        when(paymentMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(payment);

        Booking result = service.mockPay(20L, 10L);

        assertThat(result.status).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(slot.status).isEqualTo(SessionSlotStatus.BOOKED);
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS);
        verify(bookingMapper).updateById(booking);
        verify(sessionSlotMapper).updateById(slot);
        verify(paymentMapper).updateById(payment);
        verify(notificationService).record(booking, NotificationType.BOOKING_CONFIRMED);
    }
}
