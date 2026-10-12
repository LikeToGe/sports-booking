package com.sportsbooking.admin.base.po;

import java.time.LocalDateTime;

/** 动态评论主体。 */
public class PostComment {
    public Long id;
    public Long postId;
    public Long authorUserId;
    public Long parentCommentId;
    public Long currentRevisionId;
    public Long publicRevisionId;
    public String contentStatus;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
