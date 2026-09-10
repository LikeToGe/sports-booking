package com.sportsbooking.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PaymentMapper;
import com.sportsbooking.admin.biz.service.impl.BookingSettlementServiceImpl;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.CancelledBy;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.biz.service.ScheduleService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 功能描述: 支付超时释放和状态结算单元测试。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
class BookingSettlementServiceImplTest {

    @Test
    void expiresPendingBookingAndReleasesSlot() {
        BookingMapper bookingMapper = mock(BookingMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        ScheduleService scheduleService = mock(ScheduleService.class);
        BookingSettlementServiceImpl service = new BookingSettlementServiceImpl(
                bookingMapper, paymentMapper, scheduleService);
        Booking booking = new Booking();
        booking.id = 1L;
        booking.sessionSlotId = 2L;
        booking.status = BookingStatus.PENDING_PAYMENT;
        Payment payment = new Payment();
        payment.status = PaymentStatus.PENDING;
        when(bookingMapper.selectExpiredPendingIds(any())).thenReturn(List.of(1L));
        when(bookingMapper.selectNoShowIds(any())).thenReturn(List.of());
        when(bookingMapper.selectForUpdate(1L)).thenReturn(booking);
        when(paymentMapper.selectOne(any())).thenReturn(payment);

        service.settleTimedBookings();

        assertThat(booking.status).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.cancelledBy).isEqualTo(CancelledBy.SYSTEM);
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED);
        verify(scheduleService).releaseBookingSlot(2L);
        verify(bookingMapper).updateById(booking);
        verify(paymentMapper).updateById(payment);
    }
}
