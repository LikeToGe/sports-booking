package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.ContentEvidenceRevisionBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** 平台专用案件证据，禁止用于公开内容展示。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ContentEvidenceRevisionVO {
    /** 内容版本 ID。 */
    @Schema(description = "内容版本 ID") @ApiModelProperty("内容版本 ID") private Long revisionId;
    /** 内容版本序号。 */
    @Schema(description = "内容版本序号") @ApiModelProperty("内容版本序号") private Integer revisionNo;
    /** 保留的版本原文。 */
    @Schema(description = "保留的版本原文") @ApiModelProperty("保留的版本原文") private String body;
    /** 当前版本审核状态。 */
    @Schema(description = "当前版本审核状态") @ApiModelProperty("当前版本审核状态") private String moderationStatus;
    /** 版本创建时间。 */
    @Schema(description = "版本创建时间") @ApiModelProperty("版本创建时间") private LocalDateTime createdAt;
    /** 总体评分。 */
    @Schema(description = "总体评分") @ApiModelProperty("总体评分") private Integer overallScore;
    /** 场地评分。 */
    @Schema(description = "场地评分") @ApiModelProperty("场地评分") private Integer courtScore;
    /** 环境评分。 */
    @Schema(description = "环境评分") @ApiModelProperty("环境评分") private Integer environmentScore;
    /** 服务评分。 */
    @Schema(description = "服务评分") @ApiModelProperty("服务评分") private Integer serviceScore;
    /** 匿名评价标记。 */
    @Schema(description = "匿名评价标记") @ApiModelProperty("匿名评价标记") private Boolean anonymous;
    /** 评价标签名称。 */
    @Schema(description = "评价标签名称") @ApiModelProperty("评价标签名称") private String tags;
    /** 已物理清理的媒体数量，无法读取或恢复。 */
    @Schema(description = "已物理清理的媒体数量，无法读取或恢复")
    @ApiModelProperty("已物理清理的媒体数量，无法读取或恢复") private Integer purgedMediaCount;
    public static ContentEvidenceRevisionVO from(ContentEvidenceRevisionBO row) {
        return new ContentEvidenceRevisionVO(row.getRevisionId(), row.getRevisionNo(), row.getBody(),
                row.getModerationStatus(), row.getCreatedAt(), row.getOverallScore(), row.getCourtScore(),
                row.getEnvironmentScore(), row.getServiceScore(), row.getAnonymous(), row.getTags(), row.getPurgedMediaCount());
    }
}
