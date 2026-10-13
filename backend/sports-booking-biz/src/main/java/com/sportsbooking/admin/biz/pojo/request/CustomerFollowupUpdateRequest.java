package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新顾客回访待办请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFollowupUpdateRequest {
    /** 处理结果：COMPLETED 或 NOT_NEEDED。 */
    @Schema(description = "处理结果：COMPLETED 或 NOT_NEEDED")
    @ApiModelProperty(value = "处理结果：COMPLETED 或 NOT_NEEDED")
    private String status;
    /** 当前场馆内部备注，最多 255 个字符。 */
    @Schema(description = "当前场馆内部备注，最多 255 个字符")
    @ApiModelProperty(value = "当前场馆内部备注，最多 255 个字符")
    private String note;
}
