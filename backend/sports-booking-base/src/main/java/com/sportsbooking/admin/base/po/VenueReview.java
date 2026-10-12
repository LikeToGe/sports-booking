package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 场地评价归属与当前、公开内容版本。 */
@TableName("s_venue_review")
public class VenueReview {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long bookingId;
    public Long venueId;
    public Long userId;
    public Long sportTypeId;
    public Long currentRevisionId;
    public Long publicRevisionId;
    public String contentStatus;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
