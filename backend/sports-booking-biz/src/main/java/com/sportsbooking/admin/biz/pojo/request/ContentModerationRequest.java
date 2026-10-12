package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 平台内容审核或处置请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentModerationRequest {
    /** 页面看到的不可变内容版本。 */
    @Schema(description = "页面看到的内容版本 ID") @ApiModelProperty("页面看到的内容版本 ID")
    private Long revisionId;
    /** 通过、拒绝、隐藏、恢复或删除。 */
    @Schema(description = "APPROVE、REJECT、HIDE、RESTORE 或 DELETE") @ApiModelProperty("审核或处置动作")
    private String action;
    /** 本次处置原因，写入不可覆盖的审计记录。 */
    @Schema(description = "处置原因") @ApiModelProperty("处置原因")
    private String reason;
}
