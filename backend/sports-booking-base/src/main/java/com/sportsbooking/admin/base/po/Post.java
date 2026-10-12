package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 动态主体及当前/公开版本指针。 */
@TableName("s_post")
public class Post {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String authorType;
    public Long authorId;
    public Long currentRevisionId;
    public Long publicRevisionId;
    public Boolean isOfficial;
    public Boolean isPinned;
    public Integer pinnedSlot;
    public Boolean commentsEnabled;
    public String contentStatus;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
