package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.request.PostRecruitmentRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostRecruitmentVO;
import com.sportsbooking.admin.biz.pojo.vo.RecruitmentMemberVO;
import java.util.List;

/** 找搭子招募和参与关系。 */
public interface PostRecruitmentService {
    PostRecruitmentVO create(long userId, long postId, PostRecruitmentRequest request);
    PostRecruitmentVO get(long userId, long postId);
    void apply(long userId, long recruitmentId);
    void decide(long userId, long recruitmentId, long applicantId, boolean approve);
    void leave(long userId, long recruitmentId);
    List<RecruitmentMemberVO> members(long userId, long recruitmentId);
    void remove(long userId, long recruitmentId, long memberId, String reason);
    void close(long userId, long recruitmentId);
    void closeForUser(long userId);
    void closeForPost(long postId);
}
