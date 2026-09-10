package com.sportsbooking.admin.biz.service;

/**
 * 功能描述: 支付超时释放与未到场结算业务接口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface BookingSettlementService {

    /**
     * 功能描述: 释放超时占位并标记已结束未核销预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    void settleTimedBookings();
}
