package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.base.bo.BalanceSummaryBO;
import com.sportsbooking.admin.base.enums.BalanceBusinessType;
import com.sportsbooking.admin.base.enums.BalanceFlowDirection;
import com.sportsbooking.admin.base.enums.BalanceHoldStatus;
import com.sportsbooking.admin.base.enums.BalanceLotStatus;
import com.sportsbooking.admin.base.enums.BalanceType;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.RechargeChannel;
import com.sportsbooking.admin.base.enums.RechargePlanStatus;
import com.sportsbooking.admin.base.enums.RechargeStatus;
import com.sportsbooking.admin.base.enums.VenueCustomerStatus;
import com.sportsbooking.admin.base.mapper.BookingBalanceHoldMapper;
import com.sportsbooking.admin.base.mapper.MemberBalanceFlowMapper;
import com.sportsbooking.admin.base.mapper.MemberBalanceLotMapper;
import com.sportsbooking.admin.base.mapper.MemberRechargeMapper;
import com.sportsbooking.admin.base.mapper.RechargePlanMapper;
import com.sportsbooking.admin.base.mapper.VenueCustomerMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.BookingBalanceHold;
import com.sportsbooking.admin.base.po.MemberBalanceFlow;
import com.sportsbooking.admin.base.po.MemberBalanceLot;
import com.sportsbooking.admin.base.po.MemberRecharge;
import com.sportsbooking.admin.base.po.RechargePlan;
import com.sportsbooking.admin.base.po.VenueCustomer;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.biz.constants.RedisLockType;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.RechargePlanRequest;
import com.sportsbooking.admin.biz.pojo.vo.BalanceAccountVO;
import com.sportsbooking.admin.biz.pojo.vo.BalanceFlowVO;
import com.sportsbooking.admin.biz.pojo.vo.MemberRechargeVO;
import com.sportsbooking.admin.biz.pojo.vo.PaymentCompositionLotVO;
import com.sportsbooking.admin.biz.pojo.vo.PaymentCompositionVO;
import com.sportsbooking.admin.biz.pojo.vo.RechargePlanVO;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.biz.service.NotificationService;
import com.sportsbooking.admin.biz.service.PaymentChannelAdapter;
import com.sportsbooking.admin.base.enums.PaymentResult;
import com.sportsbooking.admin.biz.service.VenueCustomerService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 会员资金账本实现；所有余额变更均锁批次并追加幂等流水。 */
@Service
public class MemberFundsServiceImpl implements MemberFundsService {
    private final RechargePlanMapper rechargePlanMapper;
    private final MemberRechargeMapper rechargeMapper;
    private final MemberBalanceLotMapper lotMapper;
    private final BookingBalanceHoldMapper holdMapper;
    private final MemberBalanceFlowMapper flowMapper;
    private final VenueCustomerMapper customerMapper;
    private final RedisLockService lockService;
    private final NotificationService notificationService;
    private final PaymentChannelAdapter channelAdapter;
    private final VenueCustomerService venueCustomerService;

    public MemberFundsServiceImpl(RechargePlanMapper rechargePlanMapper, MemberRechargeMapper rechargeMapper,
                                  MemberBalanceLotMapper lotMapper, BookingBalanceHoldMapper holdMapper,
                                  MemberBalanceFlowMapper flowMapper, VenueCustomerMapper customerMapper,
                                  RedisLockService lockService, NotificationService notificationService,
                                  PaymentChannelAdapter channelAdapter, VenueCustomerService venueCustomerService) {
        this.rechargePlanMapper = rechargePlanMapper;
        this.rechargeMapper = rechargeMapper;
        this.lotMapper = lotMapper;
        this.holdMapper = holdMapper;
        this.flowMapper = flowMapper;
        this.customerMapper = customerMapper;
        this.lockService = lockService;
        this.notificationService = notificationService;
        this.channelAdapter = channelAdapter;
        this.venueCustomerService = venueCustomerService;
    }

    @Override
    public List<RechargePlanVO> listPlans(long venueId, boolean includeDisabled) {
        QueryWrapper<RechargePlan> query = new QueryWrapper<RechargePlan>().eq("venue_id", venueId).orderByAsc("id");
        if (!includeDisabled) {
            query.eq("status", RechargePlanStatus.ACTIVE);
        }
        return rechargePlanMapper.selectList(query).stream().map(RechargePlanVO::from).toList();
    }

    @Override
    public RechargePlanVO createPlan(long venueId, RechargePlanRequest request) {
        validatePlan(request);
        RechargePlan plan = new RechargePlan();
        applyPlan(plan, venueId, request, RechargePlanStatus.ACTIVE);
        rechargePlanMapper.insert(plan);
        return RechargePlanVO.from(plan);
    }

    @Override
    public RechargePlanVO updatePlan(long venueId, long planId, RechargePlanRequest request) {
        validatePlan(request);
        RechargePlan plan = rechargePlanMapper.selectById(planId);
        if (plan == null || plan.venueId != venueId) {
            throw notFound();
        }
        applyPlan(plan, venueId, request, request.getStatus() == null ? plan.status : request.getStatus());
        rechargePlanMapper.updateById(plan);
        return RechargePlanVO.from(plan);
    }

    @Override
    public MemberRechargeVO createSelfRecharge(long userId, long venueId, long planId, String idempotencyKey) {
        String key = requireText(idempotencyKey);
        VenueCustomer customer = venueCustomerService.ensureCustomer(venueId, userId);
        MemberRecharge existing = rechargeMapper.selectByClientKey(customer.id, key);
        if (existing != null) {
            return toRecharge(existing, venueId);
        }
        RechargePlan plan = requiredActivePlan(venueId, planId);
        MemberRecharge recharge = newRecharge(customer.id, plan, RechargeChannel.MOCK, null, null, key);
        MemberRecharge inserted = lockService.execute(List.of(RedisLockType.MEMBER_BALANCE.key(customer.id)), () -> {
            if (customerMapper.selectActiveUserForUpdate(customer.id) == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,ErrorCode.USER_DISABLED);
            }
            MemberRecharge duplicate = rechargeMapper.selectByClientKey(customer.id,key);
            if (duplicate != null) return duplicate;
            try { rechargeMapper.insert(recharge); }
            catch (DuplicateKeyException exception) {
                return rechargeMapper.selectByClientKey(customer.id,key);
            }
            return recharge;
        });
        if (inserted != recharge) return toRecharge(inserted,venueId);
        PaymentResult result = channelAdapter.startRecharge(recharge);
        if (result != PaymentResult.UNKNOWN) {
            return convergeRecharge(userId, recharge.id, result);
        }
        return toRecharge(recharge, venueId);
    }

    @Override
    public MemberRechargeVO confirmMockRecharge(long userId, long rechargeId) {
        return convergeRecharge(userId, rechargeId, PaymentResult.SUCCESS);
    }

    @Override
    public MemberRechargeVO handleMockRechargeNotification(String businessNo, int amountFen, PaymentResult result) {
        MemberRecharge recharge = rechargeMapper.selectByBusinessNo(businessNo);
        if (recharge == null) {
            throw notFound();
        }
        if (result == null || recharge.paidAmountFen != amountFen) {
            throw invalid();
        }
        VenueCustomer customer = requiredCustomer(recharge.venueCustomerId);
        return convergeRecharge(customer.userId, recharge.id, result);
    }

    /** 成功、失败与关闭终态不倒退；渠道确认闭单后的矛盾成功不允许重新入账。 */
    private MemberRechargeVO convergeRecharge(long userId, long rechargeId, PaymentResult result) {
        MemberRecharge candidate = requiredOwnedRecharge(userId, rechargeId);
        return lockService.execute(List.of(RedisLockType.RECHARGE.key(rechargeId),
                RedisLockType.MEMBER_BALANCE.key(candidate.venueCustomerId)), () -> {
            MemberRecharge recharge = rechargeMapper.selectForUpdate(rechargeId);
            VenueCustomer customer = requiredCustomer(recharge.venueCustomerId);
            if (recharge.status == RechargeStatus.SUCCESS) {
                return toRecharge(recharge, customer.venueId);
            }
            // 已确认未付关闭后，矛盾的成功通知必须拒绝，不能恢复会员余额或掩盖异常。
            if (recharge.status == RechargeStatus.CLOSED && result == PaymentResult.SUCCESS) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
            }
            if (recharge.status == RechargeStatus.FAILED || recharge.status == RechargeStatus.CLOSED
                    || recharge.status == RechargeStatus.REVERSED) {
                return toRecharge(recharge, customer.venueId);
            }
            if (result == PaymentResult.UNKNOWN) {
                return toRecharge(recharge, customer.venueId);
            }
            if (recharge.status != RechargeStatus.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
            }
            if (result == PaymentResult.SUCCESS) {
                recharge.providerNo = "MOCK-RECHARGE-" + recharge.rechargeNo;
                completeRecharge(recharge, customer, Instant.now());
            } else {
                recharge.status = RechargeStatus.FAILED;
                recharge.updatedAt = LocalDateTime.now();
                rechargeMapper.updateById(recharge);
            }
            return toRecharge(recharge, customer.venueId);
        });
    }

    @Override
    public MemberRechargeVO createAdminRecharge(long venueId, Long operatorAdminId, Long operatorEmployeeId,
                                                long customerId,
                                                 long planId, RechargeChannel channel, String idempotencyKey) {
        if (channel == null || channel == RechargeChannel.MOCK) {
            throw invalid();
        }
        VenueCustomer customer = requiredVenueCustomer(venueId, customerId);
        String key = requireText(idempotencyKey);
        MemberRecharge recharge = rechargeMapper.selectByClientKey(customer.id, key);
        if (recharge == null) {
            RechargePlan plan = requiredActivePlan(venueId, planId);
            recharge = newRecharge(customer.id, plan, channel, operatorAdminId, operatorEmployeeId, key);
            try {
                rechargeMapper.insert(recharge);
            } catch (DuplicateKeyException exception) {
                recharge = rechargeMapper.selectByClientKey(customer.id, key);
            }
        }
        long rechargeId = recharge.id;
        return lockService.execute(List.of(RedisLockType.RECHARGE.key(rechargeId),
                RedisLockType.MEMBER_BALANCE.key(customer.id)), () -> {
            MemberRecharge locked = rechargeMapper.selectForUpdate(rechargeId);
            if (locked.status == RechargeStatus.PENDING) {
                completeRecharge(locked, requiredVenueCustomer(venueId, customer.id), Instant.now());
            }
            return toRecharge(locked, venueId);
        });
    }

    @Override
    public MemberRechargeVO cancelRecharge(long userId, long rechargeId) {
        MemberRecharge candidate = requiredOwnedRecharge(userId, rechargeId);
        if (candidate.status != RechargeStatus.PENDING) {
            return toRecharge(candidate, requiredCustomer(candidate.venueCustomerId).venueId);
        }
        if (candidate.channel != RechargeChannel.MOCK) throw invalid();
        // 外部关闭不持有业务锁或事务，返回后必须复核并发通知已写入的终态。
        PaymentResult result = channelAdapter.closeRecharge(candidate);
        return lockService.execute(List.of(RedisLockType.RECHARGE.key(rechargeId),
                RedisLockType.MEMBER_BALANCE.key(candidate.venueCustomerId)), () -> {
            MemberRecharge recharge = rechargeMapper.selectForUpdate(rechargeId);
            if (recharge == null) throw notFound();
            VenueCustomer customer = requiredCustomer(recharge.venueCustomerId);
            if (customer.userId != userId) throw notFound();
            if (recharge.status != RechargeStatus.PENDING) return toRecharge(recharge, customer.venueId);
            if (result == null || result == PaymentResult.UNKNOWN) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
            }
            if (result == PaymentResult.SUCCESS) {
                recharge.providerNo = "MOCK-RECHARGE-" + recharge.rechargeNo;
                completeRecharge(recharge, customer, Instant.now());
            } else {
                recharge.status = RechargeStatus.CLOSED;
                recharge.updatedAt = LocalDateTime.now();
                rechargeMapper.updateById(recharge);
            }
            return toRecharge(recharge, customer.venueId);
        });
    }

    @Override
    public List<MemberRechargeVO> listOwnRecharges(long userId, long venueId) {
        VenueCustomer customer = requiredCustomer(userId, venueId);
        return rechargeMapper.selectByCustomer(customer.id).stream().map(row -> toRecharge(row, venueId)).toList();
    }

    @Override
    public MemberRechargeVO getRecharge(long userId, long rechargeId) {
        MemberRecharge recharge = requiredOwnedRecharge(userId, rechargeId);
        return toRecharge(recharge, requiredCustomer(recharge.venueCustomerId).venueId);
    }

    @Override
    public List<MemberRechargeVO> listRecharges(long venueId, long customerId) {
        requiredVenueCustomer(venueId, customerId);
        return rechargeMapper.selectByCustomer(customerId).stream().map(row -> toRecharge(row, venueId)).toList();
    }

    @Override
    public MemberRechargeVO reverseRecharge(long venueId, Long operatorAdminId, Long operatorEmployeeId,
                                            long rechargeId,
                                            String reason, boolean offlineFundsHandled) {
        String normalizedReason = requireText(reason);
        MemberRecharge candidate = rechargeMapper.selectById(rechargeId);
        if (candidate == null) {
            throw notFound();
        }
        VenueCustomer candidateCustomer = requiredVenueCustomer(venueId, candidate.venueCustomerId);
        return lockService.execute(List.of(RedisLockType.RECHARGE.key(rechargeId),
                RedisLockType.MEMBER_BALANCE.key(candidateCustomer.id)), () -> {
            MemberRecharge recharge = rechargeMapper.selectForUpdate(rechargeId);
            VenueCustomer customer = requiredVenueCustomer(venueId, recharge.venueCustomerId);
            if (recharge.status != RechargeStatus.SUCCESS || flowMapper.countConsumedByRecharge(recharge.id) > 0
                    || (operatorEmployeeId != null && operatorEmployeeId.equals(recharge.operatorEmployeeId))
                    || (recharge.channel != RechargeChannel.MOCK && !offlineFundsHandled)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RECHARGE_NOT_REVERSIBLE);
            }
            Instant now = Instant.now();
            for (MemberBalanceLot lot : lotMapper.selectByRechargeForUpdate(recharge.id)) {
                if (lot.frozenAmountFen > 0 || !lot.availableAmountFen.equals(lot.originalAmountFen)) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RECHARGE_NOT_REVERSIBLE);
                }
                lot.availableAmountFen = 0;
                lot.status = BalanceLotStatus.REVERSED;
                lot.updatedAt = LocalDateTime.now();
                lotMapper.updateById(lot);
                addFlow(customer.id, lot.id, BalanceFlowDirection.DEBIT, lot.originalAmountFen,
                        BalanceBusinessType.REVERSE, null, recharge.id, operatorAdminId, operatorEmployeeId,
                        "recharge:" + recharge.id + ":reverse:" + lot.id);
            }
            recharge.status = RechargeStatus.REVERSED;
            recharge.reverseReason = normalizedReason;
            recharge.offlineFundsHandled = offlineFundsHandled;
            recharge.offlineFundsHandledAt = offlineFundsHandled ? now : null;
            recharge.reversedAt = now;
            recharge.updatedAt = LocalDateTime.now();
            rechargeMapper.updateById(recharge);
            return toRecharge(recharge, venueId);
        });
    }

    @Override
    public BalanceAccountVO getAccountByUser(long userId, long venueId) {
        VenueCustomer customer = requiredCustomer(userId, venueId);
        return account(venueId, customer.id);
    }

    @Override
    public BalanceAccountVO getAccountByVenue(long venueId, long customerId) {
        requiredVenueCustomer(venueId, customerId);
        return account(venueId, customerId);
    }

    @Override
    public void freeze(Booking booking, long venueCustomerId, Instant now) {
        int remaining = booking.amountFen;
        int gift = 0;
        int paid = 0;
        for (MemberBalanceLot lot : lotMapper.selectSpendableForUpdate(venueCustomerId, now)) {
            if (remaining == 0) {
                break;
            }
            int amount = Math.min(remaining, lot.availableAmountFen);
            lot.availableAmountFen -= amount;
            lot.frozenAmountFen += amount;
            lot.updatedAt = LocalDateTime.now();
            lotMapper.updateById(lot);
            BookingBalanceHold hold = new BookingBalanceHold();
            hold.bookingId = booking.id;
            hold.lotId = lot.id;
            hold.amountFen = amount;
            hold.status = BalanceHoldStatus.FROZEN;
            hold.frozenAt = now;
            hold.createdAt = LocalDateTime.now();
            hold.updatedAt = hold.createdAt;
            holdMapper.insert(hold);
            addFlow(venueCustomerId, lot.id, BalanceFlowDirection.DEBIT, amount, BalanceBusinessType.HOLD,
                    booking.id, null, null, null, "booking:" + booking.id + ":hold:" + lot.id);
            if (lot.type == BalanceType.GIFT) {
                gift += amount;
            } else {
                paid += amount;
            }
            remaining -= amount;
        }
        booking.giftBalanceAmountFen = gift;
        booking.paidBalanceAmountFen = paid;
        booking.balanceAmountFen = gift + paid;
        booking.externalAmountFen = remaining;
    }

    @Override
    public void consume(long bookingId, Instant now) {
        for (BookingBalanceHold hold : holdMapper.selectByBookingForUpdate(bookingId)) {
            if (hold.status != BalanceHoldStatus.FROZEN) {
                continue;
            }
            MemberBalanceLot lot = lotMapper.selectForUpdate(hold.lotId);
            lot.frozenAmountFen -= hold.amountFen;
            if (lot.type == BalanceType.GIFT && !lot.expiresAt.isAfter(now)
                    && lot.availableAmountFen == 0 && lot.frozenAmountFen == 0) {
                lot.status = BalanceLotStatus.EXPIRED;
            }
            lot.updatedAt = LocalDateTime.now();
            lotMapper.updateById(lot);
            hold.status = BalanceHoldStatus.CONSUMED;
            hold.consumedAt = now;
            hold.updatedAt = LocalDateTime.now();
            holdMapper.updateById(hold);
            addFlow(lot.venueCustomerId, lot.id, BalanceFlowDirection.DEBIT, hold.amountFen,
                    BalanceBusinessType.CONSUME, bookingId, null, null, null,
                    "booking:" + bookingId + ":consume:" + lot.id);
        }
    }

    @Override
    public void release(long bookingId, Instant now) {
        for (BookingBalanceHold hold : holdMapper.selectByBookingForUpdate(bookingId)) {
            if (hold.status != BalanceHoldStatus.FROZEN) {
                continue;
            }
            MemberBalanceLot lot = lotMapper.selectForUpdate(hold.lotId);
            lot.frozenAmountFen -= hold.amountFen;
            boolean expiredGift = lot.type == BalanceType.GIFT && !lot.expiresAt.isAfter(now);
            if (expiredGift) {
                lot.status = BalanceLotStatus.EXPIRED;
                addFlow(lot.venueCustomerId, lot.id, BalanceFlowDirection.DEBIT, hold.amountFen,
                        BalanceBusinessType.EXPIRE, bookingId, null, null, null,
                        "booking:" + bookingId + ":release-expire:" + lot.id);
            } else {
                lot.availableAmountFen += hold.amountFen;
                addFlow(lot.venueCustomerId, lot.id, BalanceFlowDirection.CREDIT, hold.amountFen,
                        BalanceBusinessType.RELEASE, bookingId, null, null, null,
                        "booking:" + bookingId + ":release:" + lot.id);
            }
            lot.updatedAt = LocalDateTime.now();
            lotMapper.updateById(lot);
            hold.status = BalanceHoldStatus.RELEASED;
            hold.releasedAt = now;
            hold.updatedAt = LocalDateTime.now();
            holdMapper.updateById(hold);
        }
    }

    @Override
    public void refund(long bookingId, Instant now) {
        for (BookingBalanceHold hold : holdMapper.selectByBookingForUpdate(bookingId)) {
            if (hold.status != BalanceHoldStatus.CONSUMED) {
                continue;
            }
            MemberBalanceLot lot = lotMapper.selectForUpdate(hold.lotId);
            boolean expiredGift = lot.type == BalanceType.GIFT && !lot.expiresAt.isAfter(now);
            if (!expiredGift && lot.status != BalanceLotStatus.REVERSED) {
                lot.availableAmountFen += hold.amountFen;
                addFlow(lot.venueCustomerId, lot.id, BalanceFlowDirection.CREDIT, hold.amountFen,
                        BalanceBusinessType.REFUND, bookingId, null, null, null,
                        "booking:" + bookingId + ":refund:" + lot.id);
            } else {
                lot.status = BalanceLotStatus.EXPIRED;
                addFlow(lot.venueCustomerId, lot.id, BalanceFlowDirection.DEBIT, hold.amountFen,
                        BalanceBusinessType.EXPIRE, bookingId, null, null, null,
                        "booking:" + bookingId + ":refund-expire:" + lot.id);
            }
            lot.updatedAt = LocalDateTime.now();
            lotMapper.updateById(lot);
            hold.status = BalanceHoldStatus.RELEASED;
            hold.releasedAt = now;
            hold.updatedAt = LocalDateTime.now();
            holdMapper.updateById(hold);
        }
    }

    @Override
    public PaymentCompositionVO getComposition(Booking booking) {
        List<PaymentCompositionLotVO> lots = new ArrayList<>();
        for (BookingBalanceHold hold : holdMapper.selectByBooking(booking.id)) {
            MemberBalanceLot lot = lotMapper.selectById(hold.lotId);
            lots.add(new PaymentCompositionLotVO(hold.id, hold.lotId, lot.type, hold.amountFen,
                    hold.status, lot.expiresAt));
        }
        return new PaymentCompositionVO(booking.amountFen, booking.balanceAmountFen, booking.giftBalanceAmountFen,
                booking.paidBalanceAmountFen, booking.externalAmountFen, lots);
    }

    @Override
    @Scheduled(cron = "0 10 0 * * *", zone = BusinessConstants.BUSINESS_ZONE_ID)
    public void settleGiftLots() {
        Instant now = Instant.now();
        Instant remindBefore = now.plusSeconds(7L * 24 * 60 * 60);
        for (MemberBalanceLot lot : lotMapper.selectExpiringGiftLots(remindBefore, now)) {
            VenueCustomer customer = requiredCustomer(lot.venueCustomerId);
            notificationService.recordBalanceExpiry(customer.userId, lot.id, lot.availableAmountFen, lot.expiresAt);
        }
        for (MemberBalanceLot candidate : lotMapper.selectExpiredGiftLots(now)) {
            lockService.execute(List.of(RedisLockType.MEMBER_BALANCE.key(candidate.venueCustomerId)), () -> {
                MemberBalanceLot lot = lotMapper.selectForUpdate(candidate.id);
                if (lot != null && lot.status == BalanceLotStatus.ACTIVE && lot.availableAmountFen > 0
                        && !lot.expiresAt.isAfter(now)) {
                    int expired = lot.availableAmountFen;
                    lot.availableAmountFen = 0;
                    if (lot.frozenAmountFen == 0) {
                        lot.status = BalanceLotStatus.EXPIRED;
                    }
                    lot.updatedAt = LocalDateTime.now();
                    lotMapper.updateById(lot);
                    addFlow(lot.venueCustomerId, lot.id, BalanceFlowDirection.DEBIT, expired,
                            BalanceBusinessType.EXPIRE, null, null, null, null, "lot:" + lot.id + ":expire");
                }
                return null;
            });
        }
    }

    /** 待支付充值在事务和锁外查单，再按充值与余额锁幂等收敛。 */
    @Scheduled(fixedDelayString = "${app.booking.settlement-delay-ms:60000}")
    public void reconcilePendingRecharges() {
        for (Long rechargeId : rechargeMapper.selectPendingIds()) {
            MemberRecharge recharge = rechargeMapper.selectById(rechargeId);
            if (recharge == null || recharge.status != RechargeStatus.PENDING) {
                continue;
            }
            PaymentResult result = channelAdapter.queryRecharge(recharge);
            if (result != PaymentResult.UNKNOWN) {
                VenueCustomer customer = requiredCustomer(recharge.venueCustomerId);
                convergeRecharge(customer.userId, recharge.id, result);
            }
        }
    }

    private void completeRecharge(MemberRecharge recharge, VenueCustomer customer, Instant now) {
        if (recharge.status == RechargeStatus.SUCCESS) {
            return;
        }
        if (customerMapper.selectActiveUserForUpdate(customer.id) == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,ErrorCode.USER_DISABLED);
        }
        recharge.status = RechargeStatus.SUCCESS;
        recharge.succeededAt = now;
        recharge.updatedAt = LocalDateTime.now();
        rechargeMapper.updateById(recharge);
        createLot(recharge, customer.id, BalanceType.PAID, recharge.paidAmountFen, null);
        if (recharge.giftAmountFen > 0) {
            ZonedDateTime local = now.atZone(BusinessConstants.BUSINESS_ZONE);
            Instant expiresAt = local.toLocalDate().plusDays(recharge.giftValidDays)
                    .atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
            createLot(recharge, customer.id, BalanceType.GIFT, recharge.giftAmountFen, expiresAt);
        }
        if (customer.status != VenueCustomerStatus.MEMBER) {
            customer.status = VenueCustomerStatus.MEMBER;
            customer.activatedAt = now;
            customer.updatedAt = LocalDateTime.now();
            customerMapper.updateById(customer);
        }
    }

    private void createLot(MemberRecharge recharge, long customerId, BalanceType type, int amount, Instant expiresAt) {
        MemberBalanceLot lot = new MemberBalanceLot();
        lot.venueCustomerId = customerId;
        lot.sourceRechargeId = recharge.id;
        lot.type = type;
        lot.originalAmountFen = amount;
        lot.availableAmountFen = amount;
        lot.frozenAmountFen = 0;
        lot.status = BalanceLotStatus.ACTIVE;
        lot.expiresAt = expiresAt;
        lot.createdAt = LocalDateTime.now();
        lot.updatedAt = lot.createdAt;
        lotMapper.insert(lot);
        addFlow(customerId, lot.id, BalanceFlowDirection.CREDIT, amount, BalanceBusinessType.RECHARGE,
                null, recharge.id, recharge.operatorAdminId, recharge.operatorEmployeeId,
                "recharge:" + recharge.id + ":lot:" + type);
    }

    private BalanceAccountVO account(long venueId, long customerId) {
        BalanceSummaryBO summary = lotMapper.selectSummary(customerId, Instant.now());
        if (summary == null) {
            summary = new BalanceSummaryBO();
            summary.setPaidBalanceFen(0L);
            summary.setGiftBalanceFen(0L);
            summary.setNextGiftExpiresAmountFen(0L);
        }
        List<BalanceFlowVO> flows = flowMapper.selectByCustomer(customerId).stream().map(BalanceFlowVO::from).toList();
        return new BalanceAccountVO(venueId, customerId, summary.getPaidBalanceFen(), summary.getGiftBalanceFen(),
                summary.getNextGiftExpiresAt(), summary.getNextGiftExpiresAmountFen(), flows);
    }

    private MemberRecharge newRecharge(long customerId, RechargePlan plan, RechargeChannel channel,
                                       Long operatorAdminId, Long operatorEmployeeId, String clientKey) {
        MemberRecharge recharge = new MemberRecharge();
        recharge.rechargeNo = "RCH-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        recharge.venueCustomerId = customerId;
        recharge.planId = plan.id;
        recharge.planName = plan.name;
        recharge.paidAmountFen = plan.paidAmountFen;
        recharge.giftAmountFen = plan.giftAmountFen;
        recharge.giftValidDays = plan.giftValidDays;
        recharge.channel = channel;
        recharge.operatorAdminId = operatorAdminId;
        recharge.operatorEmployeeId = operatorEmployeeId;
        recharge.clientIdempotencyKey = clientKey;
        recharge.status = RechargeStatus.PENDING;
        recharge.offlineFundsHandled = false;
        recharge.createdAt = LocalDateTime.now();
        recharge.updatedAt = recharge.createdAt;
        return recharge;
    }

    private MemberRechargeVO toRecharge(MemberRecharge recharge, long venueId) {
        Instant giftExpiresAt = null;
        if (recharge.succeededAt != null && recharge.giftAmountFen > 0) {
            giftExpiresAt = recharge.succeededAt.atZone(BusinessConstants.BUSINESS_ZONE).toLocalDate()
                    .plusDays(recharge.giftValidDays).atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        }
        return MemberRechargeVO.from(recharge, venueId, giftExpiresAt);
    }

    private void addFlow(long customerId, long lotId, BalanceFlowDirection direction, int amount,
                         BalanceBusinessType type, Long bookingId, Long rechargeId, Long operatorAdminId,
                         Long operatorEmployeeId, String key) {
        MemberBalanceFlow flow = new MemberBalanceFlow();
        flow.venueCustomerId = customerId;
        flow.lotId = lotId;
        flow.direction = direction;
        flow.amountFen = amount;
        flow.businessType = type;
        flow.bookingId = bookingId;
        flow.rechargeId = rechargeId;
        flow.operatorAdminId = operatorAdminId;
        flow.operatorEmployeeId = operatorEmployeeId;
        flow.idempotencyKey = key;
        flow.createdAt = LocalDateTime.now();
        flowMapper.insert(flow);
    }

    private RechargePlan requiredActivePlan(long venueId, long planId) {
        RechargePlan plan = rechargePlanMapper.selectById(planId);
        if (plan == null || plan.venueId != venueId || plan.status != RechargePlanStatus.ACTIVE) {
            throw notFound();
        }
        return plan;
    }

    private VenueCustomer requiredCustomer(long userId, long venueId) {
        VenueCustomer customer = customerMapper.selectByVenueAndUser(venueId, userId);
        if (customer == null) {
            throw notFound();
        }
        return customer;
    }

    private VenueCustomer requiredCustomer(long customerId) {
        VenueCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw notFound();
        }
        return customer;
    }

    private VenueCustomer requiredVenueCustomer(long venueId, long customerId) {
        VenueCustomer customer = requiredCustomer(customerId);
        if (customer.venueId != venueId) {
            throw notFound();
        }
        return customer;
    }

    private MemberRecharge requiredOwnedRecharge(long userId, long rechargeId) {
        MemberRecharge recharge = rechargeMapper.selectById(rechargeId);
        if (recharge == null || requiredCustomer(recharge.venueCustomerId).userId != userId) {
            throw notFound();
        }
        return recharge;
    }

    private void validatePlan(RechargePlanRequest request) {
        if (request == null || requireText(request.getName()).isEmpty() || request.getPaidAmountFen() == null
                || request.getPaidAmountFen() <= 0 || request.getGiftAmountFen() == null
                || request.getGiftAmountFen() < 0 || (request.getGiftAmountFen() == 0
                && request.getGiftValidDays() != null) || (request.getGiftAmountFen() > 0
                && (request.getGiftValidDays() == null || request.getGiftValidDays() <= 0))) {
            throw invalid();
        }
    }

    private void applyPlan(RechargePlan plan, long venueId, RechargePlanRequest request, RechargePlanStatus status) {
        plan.venueId = venueId;
        plan.name = request.getName().trim();
        plan.paidAmountFen = request.getPaidAmountFen();
        plan.giftAmountFen = request.getGiftAmountFen();
        plan.giftValidDays = request.getGiftValidDays();
        plan.status = status;
        if (plan.createdAt == null) {
            plan.createdAt = LocalDateTime.now();
        }
        plan.updatedAt = LocalDateTime.now();
    }

    private String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid();
        }
        return value.trim();
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }
}
