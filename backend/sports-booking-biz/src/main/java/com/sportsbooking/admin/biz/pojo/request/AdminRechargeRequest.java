package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.RechargeChannel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场馆后台按档位登记充值请求。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class AdminRechargeRequest {
    /** 充值档位 ID。 */
    @Schema(description = "充值档位 ID") @ApiModelProperty("充值档位 ID")
    private Long planId;
    /** 线下收款渠道。 */
    @Schema(description = "充值渠道") @ApiModelProperty("充值渠道")
    private RechargeChannel channel;
    /** 客户端幂等键。 */
    @Schema(description = "客户端幂等键") @ApiModelProperty("客户端幂等键")
    private String idempotencyKey;
}
