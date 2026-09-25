package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 线下预约登记收款状态修改请求。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@Schema(description = "线下预约登记收款状态修改请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineBookingPaymentStatusUpdateRequest {
    /** 目标登记收款状态。 */
    @Schema(description = "目标登记收款状态")
    @ApiModelProperty(value = "目标登记收款状态")
    private BookingPaymentStatus paymentStatus;

    /** 人工修改原因。 */
    @Schema(description = "人工修改原因")
    @ApiModelProperty(value = "人工修改原因")
    private String reason;
}
