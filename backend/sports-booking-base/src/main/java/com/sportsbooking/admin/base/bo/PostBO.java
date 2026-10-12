package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.Data;

/** 动态公开信息流查询投影。 */
@Data
public class PostBO {
    private Long id;
    private Long revisionId;
    private Long authorId;
    private String authorType;
    private String authorName;
    private String body;
    private Long sportTypeId;
    private String sportName;
    private Long venueId;
    private String venueName;
    private Boolean isOfficial;
    private Boolean isPinned;
    private Boolean commentsEnabled;
    private Boolean isMine;
    private String contentStatus;
    private String moderationStatus;
    private LocalDateTime createdAt;
    private Long likeCount;
    private Integer liked;
    private Long bookingId;
}
