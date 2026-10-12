package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;

/** 内容版本的媒体投影，不直接作为 API 返回值。 */
public class UgcMediaBO {
    public Long id;
    public Long revisionId;
    public Integer position;
    public String mediaType;
    public String storageProvider;
    public String objectKey;
    public String mimeType;
    public Long sizeBytes;
    public String sha256;
    public Integer durationSeconds;
    public String moderationStatus;
    public String cleanupStatus;
    public LocalDateTime purgeAfter;
    public Boolean legalHold;
}
