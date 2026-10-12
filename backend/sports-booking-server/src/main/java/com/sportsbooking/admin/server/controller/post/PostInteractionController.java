package com.sportsbooking.admin.server.controller.post;

import com.sportsbooking.admin.biz.pojo.request.PostCommentRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostCommentVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueReviewModerationVO;
import com.sportsbooking.admin.biz.pojo.vo.CommunityUserVO;
import com.sportsbooking.admin.biz.pojo.request.ContentModerationRequest;
import com.sportsbooking.admin.biz.service.PostInteractionService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import com.sportsbooking.admin.server.support.Principals;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 动态评论、点赞和拉黑入口。 */
@RestController
public class PostInteractionController {
    @Autowired private PostInteractionService service;

    @GetMapping("/mini/posts/{postId}/comments")
    public ApiResponse<List<PostCommentVO>> list(@PathVariable long postId) { return ApiResponse.success(service.listComments(AuthContext.getMiniUserId(), postId)); }
    @PostMapping("/mini/posts/{postId}/comments")
    public ApiResponse<PostCommentVO> create(@PathVariable long postId, @RequestBody PostCommentRequest request) { return ApiResponse.success(service.createComment(AuthContext.getMiniUserId(), postId, request)); }
    @PutMapping("/mini/posts/comments/{commentId}")
    public ApiResponse<PostCommentVO> update(@PathVariable long commentId, @RequestBody PostCommentRequest request) { return ApiResponse.success(service.updateComment(AuthContext.getMiniUserId(), commentId, request)); }
    @DeleteMapping("/mini/posts/comments/{commentId}")
    public ApiResponse<Boolean> delete(@PathVariable long commentId) { service.deleteComment(AuthContext.getMiniUserId(), commentId); return ApiResponse.success(true); }
    @PostMapping("/mini/posts/{postId}/likes")
    public ApiResponse<Integer> like(@PathVariable long postId, @RequestParam boolean liked) { return ApiResponse.success(service.toggleLike(AuthContext.getMiniUserId(), postId, liked)); }
    @PostMapping("/mini/posts/blocks/{userId}")
    public ApiResponse<Boolean> block(@PathVariable long userId) { service.block(AuthContext.getMiniUserId(), userId); return ApiResponse.success(true); }
    @DeleteMapping("/mini/posts/blocks/{userId}")
    public ApiResponse<Boolean> unblock(@PathVariable long userId) { service.unblock(AuthContext.getMiniUserId(), userId); return ApiResponse.success(true); }
    @GetMapping("/mini/posts/blocks")
    public ApiResponse<List<CommunityUserVO>> blockedUsers() {
        return ApiResponse.success(service.blockedUsers(AuthContext.getMiniUserId()));
    }
    @PutMapping("/mini/posts/{postId}/comments-enabled")
    public ApiResponse<Boolean> commentsEnabled(@PathVariable long postId, @RequestParam boolean enabled) {
        service.setCommentsEnabled(AuthContext.getMiniUserId(), postId, enabled);
        return ApiResponse.success(true);
    }

    @GetMapping("/platform/content-moderation/comments/list")
    public ApiResponse<List<VenueReviewModerationVO>> queue() {
        Principals.platform();
        return ApiResponse.success(service.listModerationQueue());
    }

    @PostMapping("/platform/content-moderation/comments/{commentId}")
    public ApiResponse<Boolean> moderate(@PathVariable long commentId, @RequestBody ContentModerationRequest request) {
        service.moderate(Principals.platform().getAccountId(), commentId,
                request == null || request.getRevisionId() == null ? 0 : request.getRevisionId(),
                request == null ? null : request.getAction(), request == null ? null : request.getReason());
        return ApiResponse.success(true);
    }
}
