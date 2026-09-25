package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.BalanceHoldStatus;
import com.sportsbooking.admin.base.enums.BalanceType;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 预约固定余额批次组成。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PaymentCompositionLotVO {
    @Schema(description = "冻结明细 ID") @ApiModelProperty("冻结明细 ID") private Long holdId;
    @Schema(description = "余额批次 ID") @ApiModelProperty("余额批次 ID") private Long lotId;
    @Schema(description = "余额类型") @ApiModelProperty("余额类型") private BalanceType type;
    @Schema(description = "金额，单位分") @ApiModelProperty("金额，单位分") private Integer amountFen;
    @Schema(description = "冻结状态") @ApiModelProperty("冻结状态") private BalanceHoldStatus status;
    @Schema(description = "批次到期时间") @ApiModelProperty("批次到期时间") private Instant expiresAt;
}
