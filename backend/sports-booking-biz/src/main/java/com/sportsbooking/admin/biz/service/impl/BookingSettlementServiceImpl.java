package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PaymentMapper;
import com.sportsbooking.admin.biz.service.BookingSettlementService;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.CancelledBy;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.biz.service.ScheduleService;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能描述: 支付超时释放与未到场结算业务实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class BookingSettlementServiceImpl implements BookingSettlementService {
    private final BookingMapper bookingMapper;
    private final PaymentMapper paymentMapper;
    private final ScheduleService scheduleService;

    public BookingSettlementServiceImpl(BookingMapper bookingMapper, PaymentMapper paymentMapper,
                                        ScheduleService scheduleService) {
        this.bookingMapper = bookingMapper;
        this.paymentMapper = paymentMapper;
        this.scheduleService = scheduleService;
    }

    /**
     * 功能描述: 定时释放支付超时占位并标记未到场预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Scheduled(fixedDelayString = "${app.booking.settlement-delay-ms:60000}")
    @Transactional
    public void settleTimedBookings() {
        Instant now = Instant.now();
        // 每批最多处理 Mapper 限定的 200 笔，缩短定时事务持锁时间。
        for (Long bookingId : bookingMapper.selectExpiredPendingIds(now)) {
            Booking booking = bookingMapper.selectForUpdate(bookingId);
            if (booking != null && BookingStatus.PENDING_PAYMENT.equals(booking.status)) {
                // 锁定后再次确认状态，使重复调度只执行一次并与场次释放保持原子提交。
                booking.status = BookingStatus.CANCELLED;
                booking.cancelledBy = CancelledBy.SYSTEM;
                booking.cancelReason = "支付超时";
                booking.cancelledAt = now;
                booking.updatedAt = LocalDateTime.now();
                bookingMapper.updateById(booking);
                scheduleService.releaseBookingSlot(booking.sessionSlotId);
                Payment payment = requiredPayment(booking.id);
                payment.status = PaymentStatus.FAILED;
                payment.updatedAt = LocalDateTime.now();
                paymentMapper.updateById(payment);
            }
        }
        for (Long bookingId : bookingMapper.selectNoShowIds(now)) {
            Booking booking = bookingMapper.selectForUpdate(bookingId);
            if (booking != null && BookingStatus.CONFIRMED.equals(booking.status)) {
                // 只有结束后仍为已确认的预约才标记未到场，已核销和已取消状态保持不变。
                booking.status = BookingStatus.NO_SHOW;
                booking.completedAt = now;
                booking.updatedAt = LocalDateTime.now();
                bookingMapper.updateById(booking);
            }
        }
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
        Payment payment = paymentMapper.selectOne(new QueryWrapper<Payment>().eq("booking_id", bookingId));
        if (payment == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_MISSING);
        }
        return payment;
    }
}
