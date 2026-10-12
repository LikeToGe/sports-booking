package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.bo.PostBO;
import com.sportsbooking.admin.base.bo.VenueReviewModerationBO;
import com.sportsbooking.admin.base.po.Post;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 动态主体、公开信息流和版本指针持久化。 */
public interface PostMapper extends BaseMapper<Post> {
    List<PostBO> selectPublic(@Param("userId") long userId, @Param("sportTypeId") Long sportTypeId,
                              @Param("venueId") Long venueId);

    PostBO selectPublicById(@Param("userId") long userId, @Param("postId") long postId);
    List<PostBO> selectMine(@Param("userId") long userId);
    List<PostBO> selectUserPosts(@Param("userId") long userId, @Param("authorId") long authorId);
    int clearPinned();
    int setPinned(@Param("postId") long postId, @Param("pinned") boolean pinned);

    Post selectForUpdate(@Param("postId") long postId);

    Post selectOwned(@Param("postId") long postId, @Param("userId") long userId);

    int setCurrentRevision(@Param("postId") long postId, @Param("revisionId") long revisionId);

    int publishRevision(@Param("postId") long postId, @Param("revisionId") long revisionId);

    int publishRevisionByRevision(@Param("revisionId") long revisionId);

    int deleteOwned(@Param("postId") long postId, @Param("userId") long userId);

    List<VenueReviewModerationBO> selectModerationQueue();

    int decideRevision(@Param("revisionId") long revisionId, @Param("status") String status);

    int setContentStatus(@Param("postId") long postId, @Param("status") String status);

    int deleteModerated(@Param("postId") long postId);

    int insertModerationLog(@Param("postId") long postId, @Param("revisionId") long revisionId,
                            @Param("action") String action, @Param("reason") String reason,
                            @Param("operatorAdminId") long operatorAdminId);
}
