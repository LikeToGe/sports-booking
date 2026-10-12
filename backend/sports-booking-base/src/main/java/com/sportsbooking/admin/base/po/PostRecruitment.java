package com.sportsbooking.admin.base.po;

import java.time.LocalDateTime;

/** 动态找搭子招募。 */
public class PostRecruitment {
    public Long id; public Long postId; public Long bookingId; public String level;
    public Integer maxMembers; public String feeMode; public Integer aaAmountFen; public String description;
    public String status; public LocalDateTime createdAt; public LocalDateTime updatedAt;
}
