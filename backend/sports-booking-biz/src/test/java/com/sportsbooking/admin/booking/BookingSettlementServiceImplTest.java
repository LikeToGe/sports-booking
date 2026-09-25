package com.sportsbooking.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PaymentMapper;
import com.sportsbooking.admin.biz.service.impl.BookingSettlementServiceImpl;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.CancelledBy;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.service.PaymentService;
import java.util.List;
import java.util.function.Supplier;
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
        RedisLockService redisLockService = mock(RedisLockService.class);
        MemberFundsService memberFundsService = mock(MemberFundsService.class);
        PaymentService paymentService = mock(PaymentService.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(redisLockService).execute(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        BookingSettlementServiceImpl service = new BookingSettlementServiceImpl(
                bookingMapper, paymentMapper, scheduleService, redisLockService, memberFundsService, paymentService);
        Booking booking = new Booking();
        booking.id = 1L;
        booking.sessionSlotId = 2L;
        booking.venueCustomerId = 3L;
        booking.status = BookingStatus.PENDING_PAYMENT;
        Payment payment = new Payment();
        payment.status = PaymentStatus.PENDING;
        when(bookingMapper.selectExpiredPendingIds(any())).thenReturn(List.of(1L));
        when(bookingMapper.selectNoShowIds(any())).thenReturn(List.of());
        when(bookingMapper.selectForUpdate(1L)).thenReturn(booking);
        when(bookingMapper.selectById(1L)).thenReturn(booking);

        service.settleTimedBookings();

        assertThat(booking.status).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.cancelledBy).isEqualTo(CancelledBy.SYSTEM);
        verify(paymentService).queryPayment(1L);
        verify(paymentService).closePending(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(
                booking.cancelledAt));
        verify(scheduleService).releaseBookingSlot(2L);
        verify(bookingMapper).updateById(booking);
    }

    @Test
    void endsOfflineBookingWithoutMarkingNoShowOrTouchingPayment() {
        BookingMapper bookingMapper = mock(BookingMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        ScheduleService scheduleService = mock(ScheduleService.class);
        RedisLockService redisLockService = mock(RedisLockService.class);
        MemberFundsService memberFundsService = mock(MemberFundsService.class);
        PaymentService paymentService = mock(PaymentService.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(redisLockService).execute(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        BookingSettlementServiceImpl service = new BookingSettlementServiceImpl(
                bookingMapper, paymentMapper, scheduleService, redisLockService, memberFundsService, paymentService);
        Booking booking = new Booking();
        booking.id = 5L;
        booking.source = BookingSource.OFFLINE;
        booking.status = BookingStatus.CONFIRMED;
        when(bookingMapper.selectExpiredPendingIds(any())).thenReturn(List.of());
        when(bookingMapper.selectNoShowIds(any())).thenReturn(List.of());
        when(bookingMapper.selectEndedUntrackedIds(any())).thenReturn(List.of(5L));
        when(bookingMapper.selectForUpdate(5L)).thenReturn(booking);

        service.settleTimedBookings();

        assertThat(booking.status).isEqualTo(BookingStatus.ENDED_UNTRACKED);
        verify(bookingMapper).updateById(booking);
        verify(paymentMapper, never()).updateById((Payment) any());
        verify(scheduleService, never()).releaseBookingSlot(anyLong());
    }
}
