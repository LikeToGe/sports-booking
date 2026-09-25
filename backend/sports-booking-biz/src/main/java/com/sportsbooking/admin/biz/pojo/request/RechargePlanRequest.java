package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.RechargePlanStatus;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 充值档位创建或编辑请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargePlanRequest {
    /** 档位名称。 */
    @Schema(description = "档位名称") @ApiModelProperty("档位名称")
    private String name;
    /** 实付金额，单位分。 */
    @Schema(description = "实付金额，单位分") @ApiModelProperty("实付金额，单位分")
    private Integer paidAmountFen;
    /** 赠送金额，单位分。 */
    @Schema(description = "赠送金额，单位分") @ApiModelProperty("赠送金额，单位分")
    private Integer giftAmountFen;
    /** 赠送有效天数。 */
    @Schema(description = "赠送有效天数") @ApiModelProperty("赠送有效天数")
    private Integer giftValidDays;
    /** 编辑后的启停状态，创建时可空。 */
    @Schema(description = "档位状态") @ApiModelProperty("档位状态")
    private RechargePlanStatus status;
}
