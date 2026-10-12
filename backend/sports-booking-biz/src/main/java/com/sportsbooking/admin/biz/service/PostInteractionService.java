package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.request.PostCommentRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostCommentVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewModerationVO;
import com.sportsbooking.admin.biz.pojo.vo.CommunityUserVO;
import java.util.List;

/** 动态评论、点赞和拉黑业务。 */
public interface PostInteractionService {
    List<PostCommentVO> listComments(long userId, long postId);
    PostCommentVO createComment(long userId, long postId, PostCommentRequest request);
    PostCommentVO updateComment(long userId, long commentId, PostCommentRequest request);
    void deleteComment(long userId, long commentId);
    int toggleLike(long userId, long postId, boolean liked);
    void block(long userId, long blockedUserId);
    void unblock(long userId, long blockedUserId);
    List<CommunityUserVO> blockedUsers(long userId);
    void setCommentsEnabled(long userId, long postId, boolean enabled);
    List<VenueReviewModerationVO> listModerationQueue();
    void moderate(long adminId, long commentId, long revisionId, String action, String reason);
}
