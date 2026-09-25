package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.Payment;

/**
 * 功能描述: 支付记录数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface PaymentMapper extends BaseMapper<Payment> {
    Payment selectByBookingForUpdate(long bookingId);
    Payment selectByBusinessNo(String businessNo);
}
