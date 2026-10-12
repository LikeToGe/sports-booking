package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.VenueReviewBO;
import com.sportsbooking.admin.base.po.VenueReview;
import com.sportsbooking.admin.base.po.VenueReviewRevision;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场地评价接口模型；公开查询不返回预约或用户身份。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueReviewVO {
    @Schema(description = "评价 ID") @ApiModelProperty("评价 ID") private Long id;
    @Schema(description = "场馆 ID") @ApiModelProperty("场馆 ID") private Long venueId;
    @Schema(description = "运动类型 ID") @ApiModelProperty("运动类型 ID") private Long sportTypeId;
    @Schema(description = "运动名称") @ApiModelProperty("运动名称") private String sportName;
    @Schema(description = "到场月份") @ApiModelProperty("到场月份") private String visitMonth;
    @Schema(description = "评价正文") @ApiModelProperty("评价正文") private String body;
    @Schema(description = "总体评分") @ApiModelProperty("总体评分") private Integer overallScore;
    @Schema(description = "场地评分") @ApiModelProperty("场地评分") private Integer courtScore;
    @Schema(description = "环境评分") @ApiModelProperty("环境评分") private Integer environmentScore;
    @Schema(description = "服务评分") @ApiModelProperty("服务评分") private Integer serviceScore;
    @Schema(description = "是否匿名") @ApiModelProperty("是否匿名") private Boolean anonymous;
    @Schema(description = "当前内容状态") @ApiModelProperty("当前内容状态") private String contentStatus;
    @Schema(description = "当前版本审核状态") @ApiModelProperty("当前版本审核状态") private String moderationStatus;
    @Schema(description = "当前版本标签 ID") @ApiModelProperty("当前版本标签 ID") private List<Long> tagIds;
    @Schema(description = "公开标签名称") @ApiModelProperty("公开标签名称") private List<String> tagNames;
    @Schema(description = "场馆官方回复") @ApiModelProperty("场馆官方回复") private VenueReviewReplyVO reply;
    @Schema(description = "创建时间") @ApiModelProperty("创建时间") private LocalDateTime createdAt;
    @Schema(description = "当前可见版本图片") @ApiModelProperty("当前可见版本图片") private List<UgcMediaVO> media;

    public static VenueReviewVO fromPublic(VenueReviewBO review) {
        return new VenueReviewVO(review.getId(), review.getVenueId(), review.getSportTypeId(),
                review.getSportName(), review.getVisitMonth(), review.getBody(), review.getOverallScore(),
                review.getCourtScore(), review.getEnvironmentScore(), review.getServiceScore(),
                review.getAnonymous(), "PUBLISHED", "APPROVED", List.of(), List.of(),
                review.getReplyId() == null ? null : new VenueReviewReplyVO(review.getReplyId(), review.getReplyBody(),
                        review.getReplyContentStatus(), review.getReplyModerationStatus(), review.getReplyCreatedAt()),
                review.getCreatedAt(), List.of());
    }

    public static VenueReviewVO fromOwn(VenueReview review, VenueReviewRevision revision, List<Long> tagIds) {
        return new VenueReviewVO(review.id, review.venueId, review.sportTypeId, null, null,
                revision.body, revision.overallScore, revision.courtScore, revision.environmentScore,
                revision.serviceScore, revision.anonymous, review.contentStatus, revision.moderationStatus, tagIds,
                List.of(), null, review.createdAt, List.of());
    }
}
