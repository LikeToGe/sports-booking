package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.po.Refund;
import com.sportsbooking.admin.base.enums.PaymentResult;
import com.sportsbooking.admin.base.enums.CancelledBy;
import com.sportsbooking.admin.biz.pojo.vo.PlatformRefundVO;
import com.sportsbooking.admin.biz.pojo.vo.RefundVO;
import java.time.Instant;
import java.util.List;

/**
 * 功能描述: 模拟支付、退款和支付记录查询业务接口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface PaymentService {

    /**
     * 功能描述: 为线上预约创建待支付记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @param booking 待支付预约
     * @return 支付记录
     * @author gjm
     */
    Payment createPending(Booking booking);

    /** 在事务和 Redis 锁外发起已有支付单。 */
    Booking startPayment(long userId, long bookingId);

    /**
     * 功能描述: 模拟支付成功并原子确认预约与场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param userId 当前用户 ID
     * @param bookingId 预约 ID
     * @return 已确认预约
     * @author gjm
     */
    Booking mockPay(long userId, long bookingId);

    /** 查询并收敛预约支付结果。 */
    Booking queryPayment(long userId, long bookingId);

    /** 供场馆取消和超时任务在外部查单后收敛。 */
    Booking queryPayment(long bookingId);

    /** 主动取消先在事务外请求渠道关闭未付订单；结果未知不释放占位或余额。 */
    default Booking closeUnpaid(long userId, long bookingId) {
        return closeUnpaid(userId, bookingId, CancelledBy.USER, "用户取消待支付预约");
    }

    /** 场馆闭单保留可信操作者来源和必填原因，与用户取消共用支付资源锁。 */
    Booking closeUnpaid(long userId, long bookingId, CancelledBy cancelledBy, String reason);

    /** 受控 Mock 渠道结果入口，供专项测试覆盖失败和未知。 */
    Booking handleMockPaymentResult(long userId, long bookingId, PaymentResult result);

    /** 按支付业务号校验金额并处理 Mock 渠道通知。 */
    Booking handleMockPaymentNotification(String businessNo, int amountFen, PaymentResult result);

    /** 在当前事务中关闭待支付记录。 */
    void closePending(long bookingId, Instant now);

    /**
     * 功能描述: 已支付预约取消时写入全额退款并记录通知。
     * 创建时间: 2026/08/28 16:29
     *
     * @param booking 已取消预约
     * @param reason 退款原因
     * @author gjm
     */
    void refundAndNotify(Booking booking, String reason);

    /** 在事务外调用 Mock 渠道并收敛退款。 */
    Refund processRefund(long bookingId);

    /** 平台查询异常退款。 */
    List<PlatformRefundVO> listPlatformRefunds();

    /** 使用原退款业务号人工幂等重试。 */
    RefundVO retryRefund(long refundId);

    /** 自动重试到期的异常退款。 */
    void retryDueRefunds();

    /**
     * 功能描述: 查询预约支付记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @param bookingId 预约 ID
     * @return 支付记录，不存在时返回空
     * @author gjm
     */
    Payment findPayment(long bookingId);

    /**
     * 功能描述: 查询预约退款记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @param bookingId 预约 ID
     * @return 退款记录，不存在时返回空
     * @author gjm
     */
    Refund findRefund(long bookingId);
}
