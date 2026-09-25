package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PaymentMapper;
import com.sportsbooking.admin.biz.service.BookingSettlementService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.biz.constants.RedisLockType;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.CancelledBy;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.service.PaymentService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private final RedisLockService redisLockService;
    private final MemberFundsService memberFundsService;
    private final PaymentService paymentService;

    public BookingSettlementServiceImpl(BookingMapper bookingMapper, PaymentMapper paymentMapper,
                                        ScheduleService scheduleService, RedisLockService redisLockService,
                                        MemberFundsService memberFundsService, PaymentService paymentService) {
        this.bookingMapper = bookingMapper;
        this.paymentMapper = paymentMapper;
        this.scheduleService = scheduleService;
        this.redisLockService = redisLockService;
        this.memberFundsService = memberFundsService;
        this.paymentService = paymentService;
    }

    /**
     * 功能描述: 定时释放支付超时占位并标记未到场预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Scheduled(fixedDelayString = "${app.booking.settlement-delay-ms:60000}")
    public void settleTimedBookings() {
        Instant now = Instant.now();
        // 每笔独立加锁和提交，避免一批任务共同持有 30 秒租期的业务锁。
        for (Long bookingId : bookingMapper.selectExpiredPendingIds(now)) {
            settleExpiredBooking(bookingId, now);
        }
        for (Long bookingId : bookingMapper.selectNoShowIds(now)) {
            settleNoShowBooking(bookingId, now);
        }
        for (Long bookingId : bookingMapper.selectEndedUntrackedIds(now)) {
            settleEndedUntrackedBooking(bookingId, now);
        }
        paymentService.retryDueRefunds();
    }

    /** 支付超时按预约和具体场次锁在独立事务内收敛。 */
    private void settleExpiredBooking(long bookingId, Instant now) {
        Booking candidate = bookingMapper.selectById(bookingId);
        if (candidate == null) {
            return;
        }
        // 超时前先在事务和 Redis 锁外查单；未知结果才进入关闭流程。
        paymentService.queryPayment(bookingId);
        candidate = bookingMapper.selectById(bookingId);
        if (candidate == null || candidate.status != BookingStatus.PENDING_PAYMENT) {
            return;
        }
        Booking lockedCandidate = candidate;
        redisLockService.execute(List.of(
                RedisLockType.BOOKING.key(bookingId), RedisLockType.SESSION_SLOT.key(candidate.sessionSlotId),
                RedisLockType.MEMBER_BALANCE.key(candidate.venueCustomerId)), () -> {
            Booking booking = bookingMapper.selectForUpdate(bookingId);
            if (booking != null && booking.sessionSlotId.equals(lockedCandidate.sessionSlotId)
                    && BookingStatus.PENDING_PAYMENT.equals(booking.status)) {
                // 锁定后再次确认状态，使重复调度只执行一次并与场次释放保持原子提交。
                booking.status = BookingStatus.CANCELLED;
                booking.paymentStatus = BookingPaymentStatus.PAYMENT_FAILED;
                booking.cancelledBy = CancelledBy.SYSTEM;
                booking.cancelReason = "支付超时";
                booking.cancelledAt = now;
                booking.updatedAt = LocalDateTime.now();
                bookingMapper.updateById(booking);
                scheduleService.releaseBookingSlot(booking.sessionSlotId);
                memberFundsService.release(booking.id, now);
                paymentService.closePending(booking.id, now);
            }
            return null;
        });
    }

    /** 未到场只更新预约状态，使用预约锁与核销、取消串行。 */
    private void settleNoShowBooking(long bookingId, Instant now) {
        redisLockService.execute(List.of(RedisLockType.BOOKING.key(bookingId)), () -> {
            Booking booking = bookingMapper.selectForUpdate(bookingId);
            if (booking != null && booking.source == BookingSource.ONLINE
                    && BookingStatus.CONFIRMED.equals(booking.status)) {
                // 只有结束后仍为已确认的预约才标记未到场，已核销和已取消状态保持不变。
                booking.status = BookingStatus.NO_SHOW;
                booking.completedAt = now;
                booking.updatedAt = LocalDateTime.now();
                bookingMapper.updateById(booking);
            }
            return null;
        });
    }

    /** 已结束的线下预约只收敛为履约未跟踪，不生成未到场事实。 */
    private void settleEndedUntrackedBooking(long bookingId, Instant now) {
        redisLockService.execute(List.of(RedisLockType.BOOKING.key(bookingId)), () -> {
            Booking booking = bookingMapper.selectForUpdate(bookingId);
            if (booking != null && booking.source == BookingSource.OFFLINE
                    && BookingStatus.CONFIRMED.equals(booking.status)) {
                booking.status = BookingStatus.ENDED_UNTRACKED;
                booking.completedAt = now;
                booking.updatedAt = LocalDateTime.now();
                bookingMapper.updateById(booking);
            }
            return null;
        });
    }

}
