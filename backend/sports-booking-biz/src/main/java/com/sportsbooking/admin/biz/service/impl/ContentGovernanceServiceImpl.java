package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.bo.ContentGovernanceBO;
import com.sportsbooking.admin.base.bo.ContentTargetBO;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.po.ContentRestriction;
import com.sportsbooking.admin.base.mapper.ContentGovernanceMapper;
import com.sportsbooking.admin.base.mapper.ContentModerationSettingMapper;
import com.sportsbooking.admin.base.mapper.PostMapper;
import com.sportsbooking.admin.base.mapper.PostInteractionMapper;
import com.sportsbooking.admin.base.mapper.VenueReviewMapper;
import com.sportsbooking.admin.base.mapper.UgcMediaMapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.ContentGovernanceVO;
import com.sportsbooking.admin.biz.service.ContentGovernanceService;
import com.sportsbooking.admin.biz.service.ContentMessageService;
import com.sportsbooking.admin.biz.service.UgcMediaService;
import com.sportsbooking.admin.biz.service.UserActionEventService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 治理归属、一次申诉、平台处置和证据保留在事务中校验。 */
@Service
public class ContentGovernanceServiceImpl implements ContentGovernanceService {
    private final ContentGovernanceMapper mapper;
    private final ContentModerationSettingMapper settings;
    private final PostMapper posts;
    private final VenueReviewMapper reviews;
    private final PostInteractionMapper comments;
    private final UgcMediaMapper media;
    private final UgcMediaService mediaService;
    private final ContentMessageService messages;
    private final boolean enabled;
    private final UserActionEventService events;

    public ContentGovernanceServiceImpl(ContentGovernanceMapper mapper, ContentModerationSettingMapper settings,
            PostMapper posts, VenueReviewMapper reviews, PostInteractionMapper comments,
            UgcMediaMapper media, UgcMediaService mediaService, ContentMessageService messages,
            UserActionEventService events,
            @Value("${app.ugc.enabled:false}") boolean enabled) {
        this.mapper = mapper;
        this.settings = settings;
        this.posts = posts;
        this.reviews = reviews;
        this.comments = comments;
        this.media = media;
        this.mediaService = mediaService;
        this.messages = messages;
        this.enabled = enabled;
        this.events = events;
    }

    @Override
    public void requireEnabled() {
        if (!enabled) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.UGC_DISABLED);
    }

    @Override
    @Transactional
    public void report(long userId, String targetType, long targetId, String reasonCode, String description) {
        requireEnabled();
        requireType(targetType);
        if (targetId <= 0 || reasonCode == null
                || !List.of("SPAM", "ABUSE", "PRIVACY", "MISLEADING", "OTHER").contains(reasonCode)
                || description != null && description.trim().length() > 500) throw invalid();
        evidenceLock();
        ContentTargetBO target = mapper.selectTargetForUpdate(targetType, targetId);
        if (target == null) throw notFound();
        if (!"USER".equals(targetType) && !"PUBLISHED".equals(target.contentStatus)
                && !Objects.equals(target.ownerUserId, userId)) throw notFound();
        try {
            if (mapper.insertReport(userId, targetType, targetId, reasonCode,
                    description == null ? null : description.trim()) != 1) throw conflict();
        } catch (DuplicateKeyException duplicate) { throw conflict(); }
        mapper.setLegalHold(targetType, targetId, true);
        events.fact(userId,"REPORT_SUBMIT",targetType,targetId);
    }

    @Override
    @Transactional
    public void appeal(long userId, long moderationLogId, String body) {
        requireEnabled();
        requireText(body, 500);
        evidenceLock();
        ContentGovernanceBO log = mapper.selectLog(moderationLogId);
        if (log == null || !List.of("REJECT", "HIDE", "DELETE", "RESTRICT").contains(log.getAction())) throw notFound();
        ContentTargetBO target = mapper.selectTargetForUpdate(log.getTargetType(), log.getTargetId());
        if (target == null || !Objects.equals(target.ownerUserId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
        }
        if (Integer.valueOf(1).equals(media.hasPurgedMedia(log.getTargetType(), log.getTargetId()))) throw conflict();
        try {
            mapper.insertAppeal(moderationLogId, userId, body.trim());
        } catch (DuplicateKeyException duplicate) { throw conflict(); }
        mapper.setLegalHold(log.getTargetType(), log.getTargetId(), true);
    }

    @Override public List<ContentGovernanceVO> reports(String status) {
        requireStatus(status, List.of("PENDING", "RESOLVED", "REJECTED"));
        return mapper.selectReports(status, null).stream().map(ContentGovernanceVO::from).toList();
    }

    @Override public List<ContentGovernanceVO> appeals(String status) {
        requireStatus(status, List.of("PENDING", "UPHELD", "REVERSED"));
        return mapper.selectAppeals(status, null).stream().map(ContentGovernanceVO::from).toList();
    }

    @Override public List<ContentGovernanceVO> myReports(long userId) {
        requireEnabled();
        return mapper.selectReports(null, userId).stream().map(ContentGovernanceVO::from).toList();
    }

    @Override public List<ContentGovernanceVO> myAppeals(long userId) {
        requireEnabled();
        return mapper.selectAppeals(null, userId).stream().map(ContentGovernanceVO::from).toList();
    }

    @Override public List<ContentGovernanceVO> myDecisions(long userId) {
        requireEnabled();
        return mapper.selectUserDecisions(userId).stream().map(ContentGovernanceVO::from).toList();
    }

    @Override
    @Transactional
    public void resolveReport(long adminId, long id, String status, String resolution) {
        if (status == null || !List.of("RESOLVED", "REJECTED").contains(status)) throw invalid();
        requireText(resolution, 255);
        evidenceLock();
        ContentGovernanceBO report = mapper.selectReportForUpdate(id);
        if (report == null) throw notFound();
        if (mapper.resolveReport(id, adminId, status, resolution.trim()) != 1) throw conflict();
        messages.record(report.getUserId(), "REPORT", id, "report:" + id,
                "举报已处理", resolution.trim());
        events.fact(report.getUserId(),"REPORT_RESOLVE",report.getTargetType(),report.getTargetId());
    }

    @Override
    @Transactional
    public void decideAppeal(long adminId, long id, String status, String reason) {
        if (status == null || !List.of("UPHELD", "REVERSED").contains(status)) throw invalid();
        requireText(reason, 255);
        evidenceLock();
        ContentGovernanceBO appeal = mapper.selectAppealForUpdate(id);
        if (appeal == null || !"PENDING".equals(appeal.getStatus())) throw conflict();
        ContentGovernanceBO log = mapper.selectLog(appeal.getModerationLogId());
        ContentTargetBO target = mapper.selectTargetForUpdate(log.getTargetType(), log.getTargetId());
        if ("REVERSED".equals(status)) {
            boolean restriction = "RESTRICT".equals(log.getAction());
            // 新的处置或版本已经改变事实时，不允许旧申诉覆盖后续管理员决定。
            if (target == null || !restriction && (!Objects.equals(mapper.selectLatestLogId(log.getTargetType(), log.getTargetId()), log.getId())
                    || !Objects.equals(target.currentRevisionId, log.getRevisionId()))
                    || Integer.valueOf(1).equals(media.hasPurgedMedia(log.getTargetType(), log.getTargetId()))) throw conflict();
            if (restriction) {
                if (mapper.revokeRestriction(log.getTargetId(),log.getRevisionId()) != 1) throw conflict();
            } else if ("REJECT".equals(log.getAction())) {
                mediaService.requireDetectionReady(log.getTargetType(), log.getRevisionId());
                if (mapper.restoreRevision(log.getTargetType(), log.getRevisionId()) != 1) throw conflict();
                publish(log.getTargetType(), log.getTargetId(), log.getRevisionId());
                if (!"COMMENT".equals(log.getTargetType())) mediaService.approve(log.getTargetType(), log.getRevisionId());
            } else {
                if (mapper.restoreTarget(log.getTargetType(), log.getTargetId()) != 1) throw conflict();
                if ("POST".equals(log.getTargetType())) media.revivePostMedia(log.getTargetId());
                if ("REVIEW".equals(log.getTargetType())) media.reviveReviewMedia(log.getTargetId());
            }
            mapper.insertReversalLog(log.getTargetType(), log.getTargetId(), log.getRevisionId(), adminId, reason.trim());
        }
        if (mapper.decideAppeal(id, adminId, status, reason.trim()) != 1) throw conflict();
        messages.record(appeal.getUserId(), "APPEAL", id, "appeal:" + id,
                status.equals("REVERSED") ? "申诉通过" : "申诉已处理", reason.trim());
    }

    @Override
    @Transactional
    public void restrict(long adminId, long userId, String capability, int days, String reason) {
        if (userId <= 0 || capability == null || !List.of("POST", "COMMENT", "RECRUITMENT").contains(capability)
                || !List.of(1, 7, 30, 0).contains(days)) throw invalid();
        requireText(reason, 255);
        if (mapper.selectTargetForUpdate("USER", userId) == null) throw notFound();
        ContentRestriction restriction = new ContentRestriction();
        restriction.userId = userId;
        restriction.capability = capability;
        restriction.reason = reason.trim();
        restriction.operatorAdminId = adminId;
        restriction.expiresAt = days == 0 ? null : LocalDateTime.now(ZoneOffset.UTC).plusDays(days);
        if (mapper.insertRestriction(restriction) != 1 || mapper.insertRestrictionLog(restriction) != 1) throw conflict();
        messages.record(userId, "USER", userId, "restriction:" + userId + ":" + capability + ":" + System.nanoTime(),
                "内容权限已调整", reason.trim());
    }

    @Override
    public void requireAllowed(long userId, String capability) {
        requireEnabled();
        if (Integer.valueOf(1).equals(mapper.restricted(userId, capability))) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
        }
    }

    @Override
    @Transactional
    public void releaseHold(String targetType, long targetId) {
        requireType(targetType);
        evidenceLock();
        if (Integer.valueOf(1).equals(mapper.hasPendingEvidence(targetType, targetId))) throw conflict();
        if (mapper.setLegalHold(targetType, targetId, false) != 1) throw notFound();
    }

    private void publish(String type, long id, long revisionId) {
        int changed = switch (type) {
            case "POST" -> posts.publishRevision(id, revisionId);
            case "REVIEW" -> reviews.publishRevision(id, revisionId);
            case "COMMENT" -> comments.publishComment(id, revisionId);
            default -> 0;
        };
        if (changed != 1) throw conflict();
    }

    /** ponytail: 证据保留与清理共用单行锁，吞吐需要提升时改为对象级锁。 */
    private void evidenceLock() { settings.selectSingletonForUpdate(); }

    private void requireType(String type) {
        if (type == null || !List.of("REVIEW", "REVIEW_REPLY", "POST", "COMMENT", "USER").contains(type)) throw invalid();
    }

    private void requireText(String text, int limit) {
        if (text == null || text.isBlank() || text.trim().length() > limit) throw invalid();
    }

    private void requireStatus(String status, List<String> allowed) {
        if (status != null && !allowed.contains(status)) throw invalid();
    }

    private ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT); }
    private ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND); }
}
