package com.sportsbooking.admin.base.bo;

import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Data;

/** 场馆顾客经营摘要，预约、标签分别聚合后合并。 */
@Data
public class CustomerOperationBO {
    /** 顾客档案 ID。 */
    private Long customerId;
    /** 所属场馆 ID。 */
    private Long venueId;
    /** 顾客姓名。 */
    private String contactName;
    /** 手机号。 */
    private String phone;
    /** 排除待支付和取消后的有效预约次数。 */
    private Integer validBookingCount;
    /** 最近有效预约开始时间。 */
    private Instant lastValidBookingAt;
    /** 最近有效预约来源。 */
    private String lastValidBookingSource;
    /** 最近完成线上预约的完成时间。 */
    private Instant lastCompletedAt;
    /** 最近完成线上预约的核销时间。 */
    private Instant latestVerifiedAt;
    /** 当前未来已确认预约数量。 */
    private Integer futureConfirmedCount;
    /** 每小时常约次数，格式为小时:次数。 */
    private String usualHours;
    /** 当前场馆标签名称集合。 */
    private String tagNames;
    /** 当前场馆顾客建档时间。 */
    private LocalDateTime customerCreatedAt;
}
