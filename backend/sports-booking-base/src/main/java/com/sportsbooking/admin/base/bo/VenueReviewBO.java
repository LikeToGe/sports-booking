package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.Data;

/** 场馆公开评价列表的数据库投影，不包含真实用户资料。 */
@Data
public class VenueReviewBO {
    private Long id;
    private Long revisionId;
    private Long venueId;
    private Long sportTypeId;
    private String sportName;
    private String visitMonth;
    private String body;
    private Integer overallScore;
    private Integer courtScore;
    private Integer environmentScore;
    private Integer serviceScore;
    private Boolean anonymous;
    private LocalDateTime createdAt;
    private Long replyId;
    private String replyBody;
    private String replyContentStatus;
    private String replyModerationStatus;
    private LocalDateTime replyCreatedAt;
}
