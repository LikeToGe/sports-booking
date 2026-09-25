package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 场馆创建电话或到店预约请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "场馆线下预约创建请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineBookingCreateRequest {

    /**
     * 具体场次 ID
     */
    @Schema(description = "具体场次 ID")
    @ApiModelProperty(value = "具体场次 ID")
    private long sessionSlotId;

    /**
     * 联系人姓名
     */
    @Schema(description = "联系人姓名")
    @ApiModelProperty(value = "联系人姓名")
    private String contactName;

    /**
     * 联系人手机号
     */
    @Schema(description = "联系人手机号")
    @ApiModelProperty(value = "联系人手机号")
    private String contactPhone;

    /**
     * 线下登记收款状态
     */
    @Schema(description = "线下登记收款状态，仅允许 UNPAID 或 PAID")
    @ApiModelProperty(value = "线下登记收款状态，仅允许 UNPAID 或 PAID")
    private BookingPaymentStatus paymentStatus;

    /**
     * 可选的本场馆顾客档案 ID
     */
    @Schema(description = "可选的本场馆顾客档案 ID")
    @ApiModelProperty(value = "可选的本场馆顾客档案 ID")
    private Long venueCustomerId;
}
