package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.RechargeChannel;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.biz.pojo.request.RechargePlanRequest;
import com.sportsbooking.admin.biz.pojo.vo.BalanceAccountVO;
import com.sportsbooking.admin.biz.pojo.vo.MemberRechargeVO;
import com.sportsbooking.admin.biz.pojo.vo.PaymentCompositionVO;
import com.sportsbooking.admin.biz.pojo.vo.RechargePlanVO;
import java.time.Instant;
import java.util.List;

/** 充值、余额批次、冻结和不可变流水的统一资金服务。 */
public interface MemberFundsService {
    List<RechargePlanVO> listPlans(long venueId, boolean includeDisabled);
    RechargePlanVO createPlan(long venueId, RechargePlanRequest request);
    RechargePlanVO updatePlan(long venueId, long planId, RechargePlanRequest request);
    MemberRechargeVO createSelfRecharge(long userId, long venueId, long planId, String idempotencyKey);
    MemberRechargeVO confirmMockRecharge(long userId, long rechargeId);
    MemberRechargeVO handleMockRechargeNotification(String businessNo, int amountFen,
                                                     com.sportsbooking.admin.base.enums.PaymentResult result);
    default MemberRechargeVO createAdminRecharge(long venueId, long operatorAdminId, long customerId,
                                                 long planId, RechargeChannel channel, String idempotencyKey) {
        return createAdminRecharge(venueId, operatorAdminId, null, customerId, planId, channel, idempotencyKey);
    }
    MemberRechargeVO createAdminRecharge(long venueId, Long operatorAdminId, Long operatorEmployeeId,
                                         long customerId, long planId, RechargeChannel channel, String idempotencyKey);
    MemberRechargeVO getRecharge(long userId, long rechargeId);
    List<MemberRechargeVO> listRecharges(long venueId, long customerId);
    default MemberRechargeVO reverseRecharge(long venueId, long operatorAdminId, long rechargeId,
                                             String reason, boolean offlineFundsHandled) {
        return reverseRecharge(venueId, operatorAdminId, null, rechargeId, reason, offlineFundsHandled);
    }
    MemberRechargeVO reverseRecharge(long venueId, Long operatorAdminId, Long operatorEmployeeId, long rechargeId,
                                     String reason, boolean offlineFundsHandled);
    BalanceAccountVO getAccountByUser(long userId, long venueId);
    BalanceAccountVO getAccountByVenue(long venueId, long customerId);
    void freeze(Booking booking, long venueCustomerId, Instant now);
    void consume(long bookingId, Instant now);
    void release(long bookingId, Instant now);
    void refund(long bookingId, Instant now);
    PaymentCompositionVO getComposition(Booking booking);
    void settleGiftLots();
}
