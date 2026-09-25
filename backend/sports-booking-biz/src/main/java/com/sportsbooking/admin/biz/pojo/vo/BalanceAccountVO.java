package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 会员余额汇总和流水。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class BalanceAccountVO {
    @Schema(description = "场馆 ID") @ApiModelProperty("场馆 ID") private Long venueId;
    @Schema(description = "会员档案 ID") @ApiModelProperty("会员档案 ID") private Long venueCustomerId;
    @Schema(description = "实付余额，单位分") @ApiModelProperty("实付余额，单位分") private Long paidBalanceFen;
    @Schema(description = "赠送余额，单位分") @ApiModelProperty("赠送余额，单位分") private Long giftBalanceFen;
    @Schema(description = "最近赠送到期时间") @ApiModelProperty("最近赠送到期时间") private Instant nextGiftExpiresAt;
    @Schema(description = "最近到期赠送余额，单位分") @ApiModelProperty("最近到期赠送余额，单位分")
    private Long nextGiftExpiresAmountFen;
    @Schema(description = "余额流水") @ApiModelProperty("余额流水") private List<BalanceFlowVO> flows;
}
