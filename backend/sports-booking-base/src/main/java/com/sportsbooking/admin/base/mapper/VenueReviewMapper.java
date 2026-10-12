package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.bo.VenueReviewBO;
import com.sportsbooking.admin.base.bo.VenueReviewSummaryBO;
import com.sportsbooking.admin.base.bo.VenueReviewModerationBO;
import com.sportsbooking.admin.base.bo.ReviewTagCountBO;
import com.sportsbooking.admin.base.bo.VenueReviewReplyBO;
import com.sportsbooking.admin.base.po.VenueReview;
import com.sportsbooking.admin.base.po.VenueReviewReply;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 场地评价归属、公开查询与版本指针持久化。 */
public interface VenueReviewMapper extends BaseMapper<VenueReview> {
    VenueReview selectByBookingId(@Param("bookingId") long bookingId);

    VenueReview selectForUpdate(@Param("reviewId") long reviewId);

    int setCurrentRevision(@Param("reviewId") long reviewId, @Param("revisionId") long revisionId);

    int publishRevision(@Param("reviewId") long reviewId, @Param("revisionId") long revisionId);

    int publishRevisionByRevision(@Param("revisionId") long revisionId);

    int deleteOwned(@Param("reviewId") long reviewId, @Param("userId") long userId);

    List<VenueReviewBO> selectPublic(@Param("venueId") long venueId, @Param("score") Integer score,
                                     @Param("tagId") Long tagId, @Param("withImages") Boolean withImages);

    VenueReviewSummaryBO selectSummary(@Param("venueId") long venueId);

    List<ReviewTagCountBO> selectTagCounts(@Param("venueId") long venueId);

    List<VenueReviewBO> selectVenueReviews(@Param("venueId") long venueId);

    VenueReviewReplyBO selectPublicReply(@Param("reviewId") long reviewId);

    VenueReview selectVenueReview(@Param("venueId") long venueId, @Param("reviewId") long reviewId);

    VenueReview selectVenueReviewForUpdate(@Param("venueId") long venueId, @Param("reviewId") long reviewId);

    VenueReviewReply selectReplyForUpdate(@Param("reviewId") long reviewId);

    VenueReviewReply selectReplyByIdForUpdate(@Param("replyId") long replyId);

    int insertReply(@Param("reply") VenueReviewReply reply);

    int setReplyCurrentRevision(@Param("replyId") long replyId, @Param("revisionId") long revisionId);

    int publishReplyRevision(@Param("replyId") long replyId, @Param("revisionId") long revisionId);

    int setReplyContentStatus(@Param("replyId") long replyId, @Param("status") String status);

    int decideReplyPending(@Param("revisionId") long revisionId, @Param("status") String status);

    int insertReplyModerationLog(@Param("replyId") long replyId, @Param("revisionId") long revisionId,
                                 @Param("action") String action, @Param("reason") String reason,
                                 @Param("operatorAdminId") long operatorAdminId);

    List<VenueReviewModerationBO> selectModerationQueue();

    int setContentStatus(@Param("reviewId") long reviewId, @Param("status") String status);

    int deleteModerated(@Param("reviewId") long reviewId);

    int insertModerationLog(@Param("reviewId") long reviewId, @Param("revisionId") Long revisionId,
                            @Param("action") String action, @Param("reason") String reason,
                            @Param("operatorAdminId") long operatorAdminId);

    List<Long> selectActiveTagIds(@Param("sportTypeId") long sportTypeId, @Param("tagIds") List<Long> tagIds);

    int insertRevisionTags(@Param("revisionId") long revisionId, @Param("tagIds") List<Long> tagIds);

    List<Long> selectRevisionTagIds(@Param("revisionId") long revisionId);
}
