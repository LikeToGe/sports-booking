package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 小程序创建自助充值请求。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class MiniRechargeCreateRequest {
    /** 场馆 ID。 */
    @Schema(description = "场馆 ID") @ApiModelProperty("场馆 ID")
    private Long venueId;
    /** 充值档位 ID。 */
    @Schema(description = "充值档位 ID") @ApiModelProperty("充值档位 ID")
    private Long planId;
    /** 客户端幂等键。 */
    @Schema(description = "客户端幂等键") @ApiModelProperty("客户端幂等键")
    private String idempotencyKey;
}
