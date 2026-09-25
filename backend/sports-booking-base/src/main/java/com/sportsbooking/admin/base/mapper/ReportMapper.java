package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.bo.ReportBookingBO;
import com.sportsbooking.admin.base.bo.ReportMemberFlowBO;
import com.sportsbooking.admin.base.bo.ReportOperationDailyBO;
import com.sportsbooking.admin.base.bo.ReportOperationExtraBO;
import com.sportsbooking.admin.base.bo.ReportReceiptBO;
import com.sportsbooking.admin.base.bo.ReportVenueMemberOperationBO;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 功能描述: 经营汇总、下钻与导出共用查询。
 *
 * @author gjm
 */
@Mapper
public interface ReportMapper {
    /** 按业务日期聚合经营事件。 */
    List<ReportOperationDailyBO> selectOperationDaily(@Param("venueId") Long venueId,
                                                      @Param("startAt") Instant startAt,
                                                      @Param("endAt") Instant endAt);

    /** 查询区间复购和当前余额。 */
    ReportOperationExtraBO selectOperationExtra(@Param("venueId") Long venueId,
                                                 @Param("startAt") Instant startAt,
                                                 @Param("endAt") Instant endAt);

    /** 查询平台场馆级会员经营汇总。 */
    List<ReportVenueMemberOperationBO> selectVenueMemberOperations(@Param("startAt") Instant startAt,
                                                                   @Param("endAt") Instant endAt);

    /** 查询场馆预约下钻。 */
    List<ReportBookingBO> selectBookings(@Param("venueId") long venueId,
                                         @Param("startAt") Instant startAt,
                                         @Param("endAt") Instant endAt);

    /** 查询场馆跨会员余额流水。 */
    List<ReportMemberFlowBO> selectMemberFlows(@Param("venueId") long venueId,
                                               @Param("startAt") Instant startAt,
                                               @Param("endAt") Instant endAt);

    /** 查询场馆真实收款聚合。 */
    List<ReportReceiptBO> selectReceipts(@Param("venueId") long venueId,
                                         @Param("startAt") Instant startAt,
                                         @Param("endAt") Instant endAt);
}
