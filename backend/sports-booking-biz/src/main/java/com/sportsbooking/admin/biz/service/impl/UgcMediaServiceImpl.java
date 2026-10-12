package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.bo.ContentDetectionJobBO;
import com.sportsbooking.admin.base.bo.UgcMediaBO;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.PostMapper;
import com.sportsbooking.admin.base.mapper.UgcMediaMapper;
import com.sportsbooking.admin.base.mapper.VenueReviewMapper;
import com.sportsbooking.admin.base.mapper.VenueReviewRevisionMapper;
import com.sportsbooking.admin.base.mapper.ContentModerationSettingMapper;
import com.sportsbooking.admin.base.po.UgcUpload;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.UgcUploadVO;
import com.sportsbooking.admin.biz.pojo.vo.UgcMediaVO;
import com.sportsbooking.admin.biz.service.UgcMediaService;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 用户媒体上传和版本绑定；文件服务只接触随机对象 key。 */
@Service
public class UgcMediaServiceImpl implements UgcMediaService {
    private final UgcMediaMapper mapper;
    private final LocalUgcStorage storage;
    private final MockContentDetectionAdapter detector;
    private final PostMapper postMapper;
    private final VenueReviewMapper reviewMapper;
    private final VenueReviewRevisionMapper reviewRevisionMapper;
    private final boolean ugcEnabled;
    private final int retentionDays;
    private final ContentModerationSettingMapper settings;

    public UgcMediaServiceImpl(UgcMediaMapper mapper, LocalUgcStorage storage, MockContentDetectionAdapter detector,
                               PostMapper postMapper, VenueReviewMapper reviewMapper,
                               VenueReviewRevisionMapper reviewRevisionMapper,
                               ContentModerationSettingMapper settings,
                               @Value("${app.ugc.enabled:false}") boolean ugcEnabled,
                               @Value("${app.ugc.retention-days:0}") int retentionDays) {
        if (ugcEnabled && retentionDays <= 0) {
            throw new IllegalStateException("APP_UGC_RETENTION_DAYS is required when UGC is enabled");
        }
        this.mapper = mapper;
        this.storage = storage;
        this.detector = detector;
        this.postMapper = postMapper;
        this.reviewMapper = reviewMapper;
        this.reviewRevisionMapper = reviewRevisionMapper;
        this.ugcEnabled = ugcEnabled;
        this.retentionDays = retentionDays;
        this.settings = settings;
    }

    @Override
    public UgcUploadVO upload(long userId, MultipartFile file, Integer reportedDurationSeconds) {
        requireEnabled();
        UgcUpload upload = storage.store(file, reportedDurationSeconds);
        upload.ownerUserId = userId;
        upload.expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(24);
        try {
            if (mapper.insertUpload(upload) != 1) throw failed();
            return UgcUploadVO.from(upload);
        } catch (RuntimeException exception) {
            try { storage.delete(upload.objectKey); } catch (IOException ignored) { }
            throw exception;
        }
    }

    @Override
    public boolean attachPost(long userId, long revisionId, Long previousRevisionId,
                              List<Long> uploadIds, boolean autoPublish) {
        return attach(userId, "POST", revisionId, previousRevisionId, uploadIds, autoPublish);
    }

    @Override
    public boolean attachReview(long userId, long revisionId, Long previousRevisionId,
                                List<Long> uploadIds, boolean autoPublish) {
        return attach(userId, "REVIEW", revisionId, previousRevisionId, uploadIds, autoPublish);
    }

    /** 空集合表示移除媒体；null 沿用当前版本，未通过的对象重新进入检测。 */
    private boolean attach(long userId, String contentType, long revisionId, Long previousRevisionId,
                           List<Long> uploadIds, boolean autoPublish) {
        List<UgcMediaBO> media = new ArrayList<>();
        if (uploadIds == null && previousRevisionId != null) {
            List<UgcMediaBO> previous = "POST".equals(contentType)
                    ? mapper.selectPostMedia(previousRevisionId) : mapper.selectReviewMedia(previousRevisionId);
            for (UgcMediaBO old : previous) {
                if (!"ACTIVE".equals(old.cleanupStatus)) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
                media.add(copy(old, revisionId));
            }
        } else if (uploadIds != null && !uploadIds.isEmpty()) {
            if (uploadIds.size() > 3 || new HashSet<>(uploadIds).size() != uploadIds.size()
                    || uploadIds.stream().anyMatch(id -> id == null || id <= 0)) throw invalid();
            List<UgcUpload> locked = mapper.selectUploadsForUpdate(uploadIds);
            if (locked.size() != uploadIds.size()) throw invalid();
            Map<Long, UgcUpload> byId = new HashMap<>();
            for (UgcUpload upload : locked) {
                if (upload.ownerUserId != userId || !"STAGED".equals(upload.status)
                        || !upload.expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) throw invalid();
                byId.put(upload.id, upload);
            }
            int videos = 0;
            for (int index = 0; index < uploadIds.size(); index++) {
                UgcUpload upload = byId.get(uploadIds.get(index));
                if (upload == null) throw invalid();
                if ("VIDEO".equals(upload.mediaType)) videos++;
                media.add(fromUpload(upload, revisionId, index + 1));
            }
            if ("REVIEW".equals(contentType) && videos != 0 || videos != 0 && media.size() != 1) throw invalid();
            if (mapper.markUploadsAttached(uploadIds, userId) != uploadIds.size()) throw invalid();
        }
        if (media.isEmpty()) return false;
        int inserted = "POST".equals(contentType) ? mapper.insertPostMedia(media) : mapper.insertReviewMedia(media);
        if (inserted != media.size()) throw failed();
        boolean needsDetection = media.stream().anyMatch(item -> !"APPROVED".equals(item.moderationStatus));
        if (needsDetection && mapper.insertDetectionJob(contentType, revisionId, autoPublish) != 1) throw failed();
        return needsDetection;
    }

    private UgcMediaBO fromUpload(UgcUpload upload, long revisionId, int position) {
        UgcMediaBO media = new UgcMediaBO();
        media.revisionId = revisionId;
        media.position = position;
        media.mediaType = upload.mediaType;
        media.storageProvider = "LOCAL";
        media.objectKey = upload.objectKey;
        media.mimeType = upload.mimeType;
        media.sizeBytes = upload.sizeBytes;
        media.sha256 = upload.sha256;
        media.durationSeconds = upload.durationSeconds;
        media.moderationStatus = "PENDING";
        return media;
    }

    private UgcMediaBO copy(UgcMediaBO old, long revisionId) {
        UgcMediaBO media = new UgcMediaBO();
        media.revisionId = revisionId;
        media.position = old.position;
        media.mediaType = old.mediaType;
        media.storageProvider = old.storageProvider;
        media.objectKey = old.objectKey;
        media.mimeType = old.mimeType;
        media.sizeBytes = old.sizeBytes;
        media.sha256 = old.sha256;
        media.durationSeconds = old.durationSeconds;
        media.moderationStatus = "APPROVED".equals(old.moderationStatus) ? "APPROVED" : "PENDING";
        return media;
    }

    /** 排队中的检测不能被审核按钮抢先放行，复核/超时由平台人工决定。 */
    @Override
    public void requireDetectionReady(String contentType, long revisionId) {
        String status = mapper.selectDetectionStatus(contentType, revisionId);
        if ("QUEUED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
        }
    }

    @Override
    public void approve(String contentType, long revisionId) {
        if ("POST".equals(contentType)) mapper.approvePostMedia(revisionId);
        else mapper.approveReviewMedia(revisionId);
    }

    @Override
    public void reject(String contentType, long revisionId) {
        if ("POST".equals(contentType)) mapper.rejectPostMedia(revisionId);
        else mapper.rejectReviewMedia(revisionId);
    }

    @Override
    public void markPostDeleted(long postId) {
        LocalDateTime purgeAfter = purgeDeadline();
        mapper.markPostPurgeAfter(postId, purgeAfter);
        mapper.markPostDeleted(postId, purgeAfter);
    }

    @Override
    public void markReviewDeleted(long reviewId) {
        LocalDateTime purgeAfter = purgeDeadline();
        mapper.markReviewPurgeAfter(reviewId, purgeAfter);
        mapper.markReviewDeleted(reviewId, purgeAfter);
    }

    @Override
    public void markCommentDeleted(long commentId) { mapper.markCommentPurgeAfter(commentId,purgeDeadline()); }

    @Override
    public void markReplyDeleted(long replyId) { mapper.markReplyPurgeAfter(replyId,purgeDeadline()); }

    /** 没有显式保留期的关闭环境只能逻辑删除，不安排物理清理。 */
    private LocalDateTime purgeDeadline() {
        return retentionDays > 0 ? LocalDateTime.now(ZoneOffset.UTC).plusDays(retentionDays) : null;
    }

    @Override
    public StoredFile openPost(long userId, long mediaId) {
        requireEnabled();
        UgcMediaBO media = mapper.selectPublicPostMedia(mediaId, userId);
        if (media == null) media = mapper.selectOwnedPostMedia(mediaId, userId);
        return open(media);
    }

    @Override
    public StoredFile openReview(Long userId, long mediaId) {
        requireEnabled();
        UgcMediaBO media = mapper.selectPublicReviewMedia(mediaId);
        if (media == null && userId != null) media = mapper.selectOwnedReviewMedia(mediaId, userId);
        return open(media);
    }

    @Override
    public Map<Long, List<UgcMediaVO>> postMediaByRevision(List<Long> revisionIds, boolean publicOnly) {
        if (revisionIds.isEmpty()) return Map.of();
        Map<Long, List<UgcMediaVO>> result = new HashMap<>();
        for (UgcMediaBO media : mapper.selectPostMediaByRevisions(revisionIds)) {
            if (!"ACTIVE".equals(media.cleanupStatus)
                    || publicOnly && !"APPROVED".equals(media.moderationStatus)) continue;
            result.computeIfAbsent(media.revisionId, ignored -> new ArrayList<>()).add(UgcMediaVO.post(media));
        }
        return result;
    }

    @Override
    public Map<Long, List<UgcMediaVO>> reviewMediaByRevision(List<Long> revisionIds, boolean publicOnly) {
        if (revisionIds.isEmpty()) return Map.of();
        Map<Long, List<UgcMediaVO>> result = new HashMap<>();
        for (UgcMediaBO media : mapper.selectReviewMediaByRevisions(revisionIds)) {
            if (!"ACTIVE".equals(media.cleanupStatus)
                    || publicOnly && !"APPROVED".equals(media.moderationStatus)) continue;
            result.computeIfAbsent(media.revisionId, ignored -> new ArrayList<>()).add(UgcMediaVO.review(media));
        }
        return result;
    }

    private StoredFile open(UgcMediaBO media) {
        if (media == null || !Files.isRegularFile(storage.file(media.objectKey))) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return new StoredFile(storage.file(media.objectKey), media.mimeType);
    }

    @Override
    public List<UgcMediaVO> listForModeration(String type, long revisionId) {
        if (!List.of("POST","REVIEW").contains(type) || revisionId <= 0) throw invalid();
        List<UgcMediaBO> rows = "POST".equals(type) ? mapper.selectPostMedia(revisionId) : mapper.selectReviewMedia(revisionId);
        return rows.stream().filter(media -> !"PURGED".equals(media.cleanupStatus)).map(media ->
            new UgcMediaVO(media.id,media.mediaType,"/platform/content-media/"+type+"/file/"+media.id,
                    media.durationSeconds,media.moderationStatus)).toList();
    }

    @Override
    public StoredFile openForModeration(String type, long mediaId) {
        if (!List.of("POST","REVIEW").contains(type)) throw invalid();
        return open("POST".equals(type) ? mapper.selectPostMediaById(mediaId) : mapper.selectReviewMediaById(mediaId));
    }

    /** 检测任务与内容版本切换在同一事务内收敛。 */
    @Scheduled(fixedDelayString = "${app.ugc.detection-delay-ms:5000}")
    @Transactional
    public void processQueuedDetection() {
        for (ContentDetectionJobBO job : mapper.selectQueuedJobsForUpdate()) {
            String result = detector.detect();
            if (mapper.setJobResult(job.id, result) != 1) continue;
            if ("PASSED".equals(result)) {
                approve(job.contentType, job.revisionId);
                if (Boolean.TRUE.equals(job.autoPublish)) publishAfterDetection(job);
            } else if ("RISK".equals(result)) {
                reject(job.contentType, job.revisionId);
            }
        }
    }

    private void publishAfterDetection(ContentDetectionJobBO job) {
        if ("POST".equals(job.contentType)) {
            if (postMapper.decideRevision(job.revisionId, "APPROVED") == 1) {
                postMapper.publishRevisionByRevision(job.revisionId);
            }
        } else if (reviewRevisionMapper.decidePending(job.revisionId, "APPROVED") == 1) {
            reviewMapper.publishRevisionByRevision(job.revisionId);
        }
    }

    /** 文件删除可重试，只有所有版本引用均到期且无保留标记才清理。 */
    @Scheduled(fixedDelayString = "${app.ugc.cleanup-delay-ms:60000}")
    @Transactional
    public void purgeExpiredObjects() {
        // 与举报/申诉的保留写入共用事务锁，选中对象后不能被并发标记为保留。
        settings.selectSingletonForUpdate();
        try {
            for (UgcUpload upload : mapper.selectExpiredUploads()) {
                storage.delete(upload.objectKey);
                mapper.markUploadPurged(upload.id);
            }
            for (String key : mapper.selectPurgeablePostObjects()) {
                storage.delete(key);
                mapper.markPostObjectPurged(key);
            }
            for (String key : mapper.selectPurgeableReviewObjects()) {
                storage.delete(key);
                mapper.markReviewObjectPurged(key);
            }
        } catch (IOException exception) {
            throw failed();
        }
    }

    private void requireEnabled() {
        if (!ugcEnabled) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.UGC_DISABLED);
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
    }

    private ApiException failed() {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UGC_UPLOAD_FAILED);
    }
}
