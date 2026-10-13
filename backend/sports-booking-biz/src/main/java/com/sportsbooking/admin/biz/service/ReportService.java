package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.ReportBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportMemberFlowVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportUtilizationVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportOperationVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportReceiptVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportVenueMemberOperationVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 功能描述: 经营报表、下钻和 Excel 导出业务入口。
 *
 * @author gjm
 */
public interface ReportService {
    /** 查询当前业务日期截止的 7 或 30 天工作台经营汇总。 */
    ReportOperationVO getDashboard(long venueId, int days);

    /** 查询全平台或单场馆经营汇总。 */
    ReportOperationVO getOperationReport(Long venueId, LocalDate startDate, LocalDate endDate);

    /** 查询分时段利用率、场地对比及低利用时段。 */
    ReportUtilizationVO getUtilizationReport(Long venueId, LocalDate startDate, LocalDate endDate,
                                              Long courtId, Long sportTypeId, boolean includeCourtDetails);

    /** 查询平台场馆级会员经营汇总。 */
    List<ReportVenueMemberOperationVO> listVenueMemberOperations(LocalDate startDate, LocalDate endDate);

    /** 查询场馆预约下钻，并按权限处理联系人字段。 */
    List<ReportBookingVO> listBookings(long venueId, LocalDate startDate, LocalDate endDate,
                                       boolean includeSensitive);

    /** 查询场馆预约下钻，保留运动与完整日期时间范围并沿用敏感字段权限。 */
    List<ReportBookingVO> listBookings(long venueId, LocalDate startDate, LocalDate endDate,
            Long courtId, Long sportTypeId, LocalDateTime slotStart, LocalDateTime slotEnd, boolean includeSensitive);

    /** 查询场馆跨会员余额流水。 */
    List<ReportMemberFlowVO> listMemberFlows(long venueId, LocalDate startDate, LocalDate endDate);

    /** 查询场馆每日真实收款。 */
    List<ReportReceiptVO> listReceipts(long venueId, LocalDate startDate, LocalDate endDate);

    /** 导出预约下钻。 */
    byte[] exportBookings(long venueId, LocalDate startDate, LocalDate endDate, boolean includeSensitive);

    /** 导出跨会员余额流水。 */
    byte[] exportMemberFlows(long venueId, LocalDate startDate, LocalDate endDate);

    /** 导出每日真实收款。 */
    byte[] exportReceipts(long venueId, LocalDate startDate, LocalDate endDate);
}
