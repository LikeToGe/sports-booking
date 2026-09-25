package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 错误充值整笔撤销请求。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class RechargeReverseRequest {
    /** 撤销原因。 */
    @Schema(description = "撤销原因") @ApiModelProperty("撤销原因")
    private String reason;
    /** 线下资金已由场馆处理。 */
    @Schema(description = "线下资金已处理") @ApiModelProperty("线下资金已处理")
    private Boolean offlineFundsHandled;
}
