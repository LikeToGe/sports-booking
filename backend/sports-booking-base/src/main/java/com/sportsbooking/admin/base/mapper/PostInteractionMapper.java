package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.bo.PostCommentBO;
import com.sportsbooking.admin.base.bo.VenueReviewModerationBO;
import com.sportsbooking.admin.base.po.PostComment;
import com.sportsbooking.admin.base.po.PostCommentRevision;
import com.sportsbooking.admin.base.po.UserAccount;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 动态评论、点赞、拉黑和找搭子最小持久化入口。 */
public interface PostInteractionMapper {
    PostComment selectCommentForUpdate(@Param("commentId") long commentId);

    PostComment selectComment(@Param("commentId") long commentId);

    PostCommentRevision selectCommentRevision(@Param("revisionId") long revisionId);

    int insertComment(@Param("comment") PostComment comment);

    int insertCommentRevision(@Param("revision") PostCommentRevision revision);

    int setCommentCurrent(@Param("commentId") long commentId, @Param("revisionId") long revisionId);

    int publishComment(@Param("commentId") long commentId, @Param("revisionId") long revisionId);

    int decideComment(@Param("revisionId") long revisionId, @Param("status") String status);

    int deleteComment(@Param("commentId") long commentId, @Param("userId") long userId);

    /** 本人读取当前版本，其他人仅读取审核通过的公开版本。 */
    List<PostCommentBO> selectVisibleComments(@Param("postId") long postId, @Param("userId") long userId);

    int insertLike(@Param("postId") long postId, @Param("userId") long userId);

    int deleteLike(@Param("postId") long postId, @Param("userId") long userId);

    int countLikes(@Param("postId") long postId);

    int insertBlock(@Param("userId") long userId, @Param("blockedUserId") long blockedUserId);

    int deleteBlock(@Param("userId") long userId, @Param("blockedUserId") long blockedUserId);
    List<UserAccount> selectBlockedUsers(@Param("userId") long userId);

    Integer isBlocked(@Param("userId") long userId, @Param("otherUserId") long otherUserId);

    List<VenueReviewModerationBO> selectModerationQueue();
    int setCommentStatus(@Param("commentId") long commentId, @Param("status") String status);
    int insertModerationLog(@Param("commentId") long commentId, @Param("revisionId") long revisionId,
            @Param("action") String action, @Param("reason") String reason, @Param("adminId") long adminId);
    int setCommentsEnabled(@Param("postId") long postId, @Param("enabled") boolean enabled);
    Integer hasActiveRecruitment(@Param("postId") long postId);
    int detachBlockedMembers(@Param("userId") long userId, @Param("blockedUserId") long blockedUserId);

    /**
     * 拉黑移除成员后，批量按实际通过人数刷新双方相关的有效招募状态。
     *
     * @param userId 发起拉黑的用户 ID
     * @param blockedUserId 被拉黑的用户 ID
     * @return 更新的招募数量
     * @author gjm
     */
    int refreshBlockedRecruitmentCapacity(@Param("userId") long userId, @Param("blockedUserId") long blockedUserId);

    boolean isCommentOwner(@Param("commentId") long commentId, @Param("userId") long userId);
}
