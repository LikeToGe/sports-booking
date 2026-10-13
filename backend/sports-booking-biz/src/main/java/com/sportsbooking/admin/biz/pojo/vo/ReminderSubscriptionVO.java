package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.ReminderSubscription;
import com.sportsbooking.admin.base.bo.ReminderSubscriptionBO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.annotations.ApiModelProperty;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前用户的提醒登记展示。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderSubscriptionVO {
    /** 登记 ID。 */
    @Schema(description = "登记 ID") @ApiModelProperty("登记 ID") private Long id;
    /** 指定场馆 ID。 */
    @Schema(description = "场馆 ID") @ApiModelProperty("场馆 ID") private Long venueId;
    /** 指定运动 ID。 */
    @Schema(description = "运动类型 ID") @ApiModelProperty("运动类型 ID") private Long sportTypeId;
    /** 目标业务日期。 */
    @Schema(description = "预约日期") @ApiModelProperty("预约日期") private LocalDate targetDate;
    /** 可用时段开始。 */
    @Schema(description = "开始时间") @ApiModelProperty("开始时间") private LocalTime startTime;
    /** 可用时段结束。 */
    @Schema(description = "结束时间") @ApiModelProperty("结束时间") private LocalTime endTime;
    /** 开售或空位。 */
    @Schema(description = "提醒类型 OPENING/RELEASED") @ApiModelProperty("提醒类型") private String type;
    /** 登记当前状态。 */
    @Schema(description = "登记状态") @ApiModelProperty("登记状态") private String status;
    /** 首次开售消息生成时刻。 */
    @Schema(description = "开售提醒生成时间") @ApiModelProperty("开售提醒生成时间") private Instant openingNotifiedAt;
    /** 初次登记时是否已有可订库存。 */
    @Schema(description = "登记时是否已有可预约场次") @ApiModelProperty("登记时是否已有可预约场次") private boolean availableNow;
    /** 当前场馆预约窗口天数。 */
    @Schema(description = "当前场馆预约窗口天数") @ApiModelProperty("当前场馆预约窗口天数") private Integer bookingDaysAhead;
    /** 当前场馆开场前最晚预约分钟数。 */
    @Schema(description = "开场前最晚预约分钟数") @ApiModelProperty("开场前最晚预约分钟数") private Integer minBookingMinutes;

    public static ReminderSubscriptionVO from(ReminderSubscription row) {
        return new ReminderSubscriptionVO(row.id, row.venueId, row.sportTypeId, row.targetDate,
                row.startTime, row.endTime, row.type, row.status,
                row.openingNotifiedAt == null ? null : row.openingNotifiedAt.toInstant(ZoneOffset.UTC),
                Boolean.TRUE.equals(row.hadOpenAtRegistration), null, null);
    }

    public static ReminderSubscriptionVO from(ReminderSubscriptionBO row) {
        return new ReminderSubscriptionVO(row.getId(), row.getVenueId(), row.getSportTypeId(), row.getTargetDate(),
                row.getStartTime(), row.getEndTime(), row.getType(), row.getStatus(),
                row.getOpeningNotifiedAt() == null ? null : row.getOpeningNotifiedAt().toInstant(ZoneOffset.UTC),
                Boolean.TRUE.equals(row.getHadOpenAtRegistration()), row.getBookingDaysAhead(),
                row.getMinBookingMinutes());
    }
}
