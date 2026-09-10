package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 月度平台服务费逐笔预约查询结果。
 * 创建时间: 2026/09/08
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceFeeBookingBO {
    /** 预约单号。 */
    private String bookingNo;
    /** 场馆 ID。 */
    private Long venueId;
    /** 场馆名称快照。 */
    private String venueName;
    /** 场地名称快照。 */
    private String courtName;
    /** 场次开始时间。 */
    private String startAt;
    /** 场次结束时间。 */
    private String endAt;
    /** 预约状态。 */
    private BookingStatus status;
    /** 预约金额，单位为分。 */
    private Integer amountFen;
    /** 预约服务费率快照，单位为基点。 */
    private Integer serviceFeeRateBasisPoints;
    /** 场馆当前服务费率，单位为基点。 */
    private Integer currentServiceFeeRateBasisPoints;
}
