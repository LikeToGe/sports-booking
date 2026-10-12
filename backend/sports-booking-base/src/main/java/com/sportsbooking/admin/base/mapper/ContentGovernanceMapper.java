package com.sportsbooking.admin.base.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.sportsbooking.admin.base.bo.ContentGovernanceBO;
import com.sportsbooking.admin.base.bo.ContentTargetBO;
import com.sportsbooking.admin.base.po.ContentRestriction;
import org.apache.ibatis.annotations.Param;

/** 举报、申诉和用户内容限权查询。 */
public interface ContentGovernanceMapper {
    int insertReport(@Param("userId") long userId, @Param("targetType") String targetType, @Param("targetId") long targetId,
                     @Param("reasonCode") String reasonCode, @Param("description") String description);
    List<ContentGovernanceBO> selectReports(@Param("status") String status, @Param("userId") Long userId);
    int resolveReport(@Param("id") long id, @Param("adminId") long adminId, @Param("status") String status, @Param("resolution") String resolution);
    int insertAppeal(@Param("logId") long logId, @Param("userId") long userId, @Param("body") String body);
    List<ContentGovernanceBO> selectAppeals(@Param("status") String status, @Param("userId") Long userId);
    int decideAppeal(@Param("id") long id, @Param("adminId") long adminId, @Param("status") String status, @Param("reason") String reason);
    int insertRestriction(@Param("restriction") ContentRestriction restriction);
    int insertRestrictionLog(@Param("restriction") ContentRestriction restriction);
    int revokeRestriction(@Param("userId") long userId, @Param("restrictionId") long restrictionId);
    Integer restricted(@Param("userId") long userId, @Param("capability") String capability);
    ContentTargetBO selectTargetForUpdate(@Param("targetType") String targetType, @Param("targetId") long targetId);
    int setLegalHold(@Param("targetType") String targetType, @Param("targetId") long targetId, @Param("hold") boolean hold);
    ContentGovernanceBO selectLog(@Param("logId") long logId);
    Long selectLatestLogId(@Param("targetType") String targetType, @Param("targetId") long targetId);
    ContentGovernanceBO selectReportForUpdate(@Param("id") long id);
    ContentGovernanceBO selectAppealForUpdate(@Param("id") long id);
    List<ContentGovernanceBO> selectUserDecisions(@Param("userId") long userId);
    Integer hasPendingEvidence(@Param("targetType") String targetType, @Param("targetId") long targetId);
    int restoreTarget(@Param("targetType") String targetType, @Param("targetId") long targetId);
    int insertReversalLog(@Param("targetType") String targetType, @Param("targetId") long targetId,
            @Param("revisionId") Long revisionId, @Param("adminId") long adminId, @Param("reason") String reason);
    int restoreRevision(@Param("targetType") String targetType, @Param("revisionId") long revisionId);
}
