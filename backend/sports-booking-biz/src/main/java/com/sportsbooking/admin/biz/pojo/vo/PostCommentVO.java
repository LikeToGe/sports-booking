package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.PostCommentBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 动态评论接口模型。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PostCommentVO {
    @Schema(description = "评论 ID") @ApiModelProperty("评论 ID") private Long id;
    @Schema(description = "作者用户 ID") @ApiModelProperty("作者用户 ID") private Long authorUserId;
    @Schema(description = "父评论 ID") @ApiModelProperty("父评论 ID") private Long parentCommentId;
    @Schema(description = "正文") @ApiModelProperty("正文") private String body;
    @Schema(description = "内容状态") @ApiModelProperty("内容状态") private String contentStatus;
    @Schema(description = "创建时间") @ApiModelProperty("创建时间") private LocalDateTime createdAt;
    /** 当前版本审核状态。 */
    @Schema(description = "审核状态") @ApiModelProperty("审核状态") private String moderationStatus;
    /** 当前用户是否作者。 */
    @Schema(description = "是否本人评论") @ApiModelProperty("是否本人评论") private Boolean isMine;
    /** 社区昵称。 */
    @Schema(description = "作者昵称") @ApiModelProperty("作者昵称") private String authorName;

    public static PostCommentVO from(PostCommentBO row) {
        return new PostCommentVO(row.id, row.authorUserId, row.parentCommentId, row.body, row.contentStatus,
                row.createdAt,row.moderationStatus,false,row.authorName);
    }
}
