package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 动态评论或单层回复。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PostCommentRequest {
    @Schema(description = "评论正文") @ApiModelProperty("评论正文") private String body;
    @Schema(description = "父评论 ID；只能指向一级评论") @ApiModelProperty("父评论 ID") private Long parentCommentId;
}
