package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.RechargeChannel;
import com.sportsbooking.admin.base.enums.RechargeStatus;
import com.sportsbooking.admin.base.po.MemberRecharge;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 会员充值单接口模型。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class MemberRechargeVO {
    @Schema(description = "充值 ID") @ApiModelProperty("充值 ID") private Long id;
    @Schema(description = "充值业务号") @ApiModelProperty("充值业务号") private String rechargeNo;
    @Schema(description = "场馆 ID") @ApiModelProperty("场馆 ID") private Long venueId;
    @Schema(description = "会员档案 ID") @ApiModelProperty("会员档案 ID") private Long venueCustomerId;
    @Schema(description = "档位 ID") @ApiModelProperty("档位 ID") private Long planId;
    @Schema(description = "档位名称快照") @ApiModelProperty("档位名称快照") private String planName;
    @Schema(description = "实付金额，单位分") @ApiModelProperty("实付金额，单位分") private Integer paidAmountFen;
    @Schema(description = "赠送金额，单位分") @ApiModelProperty("赠送金额，单位分") private Integer giftAmountFen;
    @Schema(description = "赠送有效天数") @ApiModelProperty("赠送有效天数") private Integer giftValidDays;
    @Schema(description = "赠送到期时间") @ApiModelProperty("赠送到期时间") private Instant giftExpiresAt;
    @Schema(description = "充值渠道") @ApiModelProperty("充值渠道") private RechargeChannel channel;
    @Schema(description = "充值状态") @ApiModelProperty("充值状态") private RechargeStatus status;
    @Schema(description = "渠道交易号") @ApiModelProperty("渠道交易号") private String providerNo;
    @Schema(description = "撤销原因") @ApiModelProperty("撤销原因") private String reverseReason;
    @Schema(description = "成功时间") @ApiModelProperty("成功时间") private Instant succeededAt;
    @Schema(description = "撤销时间") @ApiModelProperty("撤销时间") private Instant reversedAt;
    @Schema(description = "创建时间") @ApiModelProperty("创建时间") private LocalDateTime createdAt;

    public static MemberRechargeVO from(MemberRecharge recharge, long venueId, Instant giftExpiresAt) {
        return new MemberRechargeVO(recharge.id, recharge.rechargeNo, venueId, recharge.venueCustomerId,
                recharge.planId, recharge.planName, recharge.paidAmountFen, recharge.giftAmountFen,
                recharge.giftValidDays, giftExpiresAt, recharge.channel, recharge.status, recharge.providerNo,
                recharge.reverseReason, recharge.succeededAt, recharge.reversedAt, recharge.createdAt);
    }
}
