package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;

/** 官方回复不可变内容版本。 */
public class VenueReviewReplyRevision {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long replyId;
    public Integer revisionNo;
    public String body;
    public String moderationStatus;
    public LocalDateTime createdAt;
}
