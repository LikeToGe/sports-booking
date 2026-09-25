package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.RechargeChannel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 员工小程序人工充值请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffRechargeRequest {
    /** 当前选择的员工场馆 ID。 */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private long venueId;
    /** 本场馆顾客 ID。 */
    @Schema(description = "顾客 ID")
    @ApiModelProperty(value = "顾客 ID")
    private long customerId;
    /** 本场馆有效充值档位 ID。 */
    @Schema(description = "充值档位 ID")
    @ApiModelProperty(value = "充值档位 ID")
    private long planId;
    /** 人工登记的真实收款渠道。 */
    @Schema(description = "充值渠道")
    @ApiModelProperty(value = "充值渠道")
    private RechargeChannel channel;
    /** 客户端重试时保持不变的幂等键。 */
    @Schema(description = "客户端幂等键") @ApiModelProperty(value = "客户端幂等键")
    private String clientIdempotencyKey;
}
