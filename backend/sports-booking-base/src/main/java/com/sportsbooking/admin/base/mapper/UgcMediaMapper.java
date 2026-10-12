package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.bo.ContentDetectionJobBO;
import com.sportsbooking.admin.base.bo.UgcMediaBO;
import com.sportsbooking.admin.base.po.UgcUpload;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 上传暂存、版本媒体、检测任务与对象清理持久化。 */
public interface UgcMediaMapper {
    int insertUpload(@Param("upload") UgcUpload upload);

    List<UgcUpload> selectUploadsForUpdate(@Param("ids") List<Long> ids);

    int markUploadsAttached(@Param("ids") List<Long> ids, @Param("userId") long userId);

    List<UgcUpload> selectExpiredUploads();

    int markUploadPurged(@Param("id") long id);

    int insertPostMedia(@Param("media") List<UgcMediaBO> media);

    int insertReviewMedia(@Param("media") List<UgcMediaBO> media);

    List<UgcMediaBO> selectPostMedia(@Param("revisionId") long revisionId);

    List<UgcMediaBO> selectPostMediaByRevisions(@Param("revisionIds") List<Long> revisionIds);

    List<UgcMediaBO> selectReviewMedia(@Param("revisionId") long revisionId);
    UgcMediaBO selectPostMediaById(@Param("mediaId") long mediaId);
    UgcMediaBO selectReviewMediaById(@Param("mediaId") long mediaId);

    List<UgcMediaBO> selectReviewMediaByRevisions(@Param("revisionIds") List<Long> revisionIds);

    UgcMediaBO selectPublicPostMedia(@Param("mediaId") long mediaId, @Param("userId") long userId);

    UgcMediaBO selectOwnedPostMedia(@Param("mediaId") long mediaId, @Param("userId") long userId);

    UgcMediaBO selectPublicReviewMedia(@Param("mediaId") long mediaId);

    UgcMediaBO selectOwnedReviewMedia(@Param("mediaId") long mediaId, @Param("userId") long userId);

    int insertDetectionJob(@Param("contentType") String contentType, @Param("revisionId") long revisionId,
                           @Param("autoPublish") boolean autoPublish);

    String selectDetectionStatus(@Param("contentType") String contentType, @Param("revisionId") long revisionId);

    List<ContentDetectionJobBO> selectQueuedJobsForUpdate();

    int setJobResult(@Param("jobId") long jobId, @Param("status") String status);

    int approvePostMedia(@Param("revisionId") long revisionId);

    int approveReviewMedia(@Param("revisionId") long revisionId);

    int rejectPostMedia(@Param("revisionId") long revisionId);

    int rejectReviewMedia(@Param("revisionId") long revisionId);

    int markPostDeleted(@Param("postId") long postId, @Param("purgeAfter") LocalDateTime purgeAfter);

    int markPostPurgeAfter(@Param("postId") long postId, @Param("purgeAfter") LocalDateTime purgeAfter);

    int markReviewDeleted(@Param("reviewId") long reviewId, @Param("purgeAfter") LocalDateTime purgeAfter);

    int markReviewPurgeAfter(@Param("reviewId") long reviewId, @Param("purgeAfter") LocalDateTime purgeAfter);
    int markCommentPurgeAfter(@Param("commentId") long commentId, @Param("purgeAfter") LocalDateTime purgeAfter);
    int markReplyPurgeAfter(@Param("replyId") long replyId, @Param("purgeAfter") LocalDateTime purgeAfter);

    List<String> selectPurgeablePostObjects();

    List<String> selectPurgeableReviewObjects();

    int markPostObjectPurged(@Param("objectKey") String objectKey);

    int markReviewObjectPurged(@Param("objectKey") String objectKey);
    int revivePostMedia(@Param("postId") long postId);
    int reviveReviewMedia(@Param("reviewId") long reviewId);
    Integer hasPurgedMedia(@Param("targetType") String targetType, @Param("targetId") long targetId);
}
