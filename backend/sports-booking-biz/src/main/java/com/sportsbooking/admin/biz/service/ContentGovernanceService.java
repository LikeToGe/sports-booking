package com.sportsbooking.admin.biz.service;

import java.util.List;
import com.sportsbooking.admin.biz.pojo.vo.ContentGovernanceVO;
import com.sportsbooking.admin.biz.pojo.vo.ContentCaseEvidenceVO;

/** 举报、一次申诉和账号内容限权。 */
public interface ContentGovernanceService {
    /** 平台案件详情：提交证据与当前状态分别返回。 */
    ContentCaseEvidenceVO evidence(String caseType, long id);
    void requireEnabled();
    void report(long userId, String targetType, long targetId, String reasonCode, String description);
    void appeal(long userId, long moderationLogId, String body);
    List<ContentGovernanceVO> reports(String status);
    List<ContentGovernanceVO> appeals(String status);
    List<ContentGovernanceVO> myReports(long userId);
    List<ContentGovernanceVO> myAppeals(long userId);
    List<ContentGovernanceVO> myDecisions(long userId);
    void releaseHold(String targetType, long targetId);
    void resolveReport(long adminId, long id, String status, String resolution);
    void decideAppeal(long adminId, long id, String status, String reason);
    void restrict(long adminId, long userId, String capability, int days, String reason);
    void requireAllowed(long userId, String capability);
}
