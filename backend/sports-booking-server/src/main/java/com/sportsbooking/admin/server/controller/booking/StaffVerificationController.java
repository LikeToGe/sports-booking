package com.sportsbooking.admin.server.controller.booking;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.biz.pojo.request.VerificationRequest;
import com.sportsbooking.admin.biz.service.VerificationService;
import com.sportsbooking.admin.biz.pojo.vo.StaffVerificationRecordVO;
import com.sportsbooking.admin.biz.pojo.vo.VerificationResultVO;
import com.sportsbooking.admin.server.context.AuthContext;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.biz.service.PermissionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 功能描述: 小程序场馆员工核销和本人记录协议入口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@RestController
@RequestMapping("/staff")
public class StaffVerificationController {
    @Autowired
    private VerificationService verificationService;
    @Autowired
    private PermissionService permissionService;

    /**
     * 功能描述: 使用二维码或短码核销预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 核销请求
     * @return 不含联系方式的核销结果
     * @author gjm
    */
    @PostMapping("/verifications/verify")
    public ApiResponse<VerificationResultVO> verify(
            @RequestParam long venueId, @RequestBody VerificationRequest body) {
        permissionService.requireEmployee(AuthContext.getMiniUserId(), venueId, VenuePermission.BOOKING_VERIFY);
        return ApiResponse.success(VerificationResultVO.from(verificationService.verify(
                AuthContext.getMiniUserId(), venueId, body.getCredential(), body.getMethod()
        )));
    }

    /**
     * 功能描述: 查询当前员工本人的核销记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 本人核销记录
     * @author gjm
    */
    @GetMapping("/verifications/records")
    public ApiResponse<List<StaffVerificationRecordVO>> listRecords(@RequestParam long venueId) {
        permissionService.requireEmployee(AuthContext.getMiniUserId(), venueId, VenuePermission.BOOKING_VERIFY);
        return ApiResponse.success(verificationService.listStaffRecords(AuthContext.getMiniUserId(), venueId));
    }
}
