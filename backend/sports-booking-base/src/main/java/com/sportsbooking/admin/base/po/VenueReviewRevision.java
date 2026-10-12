package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 场地评价的一次不可变内容提交。 */
@TableName("s_venue_review_revision")
public class VenueReviewRevision {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long reviewId;
    public Integer revisionNo;
    public String body;
    public Integer overallScore;
    public Integer courtScore;
    public Integer environmentScore;
    public Integer serviceScore;
    public Boolean anonymous;
    public String moderationStatus;
    public LocalDateTime createdAt;
}
