package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.ContentGovernanceBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** 举报、申诉和处置记录的界面事实。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ContentGovernanceVO {
    /** 记录 ID。 */
    @Schema(description = "记录 ID") @ApiModelProperty("记录 ID") private Long id;
    /** 提交用户 ID。 */
    @Schema(description = "提交用户 ID") @ApiModelProperty("提交用户 ID") private Long userId;
    /** 目标类型。 */
    @Schema(description = "目标类型") @ApiModelProperty("目标类型") private String targetType;
    /** 目标 ID。 */
    @Schema(description = "目标 ID") @ApiModelProperty("目标 ID") private Long targetId;
    /** 处置记录 ID。 */
    @Schema(description = "处置记录 ID") @ApiModelProperty("处置记录 ID") private Long moderationLogId;
    /** 案件或处置对应的历史版本 ID；账号处置中为限制记录 ID。 */
    @Schema(description = "案件或处置对应的历史版本 ID；账号处置中为限制记录 ID")
    @ApiModelProperty("案件或处置对应的历史版本 ID；账号处置中为限制记录 ID") private Long revisionId;
    /** 处置动作。 */
    @Schema(description = "处置动作") @ApiModelProperty("处置动作") private String action;
    /** 提交原因。 */
    @Schema(description = "提交原因") @ApiModelProperty("提交原因") private String reason;
    /** 说明。 */
    @Schema(description = "说明") @ApiModelProperty("说明") private String description;
    /** 处理状态。 */
    @Schema(description = "处理状态") @ApiModelProperty("处理状态") private String status;
    /** 处理原因。 */
    @Schema(description = "处理原因") @ApiModelProperty("处理原因") private String decisionReason;
    /** 提交时间。 */
    @Schema(description = "提交时间") @ApiModelProperty("提交时间") private LocalDateTime createdAt;
    /** 处理时间。 */
    @Schema(description = "处理时间") @ApiModelProperty("处理时间") private LocalDateTime decidedAt;
    public static ContentGovernanceVO from(ContentGovernanceBO row) {
        return new ContentGovernanceVO(row.getId(), row.getUserId(), row.getTargetType(), row.getTargetId(), row.getModerationLogId(), row.getRevisionId(), row.getAction(), row.getReason(), row.getDescription(), row.getStatus(), row.getDecisionReason(), row.getCreatedAt(), row.getDecidedAt());
    }
}
