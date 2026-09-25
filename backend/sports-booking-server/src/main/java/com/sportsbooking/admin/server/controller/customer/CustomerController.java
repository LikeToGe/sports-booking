package com.sportsbooking.admin.server.controller.customer;

import com.sportsbooking.admin.biz.pojo.vo.MembershipVO;
import com.sportsbooking.admin.biz.pojo.vo.PlatformCustomerSummaryVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueCustomerVO;
import com.sportsbooking.admin.biz.service.VenueCustomerService;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.biz.service.PermissionService;
import com.sportsbooking.admin.biz.service.MemberFundsService;
import com.sportsbooking.admin.biz.pojo.vo.BalanceAccountVO;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import com.sportsbooking.admin.server.support.Principals;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 功能描述: 场馆顾客、用户会员关系和平台汇总协议入口。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@RestController
@RequestMapping("/")
public class CustomerController {
    @Autowired
    private VenueCustomerService venueCustomerService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private MemberFundsService memberFundsService;

    /**
     * 功能描述: 查询当前登录场馆的顾客列表。
     *
     * @param keyword 姓名关键字、完整手机号或会员码
     * @return 当前场馆顾客列表
     * @author gjm
     */
    @GetMapping("venue-admin/customers/list")
    public ApiResponse<List<VenueCustomerVO>> listVenueCustomers(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(venueCustomerService.listVenueCustomers(
                Principals.venue(VenuePermission.CUSTOMER_VIEW).getVenueId(), keyword));
    }

    /**
     * 功能描述: 查询当前登录场馆的单个顾客详情。
     *
     * @param customerId 顾客档案 ID
     * @return 顾客详情
     * @author gjm
     */
    @GetMapping("venue-admin/customers/{customerId}")
    public ApiResponse<VenueCustomerVO> getVenueCustomer(@PathVariable long customerId) {
        return ApiResponse.success(venueCustomerService.getVenueCustomer(
                Principals.venue(VenuePermission.CUSTOMER_VIEW).getVenueId(), customerId));
    }

    /**
     * 功能描述: 通过会员码或完整手机号识别当前登录场馆的顾客。
     *
     * @param memberCode 可选会员码，与手机号至少提供一项
     * @param phone 可选完整手机号，与会员码至少提供一项
     * @return 识别出的顾客
     * @author gjm
     */
    @GetMapping("venue-admin/customers/identify")
    public ApiResponse<VenueCustomerVO> identifyVenueCustomer(
            @RequestParam(required = false) String memberCode,
            @RequestParam(required = false) String phone) {
        return ApiResponse.success(venueCustomerService.identifyVenueCustomer(
                Principals.venue(VenuePermission.CUSTOMER_VIEW).getVenueId(), memberCode, phone));
    }

    /**
     * 功能描述: 已绑定员工通过会员码或完整手机号识别本场馆顾客。
     *
     * @param memberCode 可选会员码，与手机号至少提供一项
     * @param phone 可选完整手机号，与会员码至少提供一项
     * @return 识别出的本场馆顾客
     * @author gjm
     */
    @GetMapping("staff/customers/identify")
    public ApiResponse<VenueCustomerVO> identifyStaffVenueCustomer(
            @RequestParam long venueId,
            @RequestParam(required = false) String memberCode,
            @RequestParam(required = false) String phone) {
        Employee employee = permissionService.requireEmployee(
                AuthContext.getMiniUserId(), venueId, VenuePermission.CUSTOMER_VIEW);
        return ApiResponse.success(venueCustomerService.identifyVenueCustomer(
                employee.venueId, memberCode, phone));
    }

    @GetMapping("staff/members")
    public ApiResponse<VenueCustomerVO> identifyStaffMember(
            @RequestParam long venueId,
            @RequestParam(required = false) String memberCode,
            @RequestParam(required = false) String phone) {
        permissionService.requireEmployee(AuthContext.getMiniUserId(), venueId, VenuePermission.CUSTOMER_VIEW);
        return ApiResponse.success(venueCustomerService.identifyVenueCustomer(venueId, memberCode, phone));
    }

    @GetMapping("staff/members/{customerId}")
    public ApiResponse<VenueCustomerVO> getStaffMember(
            @PathVariable long customerId, @RequestParam long venueId) {
        permissionService.requireEmployee(AuthContext.getMiniUserId(), venueId, VenuePermission.MEMBER_FUNDS_VIEW);
        return ApiResponse.success(venueCustomerService.getVenueCustomer(venueId, customerId));
    }

    @GetMapping("staff/members/{customerId}/balance-flows")
    public ApiResponse<BalanceAccountVO> getStaffBalanceFlows(
            @PathVariable long customerId, @RequestParam long venueId) {
        permissionService.requireEmployee(AuthContext.getMiniUserId(), venueId, VenuePermission.MEMBER_FUNDS_VIEW);
        return ApiResponse.success(memberFundsService.getAccountByVenue(venueId, customerId));
    }

    /**
     * 功能描述: 查询当前小程序用户已有的场馆关系。
     *
     * @return 当前用户场馆关系列表
     * @author gjm
     */
    @GetMapping("mini/memberships/list")
    public ApiResponse<List<MembershipVO>> listMemberships() {
        return ApiResponse.success(venueCustomerService.listMemberships(AuthContext.getMiniUserId()));
    }

    /**
     * 功能描述: 查询当前小程序用户在指定场馆的关系及会员码二维码。
     *
     * @param venueId 场馆 ID
     * @return 当前用户的场馆关系和二维码
     * @author gjm
     */
    @GetMapping("mini/memberships/{venueId}")
    public ApiResponse<MembershipVO> getMembership(@PathVariable long venueId) {
        return ApiResponse.success(venueCustomerService.getMembership(AuthContext.getMiniUserId(), venueId));
    }

    /**
     * 功能描述: 查询平台场馆级顾客会员数量汇总。
     *
     * @return 各场馆数量汇总，不包含个人字段
     * @author gjm
     */
    @GetMapping("platform/customer-summaries")
    public ApiResponse<List<PlatformCustomerSummaryVO>> listPlatformSummaries() {
        Principals.platform();
        return ApiResponse.success(venueCustomerService.listPlatformSummaries());
    }
}
