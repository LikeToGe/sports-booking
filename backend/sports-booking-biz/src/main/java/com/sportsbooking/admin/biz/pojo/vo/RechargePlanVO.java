package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.RechargePlanStatus;
import com.sportsbooking.admin.base.po.RechargePlan;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 充值档位接口模型。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class RechargePlanVO {
    @Schema(description = "档位 ID") @ApiModelProperty("档位 ID") private Long id;
    @Schema(description = "场馆 ID") @ApiModelProperty("场馆 ID") private Long venueId;
    @Schema(description = "档位名称") @ApiModelProperty("档位名称") private String name;
    @Schema(description = "实付金额，单位分") @ApiModelProperty("实付金额，单位分") private Integer paidAmountFen;
    @Schema(description = "赠送金额，单位分") @ApiModelProperty("赠送金额，单位分") private Integer giftAmountFen;
    @Schema(description = "赠送有效天数") @ApiModelProperty("赠送有效天数") private Integer giftValidDays;
    @Schema(description = "档位状态") @ApiModelProperty("档位状态") private RechargePlanStatus status;

    public static RechargePlanVO from(RechargePlan plan) {
        return new RechargePlanVO(plan.id, plan.venueId, plan.name, plan.paidAmountFen,
                plan.giftAmountFen, plan.giftValidDays, plan.status);
    }
}
