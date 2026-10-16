package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.PostInteractionMapper;
import com.sportsbooking.admin.base.mapper.PostMapper;
import com.sportsbooking.admin.base.po.Post;
import com.sportsbooking.admin.base.po.PostComment;
import com.sportsbooking.admin.base.po.PostCommentRevision;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.PostCommentRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostCommentVO;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import com.sportsbooking.admin.biz.service.ContentModerationSettingService;
import com.sportsbooking.admin.biz.service.PostInteractionService;
import com.sportsbooking.admin.biz.service.ContentGovernanceService;
import com.sportsbooking.admin.biz.service.ContentRateLimitService;
import com.sportsbooking.admin.biz.service.ContentMessageService;
import com.sportsbooking.admin.biz.service.UgcMediaService;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewModerationVO;
import com.sportsbooking.admin.biz.pojo.vo.CommunityUserVO;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 评论最多两层，编辑窗口十分钟；审核开关只影响新版本。 */
@Service
public class PostInteractionServiceImpl implements PostInteractionService {
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(10);
    private final PostInteractionMapper mapper;
    private final ContentAgreementService agreement;
    private final ContentModerationSettingService settings;
    private final ContentGovernanceService governance;
    private final PostMapper posts;
    private final ContentRateLimitService rateLimit;
    private final MockContentDetectionAdapter detector;
    private final ContentMessageService messages;
    private final UgcMediaService media;

    public PostInteractionServiceImpl(PostInteractionMapper mapper, ContentAgreementService agreement,
                                      ContentModerationSettingService settings, ContentGovernanceService governance,
                                      PostMapper posts, ContentRateLimitService rateLimit, MockContentDetectionAdapter detector,
                                      ContentMessageService messages, UgcMediaService media) {
        this.mapper = mapper;
        this.agreement = agreement;
        this.settings = settings;
        this.governance = governance;
        this.posts = posts;
        this.rateLimit = rateLimit;
        this.detector = detector;
        this.messages = messages;
        this.media = media;
    }

    @Override public List<PostCommentVO> listComments(long userId, long postId) {
        requireVisible(userId, postId);
        return mapper.selectVisibleComments(postId, userId).stream().map(row -> {
            PostCommentVO comment = PostCommentVO.from(row);
            comment.setIsMine(row.authorUserId == userId);
            return comment;
        }).toList();
    }

    @Override @Transactional
    public PostCommentVO createComment(long userId, long postId, PostCommentRequest request) {
        agreement.requireAccepted(userId);
        governance.requireAllowed(userId, "COMMENT");
        requireBody(request);
        requireVisible(userId, postId);
        Post post = posts.selectForUpdate(postId);
        if (post == null || post.deletedAt != null || !"PUBLISHED".equals(post.contentStatus)
                || !Boolean.TRUE.equals(post.commentsEnabled)) throw conflict();
        if (request.getParentCommentId() != null) {
            PostComment parent = mapper.selectCommentForUpdate(request.getParentCommentId());
            // 回复只能指向同一动态的公开一级评论，不能跨动态或形成第三层。
            if (parent == null || parent.postId != postId || parent.parentCommentId != null
                    || parent.deletedAt != null || !"PUBLISHED".equals(parent.contentStatus)
                    || Integer.valueOf(1).equals(mapper.isBlocked(userId, parent.authorUserId))) throw invalid();
        }
        rateLimit.comment(userId);
        PostComment comment = new PostComment();
        comment.postId = postId;
        comment.authorUserId = userId;
        comment.parentCommentId = request.getParentCommentId();
        comment.contentStatus = "PENDING";
        comment.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        if (mapper.insertComment(comment) != 1) throw conflict();
        PostCommentVO result = appendRevision(comment, 1, request.getBody());
        if ("USER".equals(post.authorType) && post.authorId != userId) {
            messages.record(post.authorId,"POST",post.id,"comment:"+comment.id+":author",
                    "动态收到评论","有人评论了你的动态");
        }
        if (comment.parentCommentId != null) {
            PostComment parent = mapper.selectComment(comment.parentCommentId);
            if (parent.authorUserId != userId && !parent.authorUserId.equals(post.authorId)) {
                messages.record(parent.authorUserId,"POST",post.id,"comment:"+comment.id+":reply",
                        "评论收到回复","有人回复了你的评论");
            }
        }
        return result;
    }

    @Override @Transactional
    public PostCommentVO updateComment(long userId, long commentId, PostCommentRequest request) {
        agreement.requireAccepted(userId);
        governance.requireAllowed(userId, "COMMENT");
        PostComment comment = mapper.selectCommentForUpdate(commentId);
        if (comment == null || comment.authorUserId != userId || comment.deletedAt != null) throw notFound();
        if (Instant.now().isAfter(comment.createdAt.toInstant(ZoneOffset.UTC).plus(EDIT_WINDOW))) throw conflict();
        requireBody(request);
        requireVisible(userId, comment.postId);
        if (request.getParentCommentId() != null
                && !request.getParentCommentId().equals(comment.parentCommentId)) throw invalid();
        PostCommentRevision old = mapper.selectCommentRevision(comment.currentRevisionId);
        if (old == null) throw conflict();
        rateLimit.comment(userId);
        return appendRevision(comment, old.revisionNo + 1, request.getBody());
    }

    @Override @Transactional public void deleteComment(long userId, long commentId) {
        governance.requireEnabled();
        if (mapper.deleteComment(commentId, userId) != 1) throw notFound();
        media.markCommentDeleted(commentId);
    }

    @Override @Transactional public int toggleLike(long userId, long postId, boolean liked) {
        requireVisible(userId, postId);
        rateLimit.like(userId);
        if (liked) mapper.insertLike(postId, userId); else mapper.deleteLike(postId, userId);
        return mapper.countLikes(postId);
    }

    @Override @Transactional public void block(long userId, long blockedUserId) {
        governance.requireEnabled();
        if (userId == blockedUserId || blockedUserId <= 0) throw invalid();
        mapper.insertBlock(userId, blockedUserId);
        mapper.detachBlockedMembers(userId, blockedUserId);
    }
    @Override @Transactional public void unblock(long userId, long blockedUserId) {
        governance.requireEnabled();
        mapper.deleteBlock(userId, blockedUserId);
    }

    @Override
    public List<CommunityUserVO> blockedUsers(long userId) {
        governance.requireEnabled();
        return mapper.selectBlockedUsers(userId).stream().map(user ->
                new CommunityUserVO(user.id,user.nickname,user.avatarUrl,List.of(),List.of())).toList();
    }

    @Override
    @Transactional
    public void setCommentsEnabled(long userId, long postId, boolean enabled) {
        governance.requireEnabled();
        Post post = posts.selectForUpdate(postId);
        if (post == null || !"USER".equals(post.authorType) || post.authorId != userId || post.deletedAt != null) {
            throw notFound();
        }
        if (!enabled && Integer.valueOf(1).equals(mapper.hasActiveRecruitment(postId))) throw conflict();
        if (mapper.setCommentsEnabled(postId, enabled) != 1) throw conflict();
    }

    @Override
    public List<VenueReviewModerationVO> listModerationQueue() {
        return mapper.selectModerationQueue().stream().map(VenueReviewModerationVO::from).toList();
    }

    @Override
    @Transactional
    public void moderate(long adminId, long commentId, long revisionId, String action, String reason) {
        if (revisionId <= 0 || action == null || reason == null || reason.isBlank() || reason.trim().length() > 255) {
            throw invalid();
        }
        PostComment comment = mapper.selectCommentForUpdate(commentId);
        if (comment == null || comment.deletedAt != null) throw notFound();
        if (comment.currentRevisionId == null || comment.currentRevisionId != revisionId) throw conflict();
        switch (action) {
            case "APPROVE", "REJECT" -> {
                if (mapper.decideComment(revisionId, "APPROVE".equals(action) ? "APPROVED" : "REJECTED") != 1) {
                    throw conflict();
                }
                if ("APPROVE".equals(action) && mapper.publishComment(commentId, revisionId) != 1) throw conflict();
            }
            case "HIDE" -> {
                if (!"PUBLISHED".equals(comment.contentStatus) || mapper.setCommentStatus(commentId, "HIDDEN") != 1) {
                    throw conflict();
                }
            }
            case "RESTORE" -> {
                if (!"HIDDEN".equals(comment.contentStatus) || comment.publicRevisionId == null
                        || mapper.setCommentStatus(commentId, "PUBLISHED") != 1) throw conflict();
            }
            case "DELETE" -> {
                if (mapper.setCommentStatus(commentId, "DELETED") != 1) throw conflict();
                media.markCommentDeleted(commentId);
            }
            default -> throw invalid();
        }
        mapper.insertModerationLog(commentId, revisionId, action, reason.trim(), adminId);
    }

    /** 读取一次审核开关，保证版本状态和公开指针来自同一个决定。 */
    private PostCommentVO appendRevision(PostComment comment, int number, String body) {
        boolean pending = settings.commentEnabled() || detector.requiresManualReview();
        PostCommentRevision revision = new PostCommentRevision();
        revision.commentId = comment.id;
        revision.revisionNo = number;
        revision.body = body.trim();
        revision.moderationStatus = pending ? "PENDING" : "APPROVED";
        revision.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        if (mapper.insertCommentRevision(revision) != 1 || mapper.setCommentCurrent(comment.id, revision.id) != 1) {
            throw conflict();
        }
        if (!pending) {
            if (mapper.publishComment(comment.id, revision.id) != 1) throw conflict();
            if (!"HIDDEN".equals(comment.contentStatus)) comment.contentStatus = "PUBLISHED";
        }
        return new PostCommentVO(comment.id, comment.authorUserId, comment.parentCommentId, revision.body,
                comment.contentStatus, comment.createdAt,revision.moderationStatus,true,"我");
    }

    private void requireVisible(long userId, long postId) {
        governance.requireEnabled();
        if (posts.selectPublicById(userId, postId) == null) throw notFound();
    }

    private void requireBody(PostCommentRequest request) {
        if (request == null || request.getBody() == null || request.getBody().isBlank()
                || request.getBody().trim().length() > 1000) throw invalid();
    }

    private ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT); }
    private ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND); }
}
