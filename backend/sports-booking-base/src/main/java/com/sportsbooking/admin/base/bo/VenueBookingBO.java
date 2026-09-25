package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 场馆后台预约列表项。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueBookingBO {

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
     * 预约来源
     */
    private BookingSource source;

    /**
     * 联系人姓名
     */
    private String contactName;

    /**
     * 联系人手机号
     */
    private String contactPhone;

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
     * 支付状态
     */
    private BookingPaymentStatus paymentStatus;

    /**
     * 关联的场馆顾客档案 ID
     */
    private Long venueCustomerId;
}
