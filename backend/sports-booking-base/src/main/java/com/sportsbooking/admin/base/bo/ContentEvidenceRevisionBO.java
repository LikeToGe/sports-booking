package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.Data;

/** 平台案件证据的保留版本，不参与公开内容查询。 */
@Data
public class ContentEvidenceRevisionBO {
    private Long revisionId;
    private Integer revisionNo;
    private String body;
    private String moderationStatus;
    private LocalDateTime createdAt;
    private Integer overallScore;
    private Integer courtScore;
    private Integer environmentScore;
    private Integer serviceScore;
    private Boolean anonymous;
    private String tags;
    private Integer purgedMediaCount;
}
