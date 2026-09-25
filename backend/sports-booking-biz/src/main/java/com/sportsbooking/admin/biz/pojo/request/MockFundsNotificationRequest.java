package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.PaymentResult;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 本地 Mock 渠道通知请求。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class MockFundsNotificationRequest {
    @Schema(description = "支付或充值业务号") @ApiModelProperty("支付或充值业务号") private String businessNo;
    @Schema(description = "通知金额，单位分") @ApiModelProperty("通知金额，单位分") private Integer amountFen;
    @Schema(description = "渠道结果") @ApiModelProperty("渠道结果") private PaymentResult result;
    @Schema(description = "本地签名替身") @ApiModelProperty("本地签名替身") private String signature;
}
