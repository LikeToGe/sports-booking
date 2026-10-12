package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 动态不可变正文版本。 */
@TableName("s_post_revision")
public class PostRevision {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long postId;
    public Integer revisionNo;
    public String body;
    public Long sportTypeId;
    public Long venueId;
    public Long bookingId;
    public String moderationStatus;
    public LocalDateTime createdAt;
}
