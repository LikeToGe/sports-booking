package com.sportsbooking.admin.server.controller.notification;

import com.sportsbooking.admin.biz.service.ContentMessageService;
import com.sportsbooking.admin.biz.pojo.vo.ContentNotificationVO;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 小程序站内消息列表与已读。 */
@RestController
@RequestMapping({"/mini/content/notifications", "/mini/notifications"})
public class NotificationController {
    @Autowired
    private ContentMessageService service;
    @GetMapping
    public ApiResponse<List<ContentNotificationVO>> list() {
        return ApiResponse.success(service.list(AuthContext.getMiniUserId()));
    }

    /** 统一消息中心别名，提醒消息与社区消息共享同一列表。 */
    @GetMapping("/all")
    public ApiResponse<List<ContentNotificationVO>> all() {
        return ApiResponse.success(service.list(AuthContext.getMiniUserId()));
    }
    @PostMapping("/{id}/read")
    public ApiResponse<Boolean> read(@PathVariable long id) {
        service.markRead(AuthContext.getMiniUserId(), id);
        return ApiResponse.success(true);
    }
}
