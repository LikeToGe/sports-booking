package com.sportsbooking.admin.base.po;

import java.time.LocalDateTime;

/** 找搭子参与关系。 */
public class PostRecruitmentMember {
    public Long recruitmentId; public Long userId; public String status;
    public LocalDateTime appliedAt; public LocalDateTime decidedAt;
}
