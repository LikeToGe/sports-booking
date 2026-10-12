package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.base.bo.PostBO;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PostMapper;
import com.sportsbooking.admin.base.mapper.PostRevisionMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Post;
import com.sportsbooking.admin.base.po.PostRevision;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.PostRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewModerationVO;
import com.sportsbooking.admin.biz.pojo.vo.CommunityUserVO;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.mapper.PostInteractionMapper;
import com.sportsbooking.admin.base.mapper.ContentModerationSettingMapper;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.bo.VenueReviewModerationBO;
import com.sportsbooking.admin.biz.service.PostService;
import com.sportsbooking.admin.biz.service.ContentModerationSettingService;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import com.sportsbooking.admin.biz.service.UgcMediaService;
import com.sportsbooking.admin.biz.service.ContentGovernanceService;
import com.sportsbooking.admin.biz.service.ContentRateLimitService;
import com.sportsbooking.admin.biz.service.PostRecruitmentService;
import com.sportsbooking.admin.base.mapper.PostRecruitmentMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 动态纯文字发布、三十分钟编辑和公开信息流。 */
@Service
public class PostServiceImpl implements PostService {
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(30);

    private final PostMapper postMapper;
    private final PostRevisionMapper revisionMapper;
    private final SportTypeMapper sportTypeMapper;
    private final VenueMapper venueMapper;
    private final BookingMapper bookingMapper;
    private final ContentModerationSettingService moderationSettings;
    private final ContentAgreementService contentAgreement;
    private final UgcMediaService mediaService;
    private final ContentGovernanceService governance;
    private final ContentRateLimitService rateLimit;
    private final MockContentDetectionAdapter detector;
    private final UserAccountMapper users;
    private final PostInteractionMapper interactions;
    private final ContentModerationSettingMapper settingsMapper;
    private final PostRecruitmentService recruitments;
    private final PostRecruitmentMapper recruitmentMapper;
    private final boolean ugcEnabled;

    public PostServiceImpl(PostMapper postMapper, PostRevisionMapper revisionMapper,
                           SportTypeMapper sportTypeMapper, VenueMapper venueMapper,
                           BookingMapper bookingMapper,
                           ContentModerationSettingService moderationSettings,
                           ContentAgreementService contentAgreement,
                           UgcMediaService mediaService,
                           ContentGovernanceService governance,
                           ContentRateLimitService rateLimit,
                           MockContentDetectionAdapter detector,
                           UserAccountMapper users, PostInteractionMapper interactions,
                           ContentModerationSettingMapper settingsMapper,
                           PostRecruitmentService recruitments, PostRecruitmentMapper recruitmentMapper,
                           @Value("${app.ugc.enabled:false}") boolean ugcEnabled) {
        this.postMapper = postMapper;
        this.revisionMapper = revisionMapper;
        this.sportTypeMapper = sportTypeMapper;
        this.venueMapper = venueMapper;
        this.bookingMapper = bookingMapper;
        this.moderationSettings = moderationSettings;
        this.contentAgreement = contentAgreement;
        this.mediaService = mediaService;
        this.governance = governance;
        this.rateLimit = rateLimit;
        this.detector = detector;
        this.users = users;
        this.interactions = interactions;
        this.settingsMapper = settingsMapper;
        this.recruitments = recruitments;
        this.recruitmentMapper = recruitmentMapper;
        this.ugcEnabled = ugcEnabled;
    }

    @Override
    public List<PostVO> listPublic(long userId, Long sportTypeId, Long venueId) {
        requireEnabled();
        List<PostBO> rows = postMapper.selectPublic(userId, sportTypeId, venueId);
        Map<Long, List<com.sportsbooking.admin.biz.pojo.vo.UgcMediaVO>> media = mediaService.postMediaByRevision(
                rows.stream().map(PostBO::getRevisionId).toList(), true);
        return rows.stream().map(row -> {
            PostVO post = PostVO.from(row);
            post.setMedia(media.getOrDefault(row.getRevisionId(), List.of()));
            return post;
        }).toList();
    }

    @Override
    public PostVO getPublic(long userId, long postId) {
        requireEnabled();
        PostBO row = postMapper.selectPublicById(userId, postId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        PostVO post = PostVO.from(row);
        post.setMedia(mediaService.postMediaByRevision(List.of(row.getRevisionId()), true)
                .getOrDefault(row.getRevisionId(), List.of()));
        return post;
    }

    @Override
    public List<PostVO> listMine(long userId) {
        requireEnabled();
        List<PostBO> rows = postMapper.selectMine(userId);
        Map<Long, List<com.sportsbooking.admin.biz.pojo.vo.UgcMediaVO>> media = mediaService.postMediaByRevision(
                rows.stream().map(PostBO::getRevisionId).toList(), false);
        return rows.stream().map(row -> {
            PostVO post = PostVO.from(row);
            post.setMedia(media.getOrDefault(row.getRevisionId(), List.of()));
            return post;
        }).toList();
    }

    @Override
    public CommunityUserVO userProfile(long userId, long authorId) {
        requireEnabled();
        var author = users.selectById(authorId);
        if (author == null || author.status != EnableStatus.ACTIVE || author.deletedAt != null
                || Integer.valueOf(1).equals(interactions.isBlocked(userId, authorId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        List<PostBO> rows = postMapper.selectUserPosts(userId, authorId);
        Map<Long, List<com.sportsbooking.admin.biz.pojo.vo.UgcMediaVO>> media = mediaService.postMediaByRevision(
                rows.stream().map(PostBO::getRevisionId).toList(), true);
        List<PostVO> posts = rows.stream().map(row -> {
            PostVO post = PostVO.from(row);
            post.setMedia(media.getOrDefault(row.getRevisionId(), List.of()));
            return post;
        }).toList();
        return new CommunityUserVO(author.id, author.nickname, author.avatarUrl,
                rows.stream().map(PostBO::getSportName).distinct().toList(), posts);
    }

    @Override
    @Transactional
    public PostVO createOfficial(String authorType, long authorId, PostRequest request) {
        requireEnabled();
        if (!List.of("PLATFORM", "VENUE").contains(authorType) || request == null || request.getBookingId() != null
                || request.getUploadIds() != null && !request.getUploadIds().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        if ("VENUE".equals(authorType)) request.setVenueId(authorId);
        PostRequest valid = validate(request, 0);
        Post post = new Post();
        post.authorType = authorType;
        post.authorId = authorId;
        post.isOfficial = true;
        post.isPinned = false;
        post.commentsEnabled = true;
        post.contentStatus = "PENDING";
        post.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        post.updatedAt = post.createdAt;
        postMapper.insert(post);
        boolean pending = "VENUE".equals(authorType)
                && (moderationSettings.postEnabled() || detector.requiresManualReview());
        PostRevision revision = addRevision(post, 1, valid);
        if (postMapper.setCurrentRevision(post.id,revision.id) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        post.currentRevisionId = revision.id;
        if (!pending) {
            publish(post,revision);
            post.contentStatus = "PUBLISHED";
        }
        return PostVO.fromOwn(post,revision);
    }

    @Override
    @Transactional
    public void setPinned(long postId, boolean pinned) {
        requireEnabled();
        settingsMapper.selectSingletonForUpdate();
        Post post = postMapper.selectForUpdate(postId);
        if (post == null || !"PLATFORM".equals(post.authorType) || !"PUBLISHED".equals(post.contentStatus)
                || post.deletedAt != null) throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        if (pinned) postMapper.clearPinned();
        if (postMapper.setPinned(postId,pinned) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
    }

    @Override
    @Transactional
    public PostVO create(long userId, PostRequest request) {
        requireEnabled();
        contentAgreement.requireAccepted(userId);
        governance.requireAllowed(userId, "POST");
        PostRequest valid = validate(request, userId);
        rateLimit.post(userId);
        Post post = new Post();
        post.authorType = "USER";
        post.authorId = userId;
        post.isOfficial = false;
        post.isPinned = false;
        post.commentsEnabled = true;
        post.contentStatus = "PENDING";
        post.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        post.updatedAt = post.createdAt;
        postMapper.insert(post);
        boolean moderationEnabled = moderationSettings.postEnabled() || detector.requiresManualReview();
        PostRevision revision = addRevision(post, 1, valid);
        if (postMapper.setCurrentRevision(post.id, revision.id) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        post.currentRevisionId = revision.id;
        boolean pendingDetection = mediaService.attachPost(userId, revision.id, null,
                valid.getUploadIds(), !moderationEnabled);
        if (!moderationEnabled && !pendingDetection) {
            publish(post, revision);
            post.publicRevisionId = revision.id;
            post.contentStatus = "PUBLISHED";
        }
        PostVO result = PostVO.fromOwn(post, revision);
        result.setMedia(mediaService.postMediaByRevision(List.of(revision.id), false)
                .getOrDefault(revision.id, List.of()));
        return result;
    }

    @Override
    @Transactional
    public PostVO update(long userId, long postId, PostRequest request) {
        requireEnabled();
        contentAgreement.requireAccepted(userId);
        governance.requireAllowed(userId, "POST");
        Post post = postMapper.selectForUpdate(postId);
        if (post == null || !"USER".equals(post.authorType) || post.authorId != userId || post.deletedAt != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        if (Instant.now().isAfter(post.createdAt.toInstant(ZoneOffset.UTC).plus(EDIT_WINDOW))) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        PostRevision current = revisionMapper.selectForPost(post.currentRevisionId, post.id);
        if (current == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        boolean moderationEnabled = moderationSettings.postEnabled() || detector.requiresManualReview();
        PostRequest valid = validate(request, userId);
        if (recruitmentMapper.selectByPost(postId) != null
                && !java.util.Objects.equals(valid.getBookingId(), current.bookingId)) {
            throw new ApiException(HttpStatus.CONFLICT,ErrorCode.INVALID_ARGUMENT);
        }
        Long previousMediaRevisionId = post.currentRevisionId;
        PostRevision revision = addRevision(post, current.revisionNo + 1, valid);
        if (postMapper.setCurrentRevision(post.id, revision.id) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        boolean pendingDetection = mediaService.attachPost(userId, revision.id, previousMediaRevisionId,
                valid.getUploadIds(), !moderationEnabled);
        if (!moderationEnabled && !pendingDetection) {
            publish(post, revision);
        }
        post.currentRevisionId = revision.id;
        if (!moderationEnabled && !pendingDetection) {
            post.publicRevisionId = revision.id;
            post.contentStatus = "HIDDEN".equals(post.contentStatus) ? "HIDDEN" : "PUBLISHED";
        }
        PostVO result = PostVO.fromOwn(post, revision);
        result.setMedia(mediaService.postMediaByRevision(List.of(revision.id), false)
                .getOrDefault(revision.id, List.of()));
        return result;
    }

    @Override
    @Transactional
    public void delete(long userId, long postId) {
        requireEnabled();
        if (postMapper.selectOwned(postId,userId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,ErrorCode.NOT_FOUND);
        }
        recruitments.closeForPost(postId);
        if (postMapper.deleteOwned(postId, userId) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        mediaService.markPostDeleted(postId);
    }

    @Override
    public List<VenueReviewModerationVO> listModerationQueue() {
        return postMapper.selectModerationQueue().stream().map(VenueReviewModerationVO::from).toList();
    }

    @Override
    @Transactional
    public void moderate(long operatorAdminId, long postId, long revisionId, String action, String reason) {
        if (revisionId <= 0 || action == null || reason == null || reason.isBlank() || reason.trim().length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        Post post = postMapper.selectForUpdate(postId);
        if (post == null || post.deletedAt != null || post.currentRevisionId == null
                || post.currentRevisionId != revisionId) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
        switch (action) {
            case "APPROVE" -> {
                mediaService.requireDetectionReady("POST", revisionId);
                if (postMapper.decideRevision(revisionId, "APPROVED") != 1
                        || postMapper.publishRevision(postId, revisionId) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
                mediaService.approve("POST", revisionId);
            }
            case "REJECT" -> {
                if (postMapper.decideRevision(revisionId, "REJECTED") != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
                mediaService.reject("POST", revisionId);
            }
            case "HIDE" -> {
                if (!"PUBLISHED".equals(post.contentStatus) || postMapper.setContentStatus(postId, "HIDDEN") != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
                }
            }
            case "RESTORE" -> {
                if (!"HIDDEN".equals(post.contentStatus) || post.publicRevisionId == null
                        || postMapper.setContentStatus(postId, "PUBLISHED") != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ARGUMENT);
                }
            }
            case "DELETE" -> {
                recruitments.closeForPost(postId);
                if (postMapper.deleteModerated(postId) != 1) {
                    throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
                }
                mediaService.markPostDeleted(postId);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        postMapper.insertModerationLog(postId, revisionId, action, reason.trim(), operatorAdminId);
    }

    private PostRevision addRevision(Post post, int revisionNo, PostRequest request) {
        PostRevision revision = new PostRevision();
        revision.postId = post.id;
        revision.revisionNo = revisionNo;
        revision.body = request.getBody().trim();
        revision.sportTypeId = request.getSportTypeId();
        revision.venueId = request.getVenueId();
        revision.bookingId = request.getBookingId();
        revision.moderationStatus = "PENDING";
        revision.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        revisionMapper.insert(revision);
        return revision;
    }

    private void publish(Post post, PostRevision revision) {
        if (postMapper.decideRevision(revision.id,"APPROVED") != 1) {
            throw new ApiException(HttpStatus.CONFLICT,ErrorCode.OPERATION_IN_PROGRESS);
        }
        revision.moderationStatus = "APPROVED";
        if (postMapper.publishRevision(post.id, revision.id) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
    }

    private PostRequest validate(PostRequest request, long userId) {
        if (request == null || request.getBody() == null || request.getBody().isBlank()
                || request.getBody().trim().length() > 2000 || request.getSportTypeId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        SportType sport = sportTypeMapper.selectById(request.getSportTypeId());
        if (sport == null || sport.ownerVenueId != null || sport.status != EnableStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        if (request.getVenueId() != null) {
            Venue venue = venueMapper.selectById(request.getVenueId());
            if (venue == null || venue.status != VenueStatus.ACTIVE) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
            }
            boolean venueSupportsSport = venueMapper.selectUsedSports(request.getVenueId()).stream()
                    .anyMatch(usedSport -> request.getSportTypeId().equals(usedSport.id));
            if (!venueSupportsSport) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
            }
        }
        if (request.getBookingId() != null) {
            Booking booking = bookingMapper.selectById(request.getBookingId());
            if (booking == null || booking.userId == null || booking.userId != userId
                    || booking.status != BookingStatus.CONFIRMED
                    || (request.getVenueId() != null && !request.getVenueId().equals(booking.venueId))
                    || (request.getSportTypeId() != null && !request.getSportTypeId().equals(booking.sportTypeId))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
            }
        }
        return request;
    }

    private void requireEnabled() {
        if (!ugcEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.UGC_DISABLED);
        }
    }
}
