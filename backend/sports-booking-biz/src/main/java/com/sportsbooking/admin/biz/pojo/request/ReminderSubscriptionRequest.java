package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.annotations.ApiModelProperty;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户提醒登记条件。用户身份由登录会话提供。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderSubscriptionRequest {
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
    /** 提醒类型：OPENING 开售、RELEASED 空位。 */
    @Schema(description = "提醒类型 OPENING/RELEASED") @ApiModelProperty("提醒类型") private String type;
}
