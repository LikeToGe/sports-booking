package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.request.VenueReviewRequest;
import com.sportsbooking.admin.biz.pojo.request.VenueReviewReplyRequest;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewReplyVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewSummaryVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewModerationVO;
import java.util.List;

/** 场地评价的发布资格、不可变版本及公开读接口。 */
public interface VenueReviewService {
    VenueReviewVO create(long userId, long bookingId, VenueReviewRequest request);

    VenueReviewVO update(long userId, long reviewId, VenueReviewRequest request);

    void delete(long userId, long reviewId);

    VenueReviewVO getMineByBooking(long userId, long bookingId);

    List<VenueReviewVO> listPublic(long venueId, Integer score, Long tagId, Boolean withImages);

    VenueReviewSummaryVO getSummary(long venueId);

    List<VenueReviewVO> listVenueReviews(long venueId);

    VenueReviewReplyVO upsertReply(long venueId, long reviewId, VenueReviewReplyRequest request);

    /** 平台可在公开入口关闭时继续处理历史待审内容。 */
    List<VenueReviewModerationVO> listModerationQueue();

    /** 平台对评价当前版本或公开状态进行有原因的审计处置。 */
    void moderate(long operatorAdminId, long reviewId, long revisionId, String action, String reason);

    void moderateReply(long operatorAdminId, long replyId, long revisionId, String action, String reason);
}
