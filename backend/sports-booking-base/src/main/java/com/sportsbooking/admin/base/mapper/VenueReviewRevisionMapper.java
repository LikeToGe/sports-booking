package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.VenueReviewRevision;
import com.sportsbooking.admin.base.po.VenueReviewReplyRevision;
import org.apache.ibatis.annotations.Param;

/** 评价内容版本及审核结果持久化。 */
public interface VenueReviewRevisionMapper extends BaseMapper<VenueReviewRevision> {
    int decidePending(@Param("revisionId") long revisionId, @Param("status") String status);

    int insertReplyRevision(@Param("revision") VenueReviewReplyRevision revision);

    VenueReviewReplyRevision selectReplyById(@Param("revisionId") long revisionId);

    VenueReviewReplyRevision selectLatestReplyRevision(@Param("replyId") long replyId);
}
