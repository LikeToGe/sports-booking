package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.MemberBalanceFlowBO;
import com.sportsbooking.admin.base.enums.BalanceBusinessType;
import com.sportsbooking.admin.base.enums.BalanceFlowDirection;
import com.sportsbooking.admin.base.enums.BalanceType;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 会员余额流水接口模型。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class BalanceFlowVO {
    @Schema(description = "流水 ID") @ApiModelProperty("流水 ID") private Long id;
    @Schema(description = "余额批次 ID") @ApiModelProperty("余额批次 ID") private Long lotId;
    @Schema(description = "余额类型") @ApiModelProperty("余额类型") private BalanceType balanceType;
    @Schema(description = "业务类型") @ApiModelProperty("业务类型") private BalanceBusinessType businessType;
    @Schema(description = "收支方向") @ApiModelProperty("收支方向") private BalanceFlowDirection direction;
    @Schema(description = "金额，单位分") @ApiModelProperty("金额，单位分") private Integer amountFen;
    @Schema(description = "预约 ID") @ApiModelProperty("预约 ID") private Long bookingId;
    @Schema(description = "充值 ID") @ApiModelProperty("充值 ID") private Long rechargeId;
    @Schema(description = "创建时间") @ApiModelProperty("创建时间") private LocalDateTime createdAt;

    public static BalanceFlowVO from(MemberBalanceFlowBO flow) {
        return new BalanceFlowVO(flow.getId(), flow.getLotId(), flow.getBalanceType(), flow.getBusinessType(),
                flow.getDirection(), flow.getAmountFen(), flow.getBookingId(), flow.getRechargeId(), flow.getCreatedAt());
    }
}
