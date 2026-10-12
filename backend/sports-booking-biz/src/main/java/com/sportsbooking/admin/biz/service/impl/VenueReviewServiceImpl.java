package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.VenueReviewMapper;
import com.sportsbooking.admin.base.mapper.VenueReviewRevisionMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.VenueReview;
import com.sportsbooking.admin.base.po.VenueReviewRevision;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.VenueReviewRequest;
import com.sportsbooking.admin.biz.pojo.request.VenueReviewReplyRequest;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewReplyVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewSummaryVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewModerationVO;
import com.sportsbooking.admin.biz.service.VenueReviewService;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.biz.service.ContentModerationSettingService;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import com.sportsbooking.admin.biz.service.UgcMediaService;
import com.sportsbooking.admin.biz.service.ContentMessageService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sportsbooking.admin.base.po.VenueReviewReply;
import com.sportsbooking.admin.base.po.VenueReviewReplyRevision;

/** 场地评价的归属、期限和内容版本业务实现。 */
@Service
public class VenueReviewServiceImpl implements VenueReviewService {
    private static final Duration CREATE_WINDOW = Duration.ofDays(30);
    private static final Duration EDIT_WINDOW = Duration.ofDays(7);

    private final BookingService bookingService;
    private final VenueReviewMapper reviewMapper;
    private final VenueReviewRevisionMapper revisionMapper;
    private final ContentModerationSettingService moderationSettings;
    private final ContentAgreementService contentAgreement;
    private final UgcMediaService mediaService;
    private final MockContentDetectionAdapter detector;
    private final boolean ugcEnabled;
    private final ContentMessageService messages;

    public VenueReviewServiceImpl(BookingService bookingService, VenueReviewMapper reviewMapper,
                                  VenueReviewRevisionMapper revisionMapper,
                                  ContentModerationSettingService moderationSettings,
                                  ContentAgreementService contentAgreement,
                                  UgcMediaService mediaService,
                                  MockContentDetectionAdapter detector,
                                  ContentMessageService messages,
                                  @Value("${app.ugc.enabled:false}") boolean ugcEnabled) {
        this.bookingService = bookingService;
        this.reviewMapper = reviewMapper;
        this.revisionMapper = revisionMapper;
        this.moderationSettings = moderationSettings;
        this.contentAgreement = contentAgreement;
        this.mediaService = mediaService;
        this.detector = detector;
        this.messages = messages;
        this.ugcEnabled = ugcEnabled;
    }

    /** 本人已完成的线上预约仅能在完成后 30 天内创建一条评价。 */
    @Override
    @Transactional
    public VenueReviewVO create(long userId, long bookingId, VenueReviewRequest request) {
        requireEnabled();
        contentAgreement.requireAccepted(userId);
        Booking booking = bookingService.requireReviewEligibleBooking(userId, bookingId);
        if (Instant.now().isAfter(booking.completedAt.plus(CREATE_WINDOW))) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REVIEW_WINDOW_CLOSED);
        }
        if (reviewMapper.selectByBookingId(bookingId) != null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE);
        }
        List<Long> tagIds = validate(request, booking.sportTypeId);
        VenueReview review = new VenueReview();
        review.bookingId = bookingId;
        review.venueId = booking.venueId;
        review.userId = userId;
        review.sportTypeId = booking.sportTypeId;
        review.contentStatus = "PENDING";
        review.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        review.updatedAt = review.createdAt;
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException exception) {
            // 同一预约的并发请求由数据库唯一键裁决，删除过的评价也不能重发。
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE);
        }
        boolean moderationEnabled = moderationSettings.reviewEnabled() || detector.requiresManualReview();
        VenueReviewRevision revision = addRevision(review, 1, request, tagIds);
        boolean pendingDetection = mediaService.attachReview(userId, revision.id, null,
                request.getUploadIds(), !moderationEnabled);
        if (!moderationEnabled && !pendingDetection) {
            requirePendingDecision(review,"APPROVED");
            revision.moderationStatus = "APPROVED";
            if (reviewMapper.publishRevision(review.id, revision.id) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
            }
            review.publicRevisionId = revision.id;
            review.contentStatus = "PUBLISHED";
        }
        VenueReviewVO result = VenueReviewVO.fromOwn(review, revision, tagIds);
        result.setMedia(mediaService.reviewMediaByRevision(List.of(revision.id), false)
                .getOrDefault(revision.id, List.of()));
        return result;
    }

    /** 修改始终新增版本，已公开的旧版本保留到新版本审核通过。 */
    @Override
    @Transactional
    public VenueReviewVO update(long userId, long reviewId, VenueReviewRequest request) {
        requireEnabled();
        contentAgreement.requireAccepted(userId);
        VenueReview review = requireOwned(reviewId, userId);
        if (Instant.now().isAfter(review.createdAt.toInstant(ZoneOffset.UTC).plus(EDIT_WINDOW))) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REVIEW_WINDOW_CLOSED);
        }
        List<Long> tagIds = validate(request, review.sportTypeId);
        VenueReviewRevision current = revisionMapper.selectById(review.currentRevisionId);
        boolean moderationEnabled = moderationSettings.reviewEnabled() || detector.requiresManualReview();
        Long previousMediaRevisionId = review.currentRevisionId;
        VenueReviewRevision revision = addRevision(review, current.revisionNo + 1, request, tagIds);
        boolean pendingDetection = mediaService.attachReview(userId, revision.id, previousMediaRevisionId,
                request.getUploadIds(), !moderationEnabled);
        if (!moderationEnabled && !pendingDetection) {
            requirePendingDecision(review,"APPROVED");
            revision.moderationStatus = "APPROVED";
            if (reviewMapper.publishRevision(review.id, revision.id) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
            }
            review.publicRevisionId = revision.id;
            review.contentStatus = "HIDDEN".equals(review.contentStatus) ? "HIDDEN" : "PUBLISHED";
        }
        VenueReviewVO result = VenueReviewVO.fromOwn(review, revision, tagIds);
        result.setMedia(mediaService.reviewMediaByRevision(List.of(revision.id), false)
                .getOrDefault(revision.id, List.of()));
        return result;
    }

    /** 逻辑删除后立即停止公开；预约唯一键保留，防止重新发布。 */
    @Override
    @Transactional
    public void delete(long userId, long reviewId) {
        requireEnabled();
        requireOwned(reviewId, userId);
        if (reviewMapper.deleteOwned(reviewId, userId) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        mediaService.markReviewDeleted(reviewId);
    }

    /** 用户查询自己的评价时可见待审新版本，不返回其他用户评价。 */
    @Override
    public VenueReviewVO getMineByBooking(long userId, long bookingId) {
        requireEnabled();
        bookingService.requireReviewEligibleBooking(userId, bookingId);
        VenueReview review = reviewMapper.selectByBookingId(bookingId);
        if (review == null) {
            return null;
        }
        if (review.userId != userId || review.deletedAt != null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE);
        }
        VenueReviewVO result = VenueReviewVO.fromOwn(review, revisionMapper.selectById(review.currentRevisionId),
                reviewMapper.selectRevisionTagIds(review.currentRevisionId));
        result.setMedia(mediaService.reviewMediaByRevision(List.of(review.currentRevisionId), false)
                .getOrDefault(review.currentRevisionId, List.of()));
        return result;
    }

    /** 公开列表严格使用公开版本，最多返回最新 100 条。 */
    @Override
    public List<VenueReviewVO> listPublic(long venueId, Integer score, Long tagId, Boolean withImages) {
        requireEnabled();
        if (score != null && !validScore(score)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        List<com.sportsbooking.admin.base.bo.VenueReviewBO> rows = reviewMapper.selectPublic(venueId, score, tagId, withImages);
        Map<Long, List<com.sportsbooking.admin.biz.pojo.vo.UgcMediaVO>> media = mediaService.reviewMediaByRevision(
                rows.stream().map(com.sportsbooking.admin.base.bo.VenueReviewBO::getRevisionId).toList(), true);
        return rows.stream().map(row -> {
            VenueReviewVO review = VenueReviewVO.fromPublic(row);
            review.setMedia(media.getOrDefault(row.getRevisionId(), List.of()));
            return review;
        }).toList();
    }

    /** 非公开、隐藏和删除的评价不进入场馆评分。 */
    @Override
    public VenueReviewSummaryVO getSummary(long venueId) {
        requireEnabled();
        var summary = reviewMapper.selectSummary(venueId);
        summary.setTagCounts(reviewMapper.selectTagCounts(venueId));
        return VenueReviewSummaryVO.from(summary);
    }

    /** 场馆后台只读取已经公开的评价，回复状态随当前版本返回。 */
    @Override
    public List<VenueReviewVO> listVenueReviews(long venueId) {
        List<com.sportsbooking.admin.base.bo.VenueReviewBO> rows = reviewMapper.selectVenueReviews(venueId);
        Map<Long, List<com.sportsbooking.admin.biz.pojo.vo.UgcMediaVO>> media = mediaService.reviewMediaByRevision(
                rows.stream().map(com.sportsbooking.admin.base.bo.VenueReviewBO::getRevisionId).toList(), true);
        return rows.stream().map(row -> {
            VenueReviewVO review = VenueReviewVO.fromPublic(row);
            review.setMedia(media.getOrDefault(row.getRevisionId(), List.of()));
            return review;
        }).toList();
    }

    /** 每条评价只保留一个回复实体，修改通过不可变版本重新进入审核队列。 */
    @Override
    @Transactional
    public VenueReviewReplyVO upsertReply(long venueId, long reviewId, VenueReviewReplyRequest request) {
        requireEnabled();
        if (request == null || request.getBody() == null || request.getBody().isBlank()
                || request.getBody().trim().length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        VenueReview review = reviewMapper.selectVenueReviewForUpdate(venueId, reviewId);
        if (review == null || review.publicRevisionId == null || !"PUBLISHED".equals(review.contentStatus)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        VenueReviewReply reply = reviewMapper.selectReplyForUpdate(reviewId);
        if (reply != null && "DELETED".equals(reply.contentStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
        }
        if (reply == null) {
            reply = new VenueReviewReply();
            reply.reviewId = reviewId;
            reply.venueId = venueId;
            reviewMapper.insertReply(reply);
        }
        VenueReviewReplyRevision latest = reply.currentRevisionId == null ? null
                : revisionMapper.selectReplyById(reply.currentRevisionId);
        VenueReviewReplyRevision revision = new VenueReviewReplyRevision();
        revision.replyId = reply.id;
        revision.revisionNo = latest == null ? 1 : latest.revisionNo + 1;
        revision.body = request.getBody().trim();
        boolean moderationEnabled = moderationSettings.reviewEnabled() || detector.requiresManualReview();
        revision.moderationStatus = moderationEnabled ? "PENDING" : "APPROVED";
        revision.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        revisionMapper.insertReplyRevision(revision);
        if (reviewMapper.setReplyCurrentRevision(reply.id, revision.id) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        reply.currentRevisionId = revision.id;
        reply.contentStatus = moderationEnabled ? (reply.publicRevisionId == null ? "PENDING" : reply.contentStatus)
                : "HIDDEN".equals(reply.contentStatus) ? "HIDDEN" : "PUBLISHED";
        if (!moderationEnabled) {
            if (reviewMapper.publishReplyRevision(reply.id, revision.id) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
            }
            reply.publicRevisionId = revision.id;
        }
        messages.record(review.userId,"REVIEW",review.id,"reply:"+revision.id,
                "场馆回复了你的评价","场馆已回复你的评价");
        return VenueReviewReplyVO.fromOwn(reply, revision);
    }

    /** 平台审核台不受公开 UGC 总开关影响。 */
    @Override
    public List<VenueReviewModerationVO> listModerationQueue() {
        return reviewMapper.selectModerationQueue().stream().map(VenueReviewModerationVO::from).toList();
    }

    /** 审核只针对当前版本；隐藏和恢复不改写版本内容或评分。 */
    @Override
    @Transactional
    public void moderate(long operatorAdminId, long reviewId, long revisionId, String action, String reason) {
        if (revisionId <= 0 || action == null || reason == null || reason.isBlank() || reason.trim().length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        VenueReview review = reviewMapper.selectForUpdate(reviewId);
        if (review == null || review.deletedAt != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        if (review.currentRevisionId == null || review.currentRevisionId != revisionId) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        switch (action) {
            case "APPROVE" -> {
                mediaService.requireDetectionReady("REVIEW", revisionId);
                requirePendingDecision(review, "APPROVED");
                if (reviewMapper.publishRevision(reviewId, review.currentRevisionId) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
                mediaService.approve("REVIEW", revisionId);
            }
            case "REJECT" -> {
                requirePendingDecision(review, "REJECTED");
                mediaService.reject("REVIEW", revisionId);
            }
            case "HIDE" -> {
                if (!"PUBLISHED".equals(review.contentStatus)) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
                }
                if (reviewMapper.setContentStatus(reviewId, "HIDDEN") != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
            }
            case "RESTORE" -> {
                if (!"HIDDEN".equals(review.contentStatus) || review.publicRevisionId == null) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
                }
                if (reviewMapper.setContentStatus(reviewId, "PUBLISHED") != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
            }
            case "DELETE" -> {
                if (reviewMapper.deleteModerated(reviewId) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
                mediaService.markReviewDeleted(reviewId);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        reviewMapper.insertModerationLog(reviewId, review.currentRevisionId, action, reason.trim(), operatorAdminId);
    }

    @Override
    @Transactional
    public void moderateReply(long operatorAdminId, long replyId, long revisionId, String action, String reason) {
        if (revisionId <= 0 || action == null || reason == null || reason.isBlank() || reason.trim().length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        VenueReviewReply reply = reviewMapper.selectReplyByIdForUpdate(replyId);
        if (reply == null || reply.currentRevisionId == null || "DELETED".equals(reply.contentStatus)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        if (reply.currentRevisionId != revisionId) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        switch (action) {
            case "APPROVE", "REJECT" -> {
                String decision = "APPROVE".equals(action) ? "APPROVED" : "REJECTED";
                if (reviewMapper.decideReplyPending(reply.currentRevisionId, decision) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
                if ("APPROVE".equals(action)
                        && reviewMapper.publishReplyRevision(reply.id, reply.currentRevisionId) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
            }
            case "HIDE", "RESTORE", "DELETE" -> {
                if ("HIDE".equals(action) && !"PUBLISHED".equals(reply.contentStatus)
                        || "RESTORE".equals(action) && (!"HIDDEN".equals(reply.contentStatus) || reply.publicRevisionId == null)) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
                }
                String status = "HIDE".equals(action) ? "HIDDEN"
                        : "RESTORE".equals(action) ? "PUBLISHED" : "DELETED";
                if (reviewMapper.setReplyContentStatus(replyId, status) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
                if ("DELETE".equals(action)) mediaService.markReplyDeleted(replyId);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        reviewMapper.insertReplyModerationLog(reply.id, reply.currentRevisionId, action,
                reason.trim(), operatorAdminId);
    }

    private void requirePendingDecision(VenueReview review, String decision) {
        if (review.currentRevisionId == null
                || revisionMapper.decidePending(review.currentRevisionId, decision) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
    }

    private VenueReview requireOwned(long reviewId, long userId) {
        VenueReview review = reviewMapper.selectForUpdate(reviewId);
        if (review == null || review.userId != userId || review.deletedAt != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return review;
    }

    private VenueReviewRevision addRevision(VenueReview review, int number, VenueReviewRequest request,
                                            List<Long> tagIds) {
        VenueReviewRevision revision = new VenueReviewRevision();
        revision.reviewId = review.id;
        revision.revisionNo = number;
        revision.body = request.getBody().trim();
        revision.overallScore = request.getOverallScore();
        revision.courtScore = request.getCourtScore();
        revision.environmentScore = request.getEnvironmentScore();
        revision.serviceScore = request.getServiceScore();
        revision.anonymous = Boolean.TRUE.equals(request.getAnonymous());
        revision.moderationStatus = "PENDING";
        revision.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        revisionMapper.insert(revision);
        reviewMapper.setCurrentRevision(review.id, revision.id);
        review.currentRevisionId = revision.id;
        if (!tagIds.isEmpty()) {
            reviewMapper.insertRevisionTags(revision.id, tagIds);
        }
        return revision;
    }

    private List<Long> validate(VenueReviewRequest request, long sportTypeId) {
        if (request == null || request.getBody() == null || request.getBody().isBlank()
                || request.getBody().trim().length() > 2000 || !validScore(request.getOverallScore())
                || !validScore(request.getCourtScore()) || !validScore(request.getEnvironmentScore())
                || !validScore(request.getServiceScore())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        List<Long> tagIds = request.getTagIds() == null ? List.of() : request.getTagIds();
        if (tagIds.size() > 3 || tagIds.stream().anyMatch(id -> id == null || id <= 0)
                || tagIds.stream().distinct().count() != tagIds.size()
                || (!tagIds.isEmpty() && reviewMapper.selectActiveTagIds(sportTypeId, tagIds).size() != tagIds.size())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        return tagIds;
    }

    private boolean validScore(Integer score) {
        return score != null && score >= 1 && score <= 5;
    }

    private void requireEnabled() {
        if (!ugcEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.UGC_DISABLED);
        }
    }
}
