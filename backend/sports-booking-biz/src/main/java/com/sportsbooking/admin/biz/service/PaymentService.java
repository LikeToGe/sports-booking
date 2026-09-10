package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.po.Refund;

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

    /**
     * 功能描述: 已支付预约取消时写入全额退款并记录通知。
     * 创建时间: 2026/08/28 16:29
     *
     * @param booking 已取消预约
     * @param reason 退款原因
     * @author gjm
     */
    void refundAndNotify(Booking booking, String reason);

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
