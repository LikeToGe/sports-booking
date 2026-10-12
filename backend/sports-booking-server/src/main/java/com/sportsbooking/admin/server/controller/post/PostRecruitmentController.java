package com.sportsbooking.admin.server.controller.post;

import com.sportsbooking.admin.biz.pojo.request.PostRecruitmentRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostRecruitmentVO;
import com.sportsbooking.admin.biz.pojo.vo.RecruitmentMemberVO;
import com.sportsbooking.admin.biz.service.PostRecruitmentService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 找搭子招募入口，仅返回当前身份获准读取的事实。 */
@RestController
@RequestMapping("/mini/posts")
public class PostRecruitmentController {
    @Autowired
    private PostRecruitmentService service;

    @GetMapping("/{postId}/recruitment")
    public ApiResponse<PostRecruitmentVO> get(@PathVariable long postId) {
        return ApiResponse.success(service.get(AuthContext.getMiniUserId(), postId));
    }

    @PostMapping("/{postId}/recruitment")
    public ApiResponse<PostRecruitmentVO> create(@PathVariable long postId, @RequestBody PostRecruitmentRequest request) {
        return ApiResponse.success(service.create(AuthContext.getMiniUserId(), postId, request));
    }

    @PostMapping("/recruitments/{id}/apply")
    public ApiResponse<Boolean> apply(@PathVariable long id) {
        service.apply(AuthContext.getMiniUserId(), id);
        return ApiResponse.success(true);
    }

    @PostMapping("/recruitments/{id}/members/{userId}/decide")
    public ApiResponse<Boolean> decide(@PathVariable long id, @PathVariable long userId, @RequestParam boolean approve) {
        service.decide(AuthContext.getMiniUserId(), id, userId, approve);
        return ApiResponse.success(true);
    }

    @GetMapping("/recruitments/{id}/members")
    public ApiResponse<List<RecruitmentMemberVO>> members(@PathVariable long id) {
        return ApiResponse.success(service.members(AuthContext.getMiniUserId(), id));
    }

    @PostMapping("/recruitments/{id}/members/{userId}/remove")
    public ApiResponse<Boolean> remove(@PathVariable long id, @PathVariable long userId, @RequestParam String reason) {
        service.remove(AuthContext.getMiniUserId(), id, userId, reason);
        return ApiResponse.success(true);
    }

    @PostMapping("/recruitments/{id}/leave")
    public ApiResponse<Boolean> leave(@PathVariable long id) {
        service.leave(AuthContext.getMiniUserId(), id);
        return ApiResponse.success(true);
    }

    @PostMapping("/recruitments/{id}/close")
    public ApiResponse<Boolean> close(@PathVariable long id) {
        service.close(AuthContext.getMiniUserId(), id);
        return ApiResponse.success(true);
    }
}
