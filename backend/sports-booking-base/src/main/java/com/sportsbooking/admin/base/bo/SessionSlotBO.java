package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 场馆后台某日具体场次列表项。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSlotBO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 场地 ID
     */
    private Long courtId;

    /**
     * 场地名称
     */
    private String courtName;

    /**
     * 运动类型 ID
     */
    private Long sportTypeId;

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
     * 价格，单位为分
     */
    private Integer priceFen;

    /**
     * 状态
     */
    private SessionSlotStatus status;

    /**
     * 当前关联预约 ID
     */
    private Long bookingId;

    /**
     * 当前关联预约来源
     */
    private BookingSource bookingSource;

    /**
     * 当前关联预约状态
     */
    private BookingStatus bookingStatus;

    /**
     * 当前关联预约的独立收款状态
     */
    private BookingPaymentStatus paymentStatus;

    /**
     * 当前关联预约联系人姓名
     */
    private String contactName;

    /**
     * 当前关联预约联系人手机号
     */
    private String contactPhone;
}
