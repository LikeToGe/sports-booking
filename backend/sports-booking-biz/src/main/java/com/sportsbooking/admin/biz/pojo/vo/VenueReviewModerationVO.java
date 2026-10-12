package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.VenueReviewModerationBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 平台内容处置版本。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueReviewModerationVO {
    @Schema(description = "内容类型") @ApiModelProperty("内容类型") private String contentType;
    @Schema(description = "回复 ID") @ApiModelProperty("回复 ID") private Long replyId;
    @Schema(description = "评价 ID") @ApiModelProperty("评价 ID") private Long reviewId;
    @Schema(description = "待审版本 ID") @ApiModelProperty("待审版本 ID") private Long revisionId;
    @Schema(description = "场馆 ID") @ApiModelProperty("场馆 ID") private Long venueId;
    @Schema(description = "场馆名称") @ApiModelProperty("场馆名称") private String venueName;
    @Schema(description = "评价正文") @ApiModelProperty("评价正文") private String body;
    @Schema(description = "当前公开版本正文") @ApiModelProperty("当前公开版本正文") private String publicBody;
    @Schema(description = "内容状态") @ApiModelProperty("内容状态") private String contentStatus;
    @Schema(description = "当前版本审核状态") @ApiModelProperty("当前版本审核状态") private String moderationStatus;
    @Schema(description = "总体评分") @ApiModelProperty("总体评分") private Integer overallScore;
    @Schema(description = "提交时间") @ApiModelProperty("提交时间") private LocalDateTime submittedAt;

    public static VenueReviewModerationVO from(VenueReviewModerationBO row) {
        return new VenueReviewModerationVO(row.getContentType(), row.getReplyId(), row.getReviewId(), row.getRevisionId(), row.getVenueId(),
                row.getVenueName(), row.getBody(), row.getPublicBody(), row.getContentStatus(), row.getModerationStatus(),
                row.getOverallScore(), row.getSubmittedAt());
    }
}
