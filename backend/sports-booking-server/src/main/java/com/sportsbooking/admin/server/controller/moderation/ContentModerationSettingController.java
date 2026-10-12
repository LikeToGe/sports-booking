package com.sportsbooking.admin.server.controller.moderation;

import com.sportsbooking.admin.biz.pojo.request.ContentModerationSettingRequest;
import com.sportsbooking.admin.biz.pojo.vo.ContentModerationSettingVO;
import com.sportsbooking.admin.biz.service.ContentModerationSettingService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.support.Principals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台内容审核开关管理入口。 */
@RestController
@RequestMapping("/platform/content-settings")
public class ContentModerationSettingController {
    @Autowired
    private ContentModerationSettingService settingService;

    @GetMapping("/get")
    public ApiResponse<ContentModerationSettingVO> get() {
        Principals.platform();
        return ApiResponse.success(settingService.get());
    }

    @PutMapping("/update")
    public ApiResponse<ContentModerationSettingVO> update(@RequestBody ContentModerationSettingRequest request) {
        Principals.platform();
        return ApiResponse.success(settingService.update(request));
    }
}
