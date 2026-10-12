package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 找搭子招募参数。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PostRecruitmentRequest {
    @Schema(description = "预约 ID") @ApiModelProperty("预约 ID") private Long bookingId;
    @Schema(description = "运动水平") @ApiModelProperty("运动水平") private String level;
    @Schema(description = "最多参与人数") @ApiModelProperty("最多参与人数") private Integer maxMembers;
    @Schema(description = "费用模式 FREE/AA") @ApiModelProperty("费用模式") private String feeMode;
    @Schema(description = "预计 AA 金额") @ApiModelProperty("预计 AA 金额") private Integer aaAmountFen;
    @Schema(description = "说明") @ApiModelProperty("说明") private String description;
}
