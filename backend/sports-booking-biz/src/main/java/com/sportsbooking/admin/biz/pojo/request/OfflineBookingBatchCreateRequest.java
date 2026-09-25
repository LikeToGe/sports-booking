package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 固定场地和时段的多日期线下预约请求。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@Schema(description = "多日期线下预约创建请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineBookingBatchCreateRequest {
    /** 作为场地、运动和时段模板的具体场次 ID。 */
    @Schema(description = "模板具体场次 ID")
    @ApiModelProperty(value = "模板具体场次 ID")
    private long sessionSlotId;

    /** 待预约日期，服务端去重后整批处理。 */
    @Schema(description = "待预约日期")
    @ApiModelProperty(value = "待预约日期")
    private List<LocalDate> dates;

    /** 联系人姓名。 */
    @Schema(description = "联系人姓名")
    @ApiModelProperty(value = "联系人姓名")
    private String contactName;

    /** 联系人手机号。 */
    @Schema(description = "联系人手机号")
    @ApiModelProperty(value = "联系人手机号")
    private String contactPhone;

    /** 创建时的线下登记收款状态。 */
    @Schema(description = "线下登记收款状态，仅允许 UNPAID 或 PAID")
    @ApiModelProperty(value = "线下登记收款状态，仅允许 UNPAID 或 PAID")
    private BookingPaymentStatus paymentStatus;

    /** 可选的本场馆顾客档案 ID。 */
    @Schema(description = "可选的本场馆顾客档案 ID")
    @ApiModelProperty(value = "可选的本场馆顾客档案 ID")
    private Long venueCustomerId;
}
