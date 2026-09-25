package com.sportsbooking.admin.server.controller.funds;

import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.biz.pojo.request.AdminRechargeRequest;
import com.sportsbooking.admin.biz.pojo.request.MiniRechargeCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.RechargePlanRequest;
import com.sportsbooking.admin.biz.pojo.request.RechargeReverseRequest;
import com.sportsbooking.admin.biz.pojo.request.StaffRechargeRequest;
import com.sportsbooking.admin.biz.pojo.vo.BalanceAccountVO;
import com.sportsbooking.admin.biz.pojo.vo.MemberRechargeVO;
import com.sportsbooking.admin.biz.pojo.vo.PlatformRefundVO;
import com.sportsbooking.admin.biz.pojo.vo.RechargePlanVO;
import com.sportsbooking.admin.biz.pojo.vo.RefundVO;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.service.PaymentService;
import com.sportsbooking.admin.biz.service.PermissionService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import com.sportsbooking.admin.server.support.Principals;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P2 充值、会员余额和异常退款协议入口。 */
@RestController
@RequestMapping("/")
public class FundsController {
    @Autowired
    private MemberFundsService memberFundsService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PermissionService permissionService;

    @GetMapping("venue-admin/recharge-plans")
    public ApiResponse<List<RechargePlanVO>> listVenuePlans() {
        return ApiResponse.success(memberFundsService.listPlans(
                Principals.venueAny(VenuePermission.MEMBER_FUNDS_VIEW,
                        VenuePermission.RECHARGE_PLAN_EDIT).getVenueId(), true));
    }

    @PostMapping("venue-admin/recharge-plans")
    public ApiResponse<RechargePlanVO> createVenuePlan(@RequestBody RechargePlanRequest body) {
        return ApiResponse.success(memberFundsService.createPlan(
                Principals.venue(VenuePermission.RECHARGE_PLAN_EDIT).getVenueId(), body));
    }

    @PutMapping("venue-admin/recharge-plans/{planId}")
    public ApiResponse<RechargePlanVO> updateVenuePlan(
            @PathVariable long planId, @RequestBody RechargePlanRequest body) {
        return ApiResponse.success(memberFundsService.updatePlan(
                Principals.venue(VenuePermission.RECHARGE_PLAN_EDIT).getVenueId(), planId, body));
    }

    @PostMapping("venue-admin/members/{customerId}/recharges")
    public ApiResponse<MemberRechargeVO> createAdminRecharge(
            @PathVariable long customerId, @RequestBody AdminRechargeRequest body) {
        AdminPrincipal principal = Principals.venue(VenuePermission.MEMBER_RECHARGE);
        return ApiResponse.success(memberFundsService.createAdminRecharge(principal.getVenueId(),
                adminId(principal), principal.getEmployeeId(), customerId, body.getPlanId(),
                body.getChannel(), body.getIdempotencyKey()));
    }

    @GetMapping("venue-admin/members/{customerId}/recharges")
    public ApiResponse<List<MemberRechargeVO>> listAdminRecharges(@PathVariable long customerId) {
        return ApiResponse.success(memberFundsService.listRecharges(
                Principals.venue(VenuePermission.MEMBER_FUNDS_VIEW).getVenueId(), customerId));
    }

    @GetMapping("venue-admin/members/{customerId}/balance-flows")
    public ApiResponse<BalanceAccountVO> getAdminBalanceFlows(@PathVariable long customerId) {
        return ApiResponse.success(memberFundsService.getAccountByVenue(
                Principals.venue(VenuePermission.MEMBER_FUNDS_VIEW).getVenueId(), customerId));
    }

    @PostMapping("venue-admin/recharges/{rechargeId}/reverse")
    public ApiResponse<MemberRechargeVO> reverseRecharge(
            @PathVariable long rechargeId, @RequestBody RechargeReverseRequest body) {
        AdminPrincipal principal = Principals.venue(VenuePermission.MEMBER_RECHARGE_REVERSE);
        return ApiResponse.success(memberFundsService.reverseRecharge(principal.getVenueId(), adminId(principal),
                principal.getEmployeeId(), rechargeId, body.getReason(),
                Boolean.TRUE.equals(body.getOfflineFundsHandled())));
    }

    @PostMapping("staff/recharges")
    public ApiResponse<MemberRechargeVO> createStaffRecharge(@RequestBody StaffRechargeRequest body) {
        com.sportsbooking.admin.base.po.Employee employee = permissionService.requireEmployee(
                AuthContext.getMiniUserId(), body.getVenueId(), VenuePermission.MEMBER_RECHARGE);
        return ApiResponse.success(memberFundsService.createAdminRecharge(body.getVenueId(), null, employee.id,
                body.getCustomerId(), body.getPlanId(), body.getChannel(), body.getClientIdempotencyKey()));
    }

    @GetMapping("mini/recharges/plans")
    public ApiResponse<List<RechargePlanVO>> listMiniPlans(@RequestParam long venueId) {
        return ApiResponse.success(memberFundsService.listPlans(venueId, false));
    }

    @PostMapping("mini/recharges/create")
    public ApiResponse<MemberRechargeVO> createMiniRecharge(@RequestBody MiniRechargeCreateRequest body) {
        return ApiResponse.success(memberFundsService.createSelfRecharge(AuthContext.getMiniUserId(),
                body.getVenueId(), body.getPlanId(), body.getIdempotencyKey()));
    }

    @GetMapping("mini/recharges/{rechargeId}")
    public ApiResponse<MemberRechargeVO> getMiniRecharge(@PathVariable long rechargeId) {
        return ApiResponse.success(memberFundsService.getRecharge(AuthContext.getMiniUserId(), rechargeId));
    }

    @PostMapping("mini/recharges/{rechargeId}/mock-pay")
    public ApiResponse<MemberRechargeVO> mockPayRecharge(@PathVariable long rechargeId) {
        return ApiResponse.success(memberFundsService.confirmMockRecharge(AuthContext.getMiniUserId(), rechargeId));
    }

    @GetMapping("mini/memberships/{venueId}/balance-flows")
    public ApiResponse<BalanceAccountVO> getMiniBalanceFlows(@PathVariable long venueId) {
        return ApiResponse.success(memberFundsService.getAccountByUser(AuthContext.getMiniUserId(), venueId));
    }

    @GetMapping("platform/refunds/list")
    public ApiResponse<List<PlatformRefundVO>> listPlatformRefunds() {
        Principals.platform();
        return ApiResponse.success(paymentService.listPlatformRefunds());
    }

    @PostMapping("platform/refunds/{refundId}/retry")
    public ApiResponse<RefundVO> retryPlatformRefund(@PathVariable long refundId) {
        Principals.platform();
        return ApiResponse.success(paymentService.retryRefund(refundId));
    }

    private Long adminId(AdminPrincipal principal) {
        return principal.getRole() == AdminRole.VENUE_ADMIN ? principal.getAccountId() : null;
    }
}
