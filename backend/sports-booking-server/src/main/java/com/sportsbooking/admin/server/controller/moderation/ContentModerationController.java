package com.sportsbooking.admin.server.controller.moderation;

import com.sportsbooking.admin.biz.pojo.request.ContentModerationRequest;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewModerationVO;
import com.sportsbooking.admin.biz.service.PostService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.support.Principals;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户动态审核队列与平台处置入口。 */
@RestController
@RequestMapping("/platform/content-moderation/posts")
public class ContentModerationController {
    @Autowired
    private PostService postService;

    @GetMapping("/list")
    public ApiResponse<List<VenueReviewModerationVO>> list() {
        Principals.platform();
        return ApiResponse.success(postService.listModerationQueue());
    }

    @PostMapping("/{postId}")
    public ApiResponse<Boolean> moderate(@PathVariable long postId, @RequestBody ContentModerationRequest request) {
        Principals.platform();
        postService.moderate(Principals.platform().getAccountId(), postId,
                request == null || request.getRevisionId() == null ? 0 : request.getRevisionId(),
                request == null ? null : request.getAction(), request == null ? null : request.getReason());
        return ApiResponse.success(true);
    }
}
