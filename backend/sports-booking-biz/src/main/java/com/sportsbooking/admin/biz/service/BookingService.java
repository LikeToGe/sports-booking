package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.biz.pojo.vo.BookingDetailVO;
import com.sportsbooking.admin.biz.pojo.vo.UserBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.OperationReportVO;
import com.sportsbooking.admin.biz.pojo.vo.ServiceFeeSettlementVO;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 功能描述: 预约、模拟支付退款和核销状态流的业务接口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface BookingService {
    /**
     * 功能描述: 创建线上待支付预约并锁定具体场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param userId 当前用户 ID
     * @param sessionSlotId 具体场次 ID
     * @param contactName 联系人
     * @param contactPhone 联系电话
     * @return 待支付预约
     * @author gjm
     */
    Booking createOnline(long userId, long sessionSlotId, String contactName, String contactPhone);

    /** 使用客户端幂等键创建线上预约。 */
    Booking createOnline(long userId, long sessionSlotId, String contactName, String contactPhone,
                         String idempotencyKey, Integer expectedPriceFen, String entryType);
    /**
     * 功能描述: 场馆按明确登记收款状态创建线下预约。
     *
     * @param venueId 场馆 ID
     * @param sessionSlotId 具体场次 ID
     * @param contactName 联系人
     * @param contactPhone 联系电话
     * @param paymentStatus 登记收款状态
     * @param venueCustomerId 可选的本场馆顾客档案 ID
     * @return 已确认预约
     * @author gjm
     */
    Booking createOffline(long venueId, long sessionSlotId, String contactName, String contactPhone,
                          BookingPaymentStatus paymentStatus, Long venueCustomerId);

    /**
     * 功能描述: 按模板场次为多个日期整批创建线下预约。
     *
     * @param venueId 场馆 ID
     * @param templateSessionSlotId 模板具体场次 ID
     * @param dates 目标日期列表
     * @param contactName 联系人
     * @param contactPhone 联系电话
     * @param paymentStatus 登记收款状态
     * @param venueCustomerId 可选的本场馆顾客档案 ID
     * @return 去重日期对应的预约
     * @author gjm
     */
    List<Booking> createOfflineBatch(long venueId, long templateSessionSlotId, List<LocalDate> dates,
                                     String contactName, String contactPhone,
                                     BookingPaymentStatus paymentStatus, Long venueCustomerId);

    /**
     * 功能描述: 修改线下预约登记收款状态并记录可信操作者和原因。
     *
     * @param venueId 场馆 ID
     * @param operatorAdminId 当前后台账号 ID
     * @param bookingId 预约 ID
     * @param paymentStatus 目标登记收款状态
     * @param reason 修改原因
     * @return 更新后的预约
     * @author gjm
     */
    default Booking updateOfflinePaymentStatus(long venueId, long operatorAdminId, long bookingId,
                                               BookingPaymentStatus paymentStatus, String reason) {
        return updateOfflinePaymentStatus(venueId, operatorAdminId, null, bookingId, paymentStatus, reason);
    }
    Booking updateOfflinePaymentStatus(long venueId, Long operatorAdminId, Long operatorEmployeeId, long bookingId,
                                       BookingPaymentStatus paymentStatus, String reason);

    /**
     * 功能描述: 将未开始的线下预约改到本场馆同价开放场次并记录审计。
     *
     * @param venueId 场馆 ID
     * @param operatorAdminId 当前后台账号 ID
     * @param bookingId 预约 ID
     * @param sessionSlotId 目标具体场次 ID
     * @param reason 改期原因
     * @return 改期后的预约
     * @author gjm
     */
    default Booking rescheduleOffline(long venueId, long operatorAdminId, long bookingId,
                                      long sessionSlotId, String reason) {
        return rescheduleOffline(venueId, operatorAdminId, null, bookingId, sessionSlotId, reason);
    }
    Booking rescheduleOffline(long venueId, Long operatorAdminId, Long operatorEmployeeId, long bookingId,
                              long sessionSlotId, String reason);

    /**
     * 功能描述: 修改任意状态线下预约的联系人资料。
     * 创建时间: 2026/09/11
     *
     * @param venueId 场馆 ID
     * @param bookingId 预约 ID
     * @param contactName 联系人
     * @param contactPhone 联系电话
     * @return 更新后的预约
     * @author gjm
     */
    Booking updateOfflineContact(long venueId, long bookingId, String contactName, String contactPhone);
    /**
     * 功能描述: 用户在取消截止时间前取消预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param userId 当前用户 ID
     * @param bookingId 预约 ID
     * @return 已取消预约
     * @author gjm
     */
    Booking cancelByUser(long userId, long bookingId);
    /**
     * 功能描述: 场馆填写原因后主动取消预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param bookingId 预约 ID
     * @param reason 取消原因
     * @return 已取消预约
     * @author gjm
     */
    default Booking cancelByVenue(long venueId, long bookingId, String reason) {
        return cancelByVenue(venueId, bookingId, reason, true, true);
    }
    /** 按预约来源和当前实时权限取消场馆预约。 */
    Booking cancelByVenue(long venueId, long bookingId, String reason,
                          boolean canCancelOnline, boolean canEditOffline);
    /**
     * 功能描述: 查询当前用户全部预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param userId 当前用户 ID
     * @return 用户预约列表
     * @author gjm
     */
    List<UserBookingVO> listUserBookings(long userId);
    /**
     * 功能描述: 查询当前用户单笔预约详情和核销凭证。
     * 创建时间: 2026/08/28 16:29
     *
     * @param userId 当前用户 ID
     * @param bookingId 预约 ID
     * @return 预约详情
     * @author gjm
     */
    BookingDetailVO getUserBooking(long userId, long bookingId);

    /** 返回本人已完成线上预约的评价资格事实，不暴露给客户端。 */
    Booking requireReviewEligibleBooking(long userId, long bookingId);
    /**
     * 功能描述: 查询场馆指定日期预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param date 日期，为空时查询全部
     * @return 场馆预约列表
     * @author gjm
     */
    List<VenueBookingVO> listVenueBookings(long venueId, LocalDate date);

    /**
     * 功能描述: 查询全平台或单个场馆经营报表。
     * 创建时间: 2026/09/03 10:35
     *
     * @param venueId 场馆 ID，为空时查询全平台
     * @param startDate 开始日期，包含
     * @param endDate 结束日期，包含
     * @return 经营报表
     * @author gjm
     */
    OperationReportVO getOperationReport(Long venueId, LocalDate startDate, LocalDate endDate);

    /**
     * 功能描述: 查询全平台或单个场馆的自然月平台服务费。
     * 创建时间: 2026/09/08
     *
     * @param venueId 场馆 ID，为空时查询全平台
     * @param billingMonth 自然月账期
     * @return 只读月度结算数据
     * @author gjm
     */
    ServiceFeeSettlementVO getServiceFeeSettlement(Long venueId, YearMonth billingMonth);
}
