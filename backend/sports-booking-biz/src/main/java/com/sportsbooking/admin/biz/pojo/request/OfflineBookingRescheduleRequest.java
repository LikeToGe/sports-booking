package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 线下预约同价改期请求。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@Schema(description = "线下预约同价改期请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineBookingRescheduleRequest {
    /** 目标具体场次 ID。 */
    @Schema(description = "目标具体场次 ID")
    @ApiModelProperty(value = "目标具体场次 ID")
    private long sessionSlotId;

    /** 改期原因。 */
    @Schema(description = "改期原因")
    @ApiModelProperty(value = "改期原因")
    private String reason;
}
