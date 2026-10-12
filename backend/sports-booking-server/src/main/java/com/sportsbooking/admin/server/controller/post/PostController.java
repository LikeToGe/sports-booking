package com.sportsbooking.admin.server.controller.post;

import com.sportsbooking.admin.biz.pojo.request.PostRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostVO;
import com.sportsbooking.admin.biz.pojo.vo.CommunityUserVO;
import com.sportsbooking.admin.biz.service.PostService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 小程序动态信息流和用户纯文字动态操作入口。 */
@RestController
@RequestMapping("/mini/posts")
public class PostController {
    @Autowired
    private PostService postService;

    @GetMapping("/list")
    public ApiResponse<List<PostVO>> list(@RequestParam(required = false) Long sportTypeId,
                                          @RequestParam(required = false) Long venueId) {
        return ApiResponse.success(postService.listPublic(AuthContext.getMiniUserId(), sportTypeId, venueId));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostVO> get(@PathVariable long postId) {
        return ApiResponse.success(postService.getPublic(AuthContext.getMiniUserId(), postId));
    }
    @GetMapping("/mine")
    public ApiResponse<List<PostVO>> mine() {
        return ApiResponse.success(postService.listMine(AuthContext.getMiniUserId()));
    }
    @GetMapping("/users/{authorId}")
    public ApiResponse<CommunityUserVO> user(@PathVariable long authorId) {
        return ApiResponse.success(postService.userProfile(AuthContext.getMiniUserId(),authorId));
    }

    @PostMapping("/create")
    public ApiResponse<PostVO> create(@RequestBody PostRequest request) {
        return ApiResponse.success(postService.create(AuthContext.getMiniUserId(), request));
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostVO> update(@PathVariable long postId, @RequestBody PostRequest request) {
        return ApiResponse.success(postService.update(AuthContext.getMiniUserId(), postId, request));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Boolean> delete(@PathVariable long postId) {
        postService.delete(AuthContext.getMiniUserId(), postId);
        return ApiResponse.success(true);
    }
}
