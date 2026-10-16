package com.sportsbooking.admin.funds;

import com.sportsbooking.admin.base.enums.*;
import com.sportsbooking.admin.base.po.MemberRecharge;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.biz.service.impl.MockPaymentChannelAdapter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Mock 的明确关闭操作与未知查单结果不能混用。 */
class MockPaymentChannelAdapterTest {
    private final MockPaymentChannelAdapter adapter = new MockPaymentChannelAdapter();

    @Test
    void pendingMockPaymentCanCloseButQueryRemainsUnknown() {
        Payment payment = new Payment();
        payment.status = PaymentStatus.PENDING;
        assertThat(adapter.queryPayment(payment)).isEqualTo(PaymentResult.UNKNOWN);
        assertThat(adapter.closePayment(payment)).isEqualTo(PaymentResult.FAILED);
        payment.status = PaymentStatus.SUCCESS;
        assertThat(adapter.closePayment(payment)).isEqualTo(PaymentResult.SUCCESS);
        payment.status = null;
        assertThat(adapter.closePayment(payment)).isEqualTo(PaymentResult.UNKNOWN);
    }

    @Test
    void pendingMockRechargeCanCloseWhileUnknownAndOfflineStatesCannot() {
        MemberRecharge recharge = new MemberRecharge();
        recharge.status = RechargeStatus.PENDING;
        recharge.channel = RechargeChannel.MOCK;
        assertThat(adapter.queryRecharge(recharge)).isEqualTo(PaymentResult.UNKNOWN);
        assertThat(adapter.closeRecharge(recharge)).isEqualTo(PaymentResult.FAILED);
        recharge.status = RechargeStatus.SUCCESS;
        assertThat(adapter.closeRecharge(recharge)).isEqualTo(PaymentResult.SUCCESS);
        recharge.status = null;
        assertThat(adapter.closeRecharge(recharge)).isEqualTo(PaymentResult.UNKNOWN);
        recharge.status = RechargeStatus.PENDING;
        recharge.channel = RechargeChannel.CASH;
        assertThat(adapter.closeRecharge(recharge)).isEqualTo(PaymentResult.UNKNOWN);
    }
}
