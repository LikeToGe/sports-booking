package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** 平台专用案件证据，禁止用于公开内容展示。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ContentCaseEvidenceVO {
    /** 举报或申诉案件。 */
    @Schema(description = "举报或申诉案件") @ApiModelProperty("举报或申诉案件") private ContentGovernanceVO caseRecord;
    /** REPORT_SUBMISSION、MODERATION_DECISION、LEGACY_UNRECORDED 或 ACCOUNT_TARGET。 */
    @Schema(description = "REPORT_SUBMISSION、MODERATION_DECISION、LEGACY_UNRECORDED 或 ACCOUNT_TARGET") @ApiModelProperty("REPORT_SUBMISSION、MODERATION_DECISION、LEGACY_UNRECORDED 或 ACCOUNT_TARGET") private String evidenceSource;
    /** 提交时版本或所申诉处置版本 ID，旧举报未记录时为空。 */
    @Schema(description = "提交时版本或所申诉处置版本 ID，旧举报未记录时为空") @ApiModelProperty("提交时版本或所申诉处置版本 ID，旧举报未记录时为空") private Long evidenceRevisionId;
    /** 目标当前状态，目标不存在时为 MISSING。 */
    @Schema(description = "目标当前状态，目标不存在时为 MISSING") @ApiModelProperty("目标当前状态，目标不存在时为 MISSING") private String currentStatus;
    /** 目标当前版本 ID。 */
    @Schema(description = "目标当前版本 ID") @ApiModelProperty("目标当前版本 ID") private Long currentRevisionId;
    /** 目标当前公开版本 ID。 */
    @Schema(description = "目标当前公开版本 ID") @ApiModelProperty("目标当前公开版本 ID") private Long publicRevisionId;
    /** 保留版本列表，不代表全部历史内容均可还原。 */
    @Schema(description = "保留版本列表，不代表全部历史内容均可还原") @ApiModelProperty("保留版本列表，不代表全部历史内容均可还原") private List<ContentEvidenceRevisionVO> revisions;
    /** 目标的处置记录。 */
    @Schema(description = "目标的处置记录") @ApiModelProperty("目标的处置记录") private List<ContentGovernanceVO> decisions;
}
