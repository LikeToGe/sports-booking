package com.sportsbooking.admin.server.controller.moderation;

import com.sportsbooking.admin.biz.pojo.request.ContentAgreementAcceptRequest;
import com.sportsbooking.admin.biz.pojo.request.ContentAgreementSettingRequest;
import com.sportsbooking.admin.biz.pojo.vo.ContentAgreementVO;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import com.sportsbooking.admin.server.support.Principals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台维护协议，用户读取并接受当前版本。 */
@RestController
@RequestMapping
public class ContentAgreementController {
    @Autowired
    private ContentAgreementService service;

    @GetMapping("/platform/content-agreement")
    public ApiResponse<ContentAgreementVO> getPlatform() {
        Principals.platform();
        return ApiResponse.success(service.getForPlatform());
    }

    @PutMapping("/platform/content-agreement")
    public ApiResponse<ContentAgreementVO> update(@RequestBody ContentAgreementSettingRequest request) {
        Principals.platform();
        return ApiResponse.success(service.update(request));
    }

    @GetMapping("/mini/content/agreement")
    public ApiResponse<ContentAgreementVO> getMine() {
        return ApiResponse.success(service.getForUser(AuthContext.getMiniUserId()));
    }

    @PostMapping("/mini/content/agreement")
    public ApiResponse<ContentAgreementVO> accept(@RequestBody ContentAgreementAcceptRequest request) {
        return ApiResponse.success(service.accept(AuthContext.getMiniUserId(), request));
    }
}
