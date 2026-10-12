package com.sportsbooking.admin.server.controller.auth;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.biz.constants.AuthConstants;
import com.sportsbooking.admin.biz.pojo.request.AdminLoginRequest;
import com.sportsbooking.admin.biz.pojo.request.MiniLoginRequest;
import com.sportsbooking.admin.biz.pojo.request.UserProfileUpdateRequest;
import com.sportsbooking.admin.biz.service.AuthService;
import com.sportsbooking.admin.biz.service.SessionService;
import com.sportsbooking.admin.biz.service.WebLoginTicketService;
import com.sportsbooking.admin.biz.pojo.vo.ScanTicketVO;
import com.sportsbooking.admin.biz.pojo.vo.AdminLoginVO;
import com.sportsbooking.admin.biz.pojo.vo.MiniLoginVO;
import com.sportsbooking.admin.biz.pojo.vo.UserProfileVO;
import com.sportsbooking.admin.server.context.AuthContext;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 功能描述: 认证协议入口，只负责请求转换、会话令牌传递和统一响应包装。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private WebLoginTicketService webLoginTicketService;

    /**
     * 功能描述: 后台账号登录。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 登录请求
     * @return 登录结果
     * @author gjm
     */
    @PostMapping("/admin/login")
    public ApiResponse<AdminLoginVO> loginAdmin(@RequestBody AdminLoginRequest body) {
        return ApiResponse.success(authService.loginAdmin(body.getUsername(), body.getPassword()));
    }

    @PostMapping("/admin/web-login-tickets")
    public ApiResponse<ScanTicketVO> createWebLoginTicket() {
        return ApiResponse.success(webLoginTicketService.create());
    }

    @GetMapping("/admin/web-login-tickets/{ticket}")
    public ApiResponse<ScanTicketVO> getWebLoginTicket(@PathVariable String ticket) {
        return ApiResponse.success(webLoginTicketService.get(ticket));
    }

    @PostMapping("/admin/web-login-tickets/{ticket}/consume")
    public ApiResponse<AdminLoginVO> consumeWebLoginTicket(@PathVariable String ticket) {
        return ApiResponse.success(webLoginTicketService.consume(ticket));
    }


    /**
     * 功能描述: 删除当前后台会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @param token 后台会话令牌
     * @return 是否成功
     * @author gjm
    */
    @PostMapping("/admin/logout")
    public ApiResponse<Boolean> logoutAdmin(
            @Parameter(description = "后台会话令牌")
            @RequestHeader(AuthConstants.ADMIN_TOKEN_HEADER) String token) {
        sessionService.deleteAdminSession(token);
        return ApiResponse.success(true);
    }

    /**
     * 功能描述: 使用本地模拟微信 code 登录小程序。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 模拟登录请求
     * @return 小程序登录结果
     * @author gjm
     */
    @PostMapping("/mini/mock-login")
    public ApiResponse<MiniLoginVO> loginMini(@RequestBody MiniLoginRequest body) {
        return ApiResponse.success(authService.loginMini(body.getCode()));
    }

    /**
     * 功能描述: 删除当前小程序会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @param token 小程序会话令牌
     * @return 是否成功
     * @author gjm
    */
    @PostMapping("/mini/logout")
    public ApiResponse<Boolean> logoutMini(
            @Parameter(description = "小程序会话令牌")
            @RequestHeader(AuthConstants.MINI_TOKEN_HEADER) String token) {
        sessionService.deleteMiniSession(token);
        return ApiResponse.success(true);
    }

    /**
     * 功能描述: 查询当前小程序用户资料。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 用户资料
     * @author gjm
    */
    @GetMapping("/mini/profile/detail")
    public ApiResponse<UserProfileVO> getMiniProfile() {
        return ApiResponse.success(authService.getMiniProfile(AuthContext.getMiniUserId()));
    }

    /**
     * 功能描述: 更新当前小程序用户的预约联系人资料。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 联系人资料
     * @return 更新后的用户资料
     * @author gjm
    */
    @PutMapping("/mini/profile/update")
    public ApiResponse<UserProfileVO> updateMiniProfile(@RequestBody UserProfileUpdateRequest body) {
        return ApiResponse.success(authService.updateMiniProfile(
                AuthContext.getMiniUserId(), body.getContactName(), body.getPhone()
        ));
    }

    @PostMapping("/mini/close-account")
    public ApiResponse<Boolean> closeMiniAccount(@RequestHeader(AuthConstants.MINI_TOKEN_HEADER) String token) {
        authService.closeMiniAccount(AuthContext.getMiniUserId());
        sessionService.deleteMiniSession(token);
        return ApiResponse.success(true);
    }
}
