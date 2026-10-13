package com.sportsbooking.admin.base.bo;

import java.time.Instant;
import lombok.Data;

/** 找搭子公开发现列表查询投影。 */
@Data
public class RecruitmentDiscoveryBO {
    private Long recruitmentId;
    private Long postId;
    private Long sportTypeId;
    private String sportName;
    private Long venueId;
    private String venueName;
    private Instant startAt;
    private Instant endAt;
    private String level;
    private String levelTag;
    private Integer maxMembers;
    private Integer approvedCount;
    private String feeMode;
    private Integer aaAmountFen;
    private String description;
    private String status;
}
