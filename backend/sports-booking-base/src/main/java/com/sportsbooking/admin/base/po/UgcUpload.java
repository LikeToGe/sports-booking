package com.sportsbooking.admin.base.po;

import java.time.LocalDateTime;

/** 用户媒体上传暂存记录。 */
public class UgcUpload {
    public Long id;
    public Long ownerUserId;
    public String mediaType;
    public String objectKey;
    public String mimeType;
    public Long sizeBytes;
    public String sha256;
    public Integer durationSeconds;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime expiresAt;
}
