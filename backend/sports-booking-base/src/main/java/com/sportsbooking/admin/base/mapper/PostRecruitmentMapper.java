package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.po.PostRecruitment;
import com.sportsbooking.admin.base.po.PostRecruitmentMember;
import java.util.List;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 找搭子状态和成员持久化。 */
public interface PostRecruitmentMapper {
    PostRecruitment selectByPostForUpdate(@Param("postId") long postId);
    PostRecruitment selectByIdForUpdate(@Param("id") long id);
    PostRecruitment selectById(@Param("id") long id);
    PostRecruitment selectByPost(@Param("postId") long postId);
    PostRecruitmentMember selectMember(@Param("recruitmentId") long recruitmentId, @Param("userId") long userId);
    /** 仅挑选已经失效的招募，未来有效行不能占用每分钟处理额度。 */
    List<PostRecruitment> selectExpired(@Param("now") Instant now);
    List<PostRecruitment> selectAffectedByUser(@Param("userId") long userId);
    int upsertApplication(@Param("recruitmentId") long recruitmentId, @Param("userId") long userId);
    int transitionMember(@Param("recruitmentId") long recruitmentId, @Param("userId") long userId,
            @Param("expectedStatus") String expectedStatus, @Param("status") String status, @Param("reason") String reason);
    int insert(@Param("recruitment") PostRecruitment recruitment);
    int updateStatus(@Param("id") long id, @Param("status") String status);
    int insertMember(@Param("member") PostRecruitmentMember member);
    int updateMember(@Param("recruitmentId") long recruitmentId, @Param("userId") long userId, @Param("status") String status);
    int countApproved(@Param("recruitmentId") long recruitmentId);
    List<PostRecruitmentMember> selectMembers(@Param("recruitmentId") long recruitmentId);
}
