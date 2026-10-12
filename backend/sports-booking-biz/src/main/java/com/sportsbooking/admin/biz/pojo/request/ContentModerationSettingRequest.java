package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 平台内容审核开关更新请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentModerationSettingRequest {
    @Schema(description = "动态审核是否开启") @ApiModelProperty("动态审核是否开启")
    private Boolean postEnabled;
    @Schema(description = "评论审核是否开启") @ApiModelProperty("评论审核是否开启")
    private Boolean commentEnabled;
    @Schema(description = "评价审核是否开启") @ApiModelProperty("评价审核是否开启")
    private Boolean reviewEnabled;
}
