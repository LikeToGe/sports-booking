package com.sportsbooking.admin.server.controller.moderation;

import com.sportsbooking.admin.biz.service.UserActionEventService;
import com.sportsbooking.admin.biz.pojo.vo.UserActionEventVO;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import com.sportsbooking.admin.server.support.Principals;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserActionEventController {
    @Autowired
    private UserActionEventService service;
    @PostMapping("/mini/content/events")
    public ApiResponse<Boolean> record(@RequestParam String eventType, @RequestParam String targetType,
                                       @RequestParam long targetId) {
        service.navigation(AuthContext.getMiniUserId(),eventType,targetType,targetId);
        return ApiResponse.success(true);
    }
    @GetMapping("/platform/content-events")
    public ApiResponse<List<UserActionEventVO>> list() {
        Principals.platform();
        return ApiResponse.success(service.recent());
    }
}
