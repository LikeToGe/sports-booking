package com.sportsbooking.admin.server.controller.booking;

import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.biz.pojo.vo.ReportBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportMemberFlowVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportOperationVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportReceiptVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportUtilizationVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportVenueMemberOperationVO;
import com.sportsbooking.admin.biz.pojo.vo.ServiceFeeSettlementVO;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.biz.service.ReportService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.support.Principals;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 功能描述: 平台与场馆经营报表协议入口。
 * 创建时间: 2026/09/03 10:35
 *
 * @author gjm
 */
@RestController
public class OperationReportController {
    @Autowired
    private BookingService bookingService;
    @Autowired
    private ReportService reportService;

    /**
     * 功能描述: 查询平台经营总报表，可筛选单个场馆。
     * 创建时间: 2026/09/03 10:35
     *
     * @param venueId 场馆 ID，为空时查询全平台
     * @param startDate 开始日期，包含
     * @param endDate 结束日期，包含
     * @return 平台经营报表
     * @author gjm
     */
    @GetMapping("/platform/reports/operations")
    public ApiResponse<ReportOperationVO> getPlatformReport(
            @RequestParam(required = false) Long venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Principals.platform();
        return ApiResponse.success(reportService.getOperationReport(venueId, startDate, endDate));
    }

    /** 查询平台场馆级会员经营汇总，不返回个人资料或逐笔流水。 */
    @GetMapping("/platform/reports/member-operations")
    public ApiResponse<List<ReportVenueMemberOperationVO>> getPlatformMemberOperations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Principals.platform();
        return ApiResponse.success(reportService.listVenueMemberOperations(startDate, endDate));
    }

    /** 查询当前场馆今日截止的 7 或 30 天工作台经营数据。 */
    @GetMapping("/venue-admin/reports/dashboard")
    public ApiResponse<ReportOperationVO> getVenueDashboard(
            @RequestParam(defaultValue = "7") int days) {
        AdminPrincipal principal = Principals.venue(VenuePermission.DASHBOARD_VIEW);
        return ApiResponse.success(reportService.getDashboard(principal.getVenueId(), days));
    }

    /**
     * 功能描述: 查询当前登录场馆的经营报表。
     * 创建时间: 2026/09/03 10:35
     *
     * @param startDate 开始日期，包含
     * @param endDate 结束日期，包含
     * @return 当前场馆经营报表
     * @author gjm
     */
    @GetMapping("/venue-admin/reports/operations")
    public ApiResponse<ReportOperationVO> getVenueReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdminPrincipal principal = Principals.venue(VenuePermission.REPORT_VIEW);
        return ApiResponse.success(reportService.getOperationReport(principal.getVenueId(), startDate, endDate));
    }

    /** 查询平台分时段利用率、场地对比和低利用下钻。 */
    @GetMapping("/platform/reports/utilization")
    public ApiResponse<ReportUtilizationVO> getPlatformUtilization(
            @RequestParam(required = false) Long venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long courtId,
            @RequestParam(required = false) Long sportTypeId) {
        Principals.platform();
        return ApiResponse.success(reportService.getUtilizationReport(
                venueId, startDate, endDate, courtId, sportTypeId, false));
    }

    /** 查询当前场馆分时段利用率、场地对比和低利用下钻。 */
    @GetMapping("/venue-admin/reports/utilization")
    public ApiResponse<ReportUtilizationVO> getVenueUtilization(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long courtId,
            @RequestParam(required = false) Long sportTypeId) {
        AdminPrincipal principal = Principals.venue(VenuePermission.REPORT_VIEW);
        return ApiResponse.success(reportService.getUtilizationReport(
                principal.getVenueId(), startDate, endDate, courtId, sportTypeId, true));
    }

    /** 查询当前场馆预约经营下钻。 */
    @GetMapping("/venue-admin/reports/bookings")
    public ApiResponse<List<ReportBookingVO>> listBookings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdminPrincipal principal = Principals.venue(VenuePermission.REPORT_VIEW);
        return ApiResponse.success(reportService.listBookings(principal.getVenueId(), startDate, endDate,
                canViewCustomer(principal)));
    }

    /** 按场地保留低利用下钻条件查询预约。 */
    @GetMapping("/venue-admin/reports/bookings-filtered")
    public ApiResponse<List<ReportBookingVO>> listFilteredBookings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam Long courtId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime slotStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime slotEnd,
            @RequestParam(required = false) Long sportTypeId) {
        AdminPrincipal principal = Principals.venue(VenuePermission.REPORT_VIEW);
        return ApiResponse.success(reportService.listBookings(principal.getVenueId(), startDate, endDate, courtId,
                sportTypeId, slotStart, slotEnd, canViewCustomer(principal)));
    }

    /** 查询当前场馆跨会员余额流水。 */
    @GetMapping("/venue-admin/reports/member-flows")
    public ApiResponse<List<ReportMemberFlowVO>> listMemberFlows(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdminPrincipal principal = Principals.venue(VenuePermission.REPORT_VIEW);
        return ApiResponse.success(reportService.listMemberFlows(principal.getVenueId(), startDate, endDate));
    }

    /** 查询当前场馆每日真实收款。 */
    @GetMapping("/venue-admin/reports/receipts")
    public ApiResponse<List<ReportReceiptVO>> listReceipts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdminPrincipal principal = Principals.venue(VenuePermission.REPORT_VIEW);
        return ApiResponse.success(reportService.listReceipts(principal.getVenueId(), startDate, endDate));
    }

    /** 按当前筛选和敏感字段权限导出预约经营明细。 */
    @GetMapping("/venue-admin/exports/bookings")
    public ResponseEntity<byte[]> exportBookings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdminPrincipal principal = Principals.venue(VenuePermission.REPORT_EXPORT);
        byte[] content = reportService.exportBookings(principal.getVenueId(), startDate, endDate,
                canViewCustomer(principal));
        return xlsx("bookings.xlsx", content);
    }

    /** 按当前筛选导出跨会员余额流水。 */
    @GetMapping("/venue-admin/exports/member-flows")
    public ResponseEntity<byte[]> exportMemberFlows(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdminPrincipal principal = Principals.venue(VenuePermission.REPORT_EXPORT);
        return xlsx("member-flows.xlsx",
                reportService.exportMemberFlows(principal.getVenueId(), startDate, endDate));
    }

    /** 按当前筛选导出每日真实收款。 */
    @GetMapping("/venue-admin/exports/receipts")
    public ResponseEntity<byte[]> exportReceipts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdminPrincipal principal = Principals.venue(VenuePermission.REPORT_EXPORT);
        return xlsx("receipts.xlsx", reportService.exportReceipts(principal.getVenueId(), startDate, endDate));
    }

    /**
     * 功能描述: 查询平台月度服务费，可筛选单个场馆。
     * 创建时间: 2026/09/08
     *
     * @param venueId 场馆 ID，为空时查询全平台
     * @param billingMonth 自然月账期
     * @return 平台月度服务费
     * @author gjm
     */
    @GetMapping("/platform/reports/service-fees")
    public ApiResponse<ServiceFeeSettlementVO> getPlatformServiceFees(
            @RequestParam(required = false) Long venueId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth billingMonth) {
        Principals.platform();
        return ApiResponse.success(bookingService.getServiceFeeSettlement(venueId, billingMonth));
    }

    /**
     * 功能描述: 查询当前场馆月度服务费。
     * 创建时间: 2026/09/08
     *
     * @param billingMonth 自然月账期
     * @return 当前场馆月度服务费
     * @author gjm
     */
    @GetMapping("/venue-admin/reports/service-fees")
    public ApiResponse<ServiceFeeSettlementVO> getVenueServiceFees(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth billingMonth) {
        return ApiResponse.success(bookingService.getServiceFeeSettlement(
                Principals.venue(VenuePermission.REPORT_VIEW).getVenueId(), billingMonth));
    }

    /** 普通员工仅在拥有顾客查看权限时看到完整联系人信息。 */
    private boolean canViewCustomer(AdminPrincipal principal) {
        return principal.getRole() == AdminRole.VENUE_ADMIN
                || principal.getPermissions().contains(VenuePermission.CUSTOMER_VIEW);
    }

    /** 构造标准 xlsx 下载响应。 */
    private ResponseEntity<byte[]> xlsx(String filename, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(content.length);
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
