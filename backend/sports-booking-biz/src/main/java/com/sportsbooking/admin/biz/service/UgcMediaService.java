package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.UgcUploadVO;
import com.sportsbooking.admin.biz.pojo.vo.UgcMediaVO;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/** UGC 上传、版本媒体、检测与私有对象读取。 */
public interface UgcMediaService {
    final class StoredFile {
        private final Path path;
        private final String mimeType;

        public StoredFile(Path path, String mimeType) {
            this.path = path;
            this.mimeType = mimeType;
        }

        public Path path() { return path; }

        public String mimeType() { return mimeType; }
    }

    UgcUploadVO upload(long userId, MultipartFile file, Integer reportedDurationSeconds);

    boolean attachPost(long userId, long revisionId, Long previousRevisionId, List<Long> uploadIds,
                       boolean autoPublish);

    boolean attachReview(long userId, long revisionId, Long previousRevisionId, List<Long> uploadIds,
                         boolean autoPublish);

    void requireDetectionReady(String contentType, long revisionId);

    void approve(String contentType, long revisionId);

    void reject(String contentType, long revisionId);

    void markPostDeleted(long postId);

    void markReviewDeleted(long reviewId);
    void markCommentDeleted(long commentId);
    void markReplyDeleted(long replyId);

    StoredFile openPost(long userId, long mediaId);

    StoredFile openReview(Long userId, long mediaId);

    Map<Long, List<UgcMediaVO>> postMediaByRevision(List<Long> revisionIds, boolean publicOnly);

    Map<Long, List<UgcMediaVO>> reviewMediaByRevision(List<Long> revisionIds, boolean publicOnly);
    List<UgcMediaVO> listForModeration(String type, long revisionId);
    StoredFile openForModeration(String type, long mediaId);
}
