package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;

/** 评论公开列表投影。 */
public class PostCommentBO {
    public Long id;
    public Long postId;
    public Long authorUserId;
    public Long parentCommentId;
    public Long revisionId;
    public String body;
    public String contentStatus;
    public LocalDateTime createdAt;
    public String authorName;
    public String moderationStatus;
}
