package com.sportsbooking.admin.funds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.BalanceHoldStatus;
import com.sportsbooking.admin.base.enums.BalanceLotStatus;
import com.sportsbooking.admin.base.enums.BalanceType;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.PaymentResult;
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
import com.sportsbooking.admin.base.po.MemberBalanceLot;
import com.sportsbooking.admin.base.po.MemberRecharge;
import com.sportsbooking.admin.base.po.RechargePlan;
import com.sportsbooking.admin.base.po.VenueCustomer;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.RechargePlanRequest;
import com.sportsbooking.admin.biz.service.NotificationService;
import com.sportsbooking.admin.biz.service.PaymentChannelAdapter;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.biz.service.VenueCustomerService;
import com.sportsbooking.admin.biz.service.impl.MemberFundsServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** P2 会员账本、顺序消费、跨到期与撤销专项测试。 */
class MemberFundsServiceImplTest {
    private final RechargePlanMapper planMapper = mock(RechargePlanMapper.class);
    private final MemberRechargeMapper rechargeMapper = mock(MemberRechargeMapper.class);
    private final MemberBalanceLotMapper lotMapper = mock(MemberBalanceLotMapper.class);
    private final BookingBalanceHoldMapper holdMapper = mock(BookingBalanceHoldMapper.class);
    private final MemberBalanceFlowMapper flowMapper = mock(MemberBalanceFlowMapper.class);
    private final VenueCustomerMapper customerMapper = mock(VenueCustomerMapper.class);
    private final RedisLockService lockService = mock(RedisLockService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final PaymentChannelAdapter channelAdapter = mock(PaymentChannelAdapter.class);
    private final VenueCustomerService venueCustomerService = mock(VenueCustomerService.class);
    private final MemberFundsServiceImpl service = new MemberFundsServiceImpl(planMapper, rechargeMapper, lotMapper,
            holdMapper, flowMapper, customerMapper, lockService, notificationService,
            channelAdapter, venueCustomerService);

    @BeforeEach
    void runLockedActionsInline() {
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(lockService).execute(any(), any());
    }

    @Test
    void acceptsOnlyExplainableGiftValidity() {
        service.createPlan(1L, new RechargePlanRequest("纯实付", 10000, 0, null, null));
        service.createPlan(1L, new RechargePlanRequest("含赠送", 10000, 1000, 30, null));

        assertThatThrownBy(() -> service.createPlan(
                1L, new RechargePlanRequest("错误", 10000, 1000, null, null)))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.INVALID_ARGUMENT.getDescription());
        assertThatThrownBy(() -> service.createPlan(
                1L, new RechargePlanRequest("错误", 10000, 0, 30, null)))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.INVALID_ARGUMENT.getDescription());
    }

    @Test
    void freezesGiftByExpiryThenPaidAndKeepsFixedComposition() {
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        MemberBalanceLot gift = lot(1L, BalanceType.GIFT, 3000, now.plusSeconds(3600));
        MemberBalanceLot paid = lot(2L, BalanceType.PAID, 5000, null);
        when(lotMapper.selectSpendableForUpdate(9L, now)).thenReturn(List.of(gift, paid));
        Booking booking = new Booking();
        booking.id = 10L;
        booking.amountFen = 6000;

        service.freeze(booking, 9L, now);

        assertThat(booking.giftBalanceAmountFen).isEqualTo(3000);
        assertThat(booking.paidBalanceAmountFen).isEqualTo(3000);
        assertThat(booking.balanceAmountFen).isEqualTo(6000);
        assertThat(booking.externalAmountFen).isZero();
        assertThat(gift.availableAmountFen).isZero();
        assertThat(paid.availableAmountFen).isEqualTo(2000);
        ArgumentCaptor<BookingBalanceHold> holds = ArgumentCaptor.forClass(BookingBalanceHold.class);
        verify(holdMapper, org.mockito.Mockito.times(2)).insert(holds.capture());
        assertThat(holds.getAllValues()).extracting(row -> row.lotId).containsExactly(1L, 2L);
    }

    @Test
    void releasedExpiredGiftNeverReturnsToAvailableBalance() {
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        MemberBalanceLot gift = lot(1L, BalanceType.GIFT, 0, now.minusSeconds(1));
        gift.frozenAmountFen = 1000;
        BookingBalanceHold hold = new BookingBalanceHold();
        hold.bookingId = 10L;
        hold.lotId = gift.id;
        hold.amountFen = 1000;
        hold.status = BalanceHoldStatus.FROZEN;
        when(holdMapper.selectByBookingForUpdate(10L)).thenReturn(List.of(hold));
        when(lotMapper.selectForUpdate(1L)).thenReturn(gift);

        service.release(10L, now);

        assertThat(gift.availableAmountFen).isZero();
        assertThat(gift.frozenAmountFen).isZero();
        assertThat(gift.status).isEqualTo(BalanceLotStatus.EXPIRED);
        assertThat(hold.status).isEqualTo(BalanceHoldStatus.RELEASED);
    }

    @Test
    void consumedGiftRefundedAfterExpiryRemainsUnavailable() {
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        MemberBalanceLot gift = lot(1L, BalanceType.GIFT, 0, now.minusSeconds(1));
        BookingBalanceHold hold = new BookingBalanceHold();
        hold.bookingId = 10L;
        hold.lotId = gift.id;
        hold.amountFen = 1000;
        hold.status = BalanceHoldStatus.CONSUMED;
        when(holdMapper.selectByBookingForUpdate(10L)).thenReturn(List.of(hold));
        when(lotMapper.selectForUpdate(1L)).thenReturn(gift);

        service.refund(10L, now);

        assertThat(gift.availableAmountFen).isZero();
        assertThat(gift.status).isEqualTo(BalanceLotStatus.EXPIRED);
        assertThat(hold.status).isEqualTo(BalanceHoldStatus.RELEASED);
    }

    @Test
    void fixedCompositionLeavesSingleExternalDifference() {
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        MemberBalanceLot gift = lot(1L, BalanceType.GIFT, 2000, now.plusSeconds(3600));
        MemberBalanceLot paid = lot(2L, BalanceType.PAID, 3000, null);
        when(lotMapper.selectSpendableForUpdate(9L, now)).thenReturn(List.of(gift, paid));
        Booking booking = new Booking();
        booking.id = 10L;
        booking.amountFen = 6000;

        service.freeze(booking, 9L, now);

        assertThat(booking.giftBalanceAmountFen).isEqualTo(2000);
        assertThat(booking.paidBalanceAmountFen).isEqualTo(3000);
        assertThat(booking.externalAmountFen).isEqualTo(1000);
    }

    @Test
    void rejectsRechargeReversalAfterAnyConsumptionHistory() {
        MemberRecharge recharge = recharge(7L, 9L, RechargeChannel.CASH);
        VenueCustomer customer = customer(9L, 1L, 2L);
        when(rechargeMapper.selectById(7L)).thenReturn(recharge);
        when(rechargeMapper.selectForUpdate(7L)).thenReturn(recharge);
        when(customerMapper.selectById(9L)).thenReturn(customer);
        when(flowMapper.countConsumedByRecharge(7L)).thenReturn(1);

        assertThatThrownBy(() -> service.reverseRecharge(1L, 3L, 7L, "录错", true))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.RECHARGE_NOT_REVERSIBLE.getDescription());
        verify(lotMapper, never()).selectByRechargeForUpdate(anyLong());
    }

    @Test
    void rejectsEmployeeReversingOwnRecharge() {
        MemberRecharge recharge = recharge(7L, 9L, RechargeChannel.CASH);
        recharge.operatorEmployeeId = 4L;
        VenueCustomer customer = customer(9L, 1L, 2L);
        when(rechargeMapper.selectById(7L)).thenReturn(recharge);
        when(rechargeMapper.selectForUpdate(7L)).thenReturn(recharge);
        when(customerMapper.selectById(9L)).thenReturn(customer);

        assertThatThrownBy(() -> service.reverseRecharge(1L, null, 4L, 7L, "录错", true))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.RECHARGE_NOT_REVERSIBLE.getDescription());
        verify(lotMapper, never()).selectByRechargeForUpdate(anyLong());
    }

    @Test
    void allowsAuthorizedEmployeeToReverseAnotherEmployeesRecharge() {
        MemberRecharge recharge = recharge(7L, 9L, RechargeChannel.CASH);
        recharge.operatorEmployeeId = 5L;
        VenueCustomer customer = customer(9L, 1L, 2L);
        when(rechargeMapper.selectById(7L)).thenReturn(recharge);
        when(rechargeMapper.selectForUpdate(7L)).thenReturn(recharge);
        when(customerMapper.selectById(9L)).thenReturn(customer);
        when(lotMapper.selectByRechargeForUpdate(7L)).thenReturn(List.of());

        service.reverseRecharge(1L, null, 4L, 7L, "录错", true);

        assertThat(recharge.status).isEqualTo(RechargeStatus.REVERSED);
        verify(lotMapper).selectByRechargeForUpdate(7L);
    }

    @Test
    void retriesSameAdminIdempotencyKeyWithoutCreatingSecondRecharge() {
        VenueCustomer customer = customer(9L, 1L, 2L);
        RechargePlan plan = plan(5L, 1L);
        MemberRecharge pending = recharge(7L, 9L, RechargeChannel.CASH);
        pending.status = RechargeStatus.PENDING;
        when(customerMapper.selectById(9L)).thenReturn(customer);
        when(rechargeMapper.selectByClientKey(9L, "admin-key")).thenReturn(pending);
        when(rechargeMapper.selectForUpdate(7L)).thenReturn(pending);
        AtomicLong lotIds = new AtomicLong(20);
        doAnswer(invocation -> {
            MemberBalanceLot lot = invocation.getArgument(0);
            lot.id = lotIds.incrementAndGet();
            return 1;
        }).when(lotMapper).insert(any(MemberBalanceLot.class));

        service.createAdminRecharge(1L, 3L, 9L, plan.id, RechargeChannel.CASH, "admin-key");

        assertThat(pending.status).isEqualTo(RechargeStatus.SUCCESS);
        verify(rechargeMapper, never()).insert(any(MemberRecharge.class));
    }

    private MemberBalanceLot lot(long id, BalanceType type, int available, Instant expiresAt) {
        MemberBalanceLot lot = new MemberBalanceLot();
        lot.id = id;
        lot.venueCustomerId = 9L;
        lot.type = type;
        lot.originalAmountFen = Math.max(available, 1000);
        lot.availableAmountFen = available;
        lot.frozenAmountFen = 0;
        lot.status = BalanceLotStatus.ACTIVE;
        lot.expiresAt = expiresAt;
        return lot;
    }

    private MemberRecharge recharge(long id, long customerId, RechargeChannel channel) {
        MemberRecharge recharge = new MemberRecharge();
        recharge.id = id;
        recharge.rechargeNo = "RCH-7";
        recharge.venueCustomerId = customerId;
        recharge.planName = "充值";
        recharge.paidAmountFen = 10000;
        recharge.giftAmountFen = 0;
        recharge.channel = channel;
        recharge.status = RechargeStatus.SUCCESS;
        recharge.createdAt = java.time.LocalDateTime.now();
        return recharge;
    }

    private RechargePlan plan(long id, long venueId) {
        RechargePlan plan = new RechargePlan();
        plan.id = id;
        plan.venueId = venueId;
        plan.name = "充值";
        plan.paidAmountFen = 10000;
        plan.giftAmountFen = 0;
        plan.status = RechargePlanStatus.ACTIVE;
        return plan;
    }

    private VenueCustomer customer(long id, long venueId, long userId) {
        VenueCustomer customer = new VenueCustomer();
        customer.id = id;
        customer.venueId = venueId;
        customer.userId = userId;
        customer.status = VenueCustomerStatus.CUSTOMER;
        return customer;
    }
}
