package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.Data;

/** 平台内容处置列表，不含预约联系人。 */
@Data
public class VenueReviewModerationBO {
    private String contentType;
    private Long replyId;
    private Long reviewId;
    private Long revisionId;
    private Long venueId;
    private String venueName;
    private String body;
    private String publicBody;
    private String contentStatus;
    private String moderationStatus;
    private Integer overallScore;
    private LocalDateTime submittedAt;
}
