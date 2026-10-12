package com.sportsbooking.admin.base.po;

import java.time.LocalDateTime;

/** 评论不可变正文版本。 */
public class PostCommentRevision {
    public Long id;
    public Long commentId;
    public Integer revisionNo;
    public String body;
    public String moderationStatus;
    public LocalDateTime createdAt;
}
