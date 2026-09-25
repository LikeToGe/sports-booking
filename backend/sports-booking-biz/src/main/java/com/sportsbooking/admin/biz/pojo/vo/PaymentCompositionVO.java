package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建时冻结的预约支付组成。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PaymentCompositionVO {
    @Schema(description = "预约总额，单位分") @ApiModelProperty("预约总额，单位分") private Integer totalAmountFen;
    @Schema(description = "余额总额，单位分") @ApiModelProperty("余额总额，单位分") private Integer balanceAmountFen;
    @Schema(description = "赠送余额，单位分") @ApiModelProperty("赠送余额，单位分") private Integer giftBalanceAmountFen;
    @Schema(description = "实付余额，单位分") @ApiModelProperty("实付余额，单位分") private Integer paidBalanceAmountFen;
    @Schema(description = "外部支付额，单位分") @ApiModelProperty("外部支付额，单位分") private Integer externalAmountFen;
    @Schema(description = "余额批次明细") @ApiModelProperty("余额批次明细") private List<PaymentCompositionLotVO> lots;
}
