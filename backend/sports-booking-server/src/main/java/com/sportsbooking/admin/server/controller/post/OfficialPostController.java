package com.sportsbooking.admin.server.controller.post;

import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.biz.pojo.request.PostRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostVO;
import com.sportsbooking.admin.biz.service.PostService;
import com.sportsbooking.admin.biz.service.VenueCatalogService;
import com.sportsbooking.admin.biz.pojo.vo.SportTypeVO;
import java.util.List;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.support.Principals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 官方身份来自认证角色，不能由客户端伪造。 */
@RestController
public class OfficialPostController {
    @Autowired
    private PostService service;
    @Autowired
    private VenueCatalogService catalog;
    @GetMapping("/venue-admin/posts/sports")
    public ApiResponse<List<SportTypeVO>> sports() {
        Principals.venue(VenuePermission.POST_PUBLISH);
        return ApiResponse.success(catalog.listPlatformSports().stream().map(SportTypeVO::from).toList());
    }
    @PostMapping("/platform/posts")
    public ApiResponse<PostVO> platform(@RequestBody PostRequest request) {
        return ApiResponse.success(service.createOfficial("PLATFORM",Principals.platform().getAccountId(),request));
    }
    @PostMapping("/venue-admin/posts")
    public ApiResponse<PostVO> venue(@RequestBody PostRequest request) {
        return ApiResponse.success(service.createOfficial("VENUE",Principals.venue(VenuePermission.POST_PUBLISH).getVenueId(),request));
    }
    @PostMapping("/platform/posts/{postId}/pin")
    public ApiResponse<Boolean> pin(@PathVariable long postId, @RequestParam boolean pinned) {
        Principals.platform();
        service.setPinned(postId,pinned);
        return ApiResponse.success(true);
    }
}
