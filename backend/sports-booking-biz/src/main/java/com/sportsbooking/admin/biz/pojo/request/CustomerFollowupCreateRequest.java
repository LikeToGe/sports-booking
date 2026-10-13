package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建顾客回访待办请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFollowupCreateRequest {
    /** 当前场馆顾客档案 ID。 */
    @Schema(description = "当前场馆顾客档案 ID")
    @ApiModelProperty(value = "当前场馆顾客档案 ID")
    private Long customerId;
    /** 待办目的，默认 30 天未再预约。 */
    @Schema(description = "待办目的，默认 30 天未再预约")
    @ApiModelProperty(value = "待办目的，默认 30 天未再预约")
    private String purpose;
    /** 来源分组，只允许 30_DAY_NO_REBOOK。 */
    @Schema(description = "来源分组，只允许 30_DAY_NO_REBOOK")
    @ApiModelProperty(value = "来源分组，只允许 30_DAY_NO_REBOOK")
    private String sourceGroup;
}
