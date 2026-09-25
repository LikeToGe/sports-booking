package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.PlatformRefundBO;
import com.sportsbooking.admin.base.enums.RefundStatus;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 平台异常退款接口模型。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PlatformRefundVO {
    @Schema(description = "退款 ID") @ApiModelProperty("退款 ID") private Long id;
    @Schema(description = "退款业务号") @ApiModelProperty("退款业务号") private String businessNo;
    @Schema(description = "预约 ID") @ApiModelProperty("预约 ID") private Long bookingId;
    @Schema(description = "预约单号") @ApiModelProperty("预约单号") private String bookingNo;
    @Schema(description = "场馆 ID") @ApiModelProperty("场馆 ID") private Long venueId;
    @Schema(description = "场馆名称") @ApiModelProperty("场馆名称") private String venueName;
    @Schema(description = "金额，单位分") @ApiModelProperty("金额，单位分") private Integer amountFen;
    @Schema(description = "退款状态") @ApiModelProperty("退款状态") private RefundStatus status;
    @Schema(description = "重试次数") @ApiModelProperty("重试次数") private Integer retryCount;
    @Schema(description = "下次重试时间") @ApiModelProperty("下次重试时间") private Instant nextRetryAt;
    @Schema(description = "最近失败原因") @ApiModelProperty("最近失败原因") private String lastFailureReason;
    @Schema(description = "创建时间") @ApiModelProperty("创建时间") private LocalDateTime createdAt;
    @Schema(description = "更新时间") @ApiModelProperty("更新时间") private LocalDateTime updatedAt;

    public static PlatformRefundVO from(PlatformRefundBO row) {
        return new PlatformRefundVO(row.getId(), row.getBusinessNo(), row.getBookingId(), row.getBookingNo(),
                row.getVenueId(), row.getVenueName(), row.getAmountFen(), row.getStatus(), row.getRetryCount(),
                row.getNextRetryAt(), row.getLastFailureReason(), row.getCreatedAt(), row.getUpdatedAt());
    }
}
