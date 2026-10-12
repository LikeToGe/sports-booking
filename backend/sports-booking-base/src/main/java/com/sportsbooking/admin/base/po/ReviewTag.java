package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 平台固定评价标签。 */
@TableName("s_review_tag")
public class ReviewTag {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String name;
    public String normalizedName;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
