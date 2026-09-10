package com.sportsbooking.admin.server.controller.booking;

import com.sportsbooking.admin.biz.pojo.vo.OperationReportVO;
import com.sportsbooking.admin.biz.pojo.vo.ServiceFeeSettlementVO;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.support.Principals;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
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
    public ApiResponse<OperationReportVO> getPlatformReport(
            @RequestParam(required = false) Long venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Principals.platform();
        return ApiResponse.success(bookingService.getOperationReport(venueId, startDate, endDate));
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
    public ApiResponse<OperationReportVO> getVenueReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(bookingService.getOperationReport(
                Principals.venue().getVenueId(), startDate, endDate));
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
                Principals.venue().getVenueId(), billingMonth));
    }
}
