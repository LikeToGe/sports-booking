package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;

/** 场馆针对评价的单条官方回复。 */
public class VenueReviewReply {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long reviewId;
    public Long venueId;
    public Long currentRevisionId;
    public Long publicRevisionId;
    public String contentStatus;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
