package com.sportsbooking.admin.server.controller.notification;

import com.sportsbooking.admin.biz.pojo.request.ReminderSubscriptionRequest;
import com.sportsbooking.admin.biz.pojo.vo.ReminderSubscriptionVO;
import com.sportsbooking.admin.biz.service.ReminderService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 小程序提醒登记及撤回入口。 */
@RestController
@RequestMapping({"/mini/reminder-subscriptions", "/mini/reminders"})
public class ReminderController {
    @Autowired
    private ReminderService reminders;

    @GetMapping
    public ApiResponse<List<ReminderSubscriptionVO>> list() {
        return ApiResponse.success(reminders.listSubscriptions(AuthContext.getMiniUserId()));
    }

    @PostMapping
    public ApiResponse<ReminderSubscriptionVO> subscribe(@RequestBody ReminderSubscriptionRequest request) {
        return ApiResponse.success(ReminderSubscriptionVO.from(
                reminders.subscribe(AuthContext.getMiniUserId(), request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> cancel(@PathVariable long id) {
        reminders.cancelSubscription(AuthContext.getMiniUserId(), id);
        return ApiResponse.success(true);
    }
}
