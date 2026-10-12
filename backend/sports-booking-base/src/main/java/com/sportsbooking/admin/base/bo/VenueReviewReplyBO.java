package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.Data;

/** 评价公开列表中的官方回复投影。 */
@Data
public class VenueReviewReplyBO {
    private Long replyId;
    private String body;
    private String contentStatus;
    private String moderationStatus;
    private LocalDateTime createdAt;
}
