package com.sportsbooking.admin.server.controller.review;

import com.sportsbooking.admin.biz.pojo.request.VenueReviewRequest;
import com.sportsbooking.admin.biz.pojo.request.VenueReviewReplyRequest;
import com.sportsbooking.admin.biz.pojo.request.ContentModerationRequest;
import com.sportsbooking.admin.biz.pojo.request.ReviewTagRequest;
import com.sportsbooking.admin.biz.pojo.vo.ReviewTagVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewModerationVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewSummaryVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewReplyVO;
import com.sportsbooking.admin.biz.service.VenueReviewService;
import com.sportsbooking.admin.biz.service.ReviewTagService;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import com.sportsbooking.admin.server.support.Principals;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户评价写入与无需登录的场馆公开评价查询。 */
@RestController
@RequestMapping("/")
public class VenueReviewController {
    @Autowired
    private VenueReviewService reviewService;
    @Autowired
    private ReviewTagService tagService;
    @Value("${app.ugc.enabled:false}")
    private boolean ugcEnabled;

    /** 小程序根据环境总开关显示或隐藏内容入口。 */
    @GetMapping("public/ugc-config")
    public ApiResponse<Boolean> config() {
        return ApiResponse.success(ugcEnabled);
    }

    /** 场地评价页可选的平台固定标签。 */
    @GetMapping("public/review-tags")
    public ApiResponse<List<ReviewTagVO>> publicTags(@RequestParam long sportTypeId) {
        return ApiResponse.success(tagService.listPublic(sportTypeId));
    }

    /** 平台维护标签时可查看启停状态和全部运动关联。 */
    @GetMapping("platform/review-tags")
    public ApiResponse<List<ReviewTagVO>> platformTags() {
        Principals.platform();
        return ApiResponse.success(tagService.listPlatform());
    }

    @PostMapping("platform/review-tags")
    public ApiResponse<ReviewTagVO> createTag(@RequestBody ReviewTagRequest request) {
        Principals.platform();
        return ApiResponse.success(tagService.create(request));
    }

    @PutMapping("platform/review-tags/{tagId}")
    public ApiResponse<ReviewTagVO> updateTag(@PathVariable long tagId, @RequestBody ReviewTagRequest request) {
        Principals.platform();
        return ApiResponse.success(tagService.update(tagId, request));
    }

    /** 用户按本人已完成预约发布评价。 */
    @PostMapping("mini/bookings/{bookingId}/review")
    public ApiResponse<VenueReviewVO> create(@PathVariable long bookingId, @RequestBody VenueReviewRequest request) {
        return ApiResponse.success(reviewService.create(AuthContext.getMiniUserId(), bookingId, request));
    }

    /** 用户查询本预约的当前评价版本。 */
    @GetMapping("mini/bookings/{bookingId}/review")
    public ApiResponse<VenueReviewVO> mine(@PathVariable long bookingId) {
        return ApiResponse.success(reviewService.getMineByBooking(AuthContext.getMiniUserId(), bookingId));
    }

    /** 用户在评价创建后七天内修改内容。 */
    @PutMapping("mini/reviews/{reviewId}")
    public ApiResponse<VenueReviewVO> update(@PathVariable long reviewId, @RequestBody VenueReviewRequest request) {
        return ApiResponse.success(reviewService.update(AuthContext.getMiniUserId(), reviewId, request));
    }

    /** 删除后不能对同一预约重新评价。 */
    @DeleteMapping("mini/reviews/{reviewId}")
    public ApiResponse<Boolean> delete(@PathVariable long reviewId) {
        reviewService.delete(AuthContext.getMiniUserId(), reviewId);
        return ApiResponse.success(true);
    }

    /** 未登录访客可以读取已公开的评价。 */
    @GetMapping("public/venues/{venueId}/reviews")
    public ApiResponse<List<VenueReviewVO>> listPublic(@PathVariable long venueId,
            @RequestParam(required = false) Integer score, @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Boolean withImages) {
        return ApiResponse.success(reviewService.listPublic(venueId, score, tagId, withImages));
    }

    /** 场馆评分仅从正常公开版本计算。 */
    @GetMapping("public/venues/{venueId}/review-summary")
    public ApiResponse<VenueReviewSummaryVO> summary(@PathVariable long venueId) {
        return ApiResponse.success(reviewService.getSummary(venueId));
    }

    /** 场馆后台查看本馆已公开评价及回复状态。 */
    @GetMapping("venue-admin/reviews")
    public ApiResponse<List<VenueReviewVO>> venueReviews() {
        return ApiResponse.success(reviewService.listVenueReviews(Principals.venue(VenuePermission.REVIEW_MANAGE).getVenueId()));
    }

    /** 场馆新增或修改一条官方回复，内容进入平台评价审核队列。 */
    @PutMapping("venue-admin/reviews/{reviewId}/reply")
    public ApiResponse<VenueReviewReplyVO> upsertReply(@PathVariable long reviewId,
                                                        @RequestBody VenueReviewReplyRequest request) {
        long venueId = Principals.venue(VenuePermission.REVIEW_MANAGE).getVenueId();
        return ApiResponse.success(reviewService.upsertReply(venueId, reviewId, request));
    }

    /** 平台查看未完成审核的评价当前版本。 */
    @GetMapping("platform/content-moderation/reviews")
    public ApiResponse<List<VenueReviewModerationVO>> moderationQueue() {
        Principals.platform();
        return ApiResponse.success(reviewService.listModerationQueue());
    }

    /** 平台审核或处置评价，原因和操作者在同一事务中记入审计。 */
    @PostMapping("platform/content-moderation/reviews/{reviewId}")
    public ApiResponse<Boolean> moderate(@PathVariable long reviewId, @RequestBody ContentModerationRequest request) {
        reviewService.moderate(Principals.platform().getAccountId(), reviewId,
                request == null || request.getRevisionId() == null ? 0 : request.getRevisionId(),
                request == null ? null : request.getAction(), request == null ? null : request.getReason());
        return ApiResponse.success(true);
    }

    /** 平台审核场馆官方回复。 */
    @PostMapping("platform/content-moderation/replies/{replyId}")
    public ApiResponse<Boolean> moderateReply(@PathVariable long replyId, @RequestBody ContentModerationRequest request) {
        reviewService.moderateReply(Principals.platform().getAccountId(), replyId,
                request == null || request.getRevisionId() == null ? 0 : request.getRevisionId(),
                request == null ? null : request.getAction(), request == null ? null : request.getReason());
        return ApiResponse.success(true);
    }
}
