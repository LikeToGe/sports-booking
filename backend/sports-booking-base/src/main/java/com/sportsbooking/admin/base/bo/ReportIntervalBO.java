package com.sportsbooking.admin.base.bo;

import java.time.Instant;
import lombok.Data;

/** 报表批量读取的供给或预约区间，不包含个人资料。 */
@Data
public class ReportIntervalBO {
    /** 场馆 ID。 */
    private Long venueId;
    /** 场地 ID。 */
    private Long courtId;
    /** 场地名称。 */
    private String courtName;
    /** 运动类型 ID。 */
    private Long sportTypeId;
    /** 运动名称。 */
    private String sportName;
    /** 预约 ID；供给记录为空。 */
    private Long bookingId;
    /** 开始时刻。 */
    private Instant startAt;
    /** 结束时刻。 */
    private Instant endAt;
    /** SUPPLY、UNAVAILABLE、BOOKED、CANCELLED 或 OTHER。 */
    private String kind;
}
