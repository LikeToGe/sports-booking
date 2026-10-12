package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.VenueReviewReplyBO;
import com.sportsbooking.admin.base.po.VenueReviewReply;
import com.sportsbooking.admin.base.po.VenueReviewReplyRevision;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场馆官方回复接口模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueReviewReplyVO {
    @Schema(description = "回复 ID") @ApiModelProperty("回复 ID") private Long id;
    @Schema(description = "回复正文") @ApiModelProperty("回复正文") private String body;
    @Schema(description = "内容状态") @ApiModelProperty("内容状态") private String contentStatus;
    @Schema(description = "审核状态") @ApiModelProperty("审核状态") private String moderationStatus;
    @Schema(description = "创建时间") @ApiModelProperty("创建时间") private LocalDateTime createdAt;

    public static VenueReviewReplyVO fromPublic(VenueReviewReplyBO row) {
        return row == null ? null : new VenueReviewReplyVO(row.getReplyId(), row.getBody(),
                row.getContentStatus(), row.getModerationStatus(), row.getCreatedAt());
    }

    public static VenueReviewReplyVO fromOwn(VenueReviewReply reply, VenueReviewReplyRevision revision) {
        return new VenueReviewReplyVO(reply.id, revision.body, reply.contentStatus,
                revision.moderationStatus, revision.createdAt);
    }
}
