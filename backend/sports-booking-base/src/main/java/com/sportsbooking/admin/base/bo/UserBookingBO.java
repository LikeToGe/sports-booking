package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.base.enums.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 小程序“我的预约”列表项。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBookingBO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 预约编号
     */
    private String bookingNo;

    /**
     * 场馆名称
     */
    private String venueName;

    /**
     * 场地名称
     */
    private String courtName;

    /**
     * 运动名称
     */
    private String sportName;

    /**
     * 开始时间
     */
    private String startAt;

    /**
     * 结束时间
     */
    private String endAt;

    /**
     * 金额，单位为分
     */
    private Integer amountFen;

    /**
     * 状态
     */
    private BookingStatus status;

    /**
     * 支付过期时间
     */
    private String paymentExpiresAt;

    /**
     * 支付状态
     */
    private PaymentStatus paymentStatus;

    /**
     * 退款状态
     */
    private RefundStatus refundStatus;
}
