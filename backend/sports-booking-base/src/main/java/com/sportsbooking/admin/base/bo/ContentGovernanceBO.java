package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.Data;

/** 治理记录查询投影。 */
@Data
public class ContentGovernanceBO {
    private Long id;
    private Long userId;
    private String targetType;
    private Long targetId;
    private Long moderationLogId;
    private Long revisionId;
    private String action;
    private String reason;
    private String description;
    private String status;
    private String decisionReason;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
}
