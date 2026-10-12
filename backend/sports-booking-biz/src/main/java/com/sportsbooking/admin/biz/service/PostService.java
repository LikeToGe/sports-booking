package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.request.PostRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewModerationVO;
import com.sportsbooking.admin.biz.pojo.vo.CommunityUserVO;
import java.util.List;

/** 动态首个纯文字纵向链路；媒体、评论和找搭子在后续 P4 任务接入。 */
public interface PostService {
    List<PostVO> listPublic(long userId, Long sportTypeId, Long venueId);

    PostVO getPublic(long userId, long postId);
    List<PostVO> listMine(long userId);
    CommunityUserVO userProfile(long userId, long authorId);
    PostVO createOfficial(String authorType, long authorId, PostRequest request);
    void setPinned(long postId, boolean pinned);

    PostVO create(long userId, PostRequest request);

    PostVO update(long userId, long postId, PostRequest request);

    void delete(long userId, long postId);

    List<VenueReviewModerationVO> listModerationQueue();

    void moderate(long operatorAdminId, long postId, long revisionId, String action, String reason);
}
