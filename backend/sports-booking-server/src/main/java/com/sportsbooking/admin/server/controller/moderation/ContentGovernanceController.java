package com.sportsbooking.admin.server.controller.moderation;

import com.sportsbooking.admin.biz.service.ContentGovernanceService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import com.sportsbooking.admin.server.support.Principals;
import java.util.List;
import com.sportsbooking.admin.biz.pojo.vo.ContentGovernanceVO;
import com.sportsbooking.admin.biz.pojo.vo.ContentCaseEvidenceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户举报/申诉和平台治理操作。 */
@RestController
public class ContentGovernanceController {
    @Autowired
    private ContentGovernanceService service;

    @PostMapping("/mini/content/reports")
    public ApiResponse<Boolean> report(@RequestParam String targetType, @RequestParam long targetId,
            @RequestParam String reasonCode, @RequestParam(required = false) String description) {
        service.report(AuthContext.getMiniUserId(), targetType, targetId, reasonCode, description);
        return ApiResponse.success(true);
    }

    @PostMapping("/mini/content/appeals")
    public ApiResponse<Boolean> appeal(@RequestParam long moderationLogId, @RequestParam String body) {
        service.appeal(AuthContext.getMiniUserId(), moderationLogId, body);
        return ApiResponse.success(true);
    }

    @GetMapping("/platform/content-governance/reports")
    public ApiResponse<List<ContentGovernanceVO>> reports(@RequestParam(required = false) String status) {
        Principals.platform();
        return ApiResponse.success(service.reports(status));
    }

    @GetMapping("/platform/content-governance/appeals")
    public ApiResponse<List<ContentGovernanceVO>> appeals(@RequestParam(required = false) String status) {
        Principals.platform();
        return ApiResponse.success(service.appeals(status));
    }

    @GetMapping("/platform/content-governance/{caseType}/{id}/evidence")
    public ApiResponse<ContentCaseEvidenceVO> evidence(
            @PathVariable String caseType, @PathVariable long id) {
        Principals.platform();
        return ApiResponse.success(service.evidence(caseType, id));
    }

    @PostMapping("/platform/content-governance/reports/{id}/resolve")
    public ApiResponse<Boolean> resolve(@PathVariable long id, @RequestParam String status,
                                        @RequestParam String resolution) {
        service.resolveReport(Principals.platform().getAccountId(), id, status, resolution);
        return ApiResponse.success(true);
    }

    @PostMapping("/platform/content-governance/appeals/{id}/decide")
    public ApiResponse<Boolean> decide(@PathVariable long id, @RequestParam String status, @RequestParam String reason) {
        service.decideAppeal(Principals.platform().getAccountId(), id, status, reason);
        return ApiResponse.success(true);
    }

    @PostMapping("/platform/content-governance/restrictions")
    public ApiResponse<Boolean> restrict(@RequestParam long userId, @RequestParam String capability,
                                         @RequestParam int days, @RequestParam String reason) {
        service.restrict(Principals.platform().getAccountId(), userId, capability, days, reason);
        return ApiResponse.success(true);
    }

    @GetMapping("/mini/content/reports")
    public ApiResponse<List<ContentGovernanceVO>> myReports() {
        return ApiResponse.success(service.myReports(AuthContext.getMiniUserId()));
    }
    @GetMapping("/mini/content/appeals")
    public ApiResponse<List<ContentGovernanceVO>> myAppeals() {
        return ApiResponse.success(service.myAppeals(AuthContext.getMiniUserId()));
    }
    @GetMapping("/mini/content/decisions")
    public ApiResponse<List<ContentGovernanceVO>> myDecisions() {
        return ApiResponse.success(service.myDecisions(AuthContext.getMiniUserId()));
    }
    @PostMapping("/platform/content-governance/targets/{type}/{id}/release-hold")
    public ApiResponse<Boolean> releaseHold(@PathVariable String type, @PathVariable long id) {
        Principals.platform();
        service.releaseHold(type, id);
        return ApiResponse.success(true);
    }
}
