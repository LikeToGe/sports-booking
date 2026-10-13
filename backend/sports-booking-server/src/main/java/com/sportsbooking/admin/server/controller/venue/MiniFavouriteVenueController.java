package com.sportsbooking.admin.server.controller.venue;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.biz.pojo.vo.FavouriteVenueVO;
import com.sportsbooking.admin.biz.service.FavouriteVenueService;
import com.sportsbooking.admin.server.context.AuthContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 小程序用户收藏和最近预约场馆入口。 */
@RestController
@RequestMapping("/mini")
public class MiniFavouriteVenueController {
    @Autowired
    private FavouriteVenueService service;

    @GetMapping("/favourite-venues")
    public ApiResponse<List<FavouriteVenueVO>> favourites() {
        return ApiResponse.success(service.favourites(AuthContext.getMiniUserId()));
    }

    @GetMapping("/recent-venues")
    public ApiResponse<List<FavouriteVenueVO>> recent() {
        return ApiResponse.success(service.recent(AuthContext.getMiniUserId()));
    }

    @PostMapping("/favourite-venues/{venueId}")
    public ApiResponse<Boolean> add(@PathVariable long venueId) {
        service.add(AuthContext.getMiniUserId(), venueId);
        return ApiResponse.success(true);
    }

    @DeleteMapping("/favourite-venues/{venueId}")
    public ApiResponse<Boolean> remove(@PathVariable long venueId) {
        service.remove(AuthContext.getMiniUserId(), venueId);
        return ApiResponse.success(true);
    }
}
