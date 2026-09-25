package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.RefundStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Data;

/** 平台异常退款列表查询投影。 */
@Data
public class PlatformRefundBO {
    private Long id;
    private String businessNo;
    private Long bookingId;
    private String bookingNo;
    private Long venueId;
    private String venueName;
    private Integer amountFen;
    private RefundStatus status;
    private Integer retryCount;
    private Instant nextRetryAt;
    private String lastFailureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
