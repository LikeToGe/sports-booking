package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.VenueBookingBO;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 场馆后台预约列表项。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueBookingVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 预约编号
     */
    @Schema(description = "预约编号")
    @ApiModelProperty(value = "预约编号")
    private String bookingNo;

    /**
     * 场馆名称
     */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String venueName;

    /**
     * 预约来源
     */
    @Schema(description = "预约来源")
    @ApiModelProperty(value = "预约来源")
    private BookingSource source;

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
     * 场地名称
     */
    @Schema(description = "场地名称")
    @ApiModelProperty(value = "场地名称")
    private String courtName;

    /**
     * 运动名称
     */
    @Schema(description = "运动名称")
    @ApiModelProperty(value = "运动名称")
    private String sportName;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    @ApiModelProperty(value = "开始时间")
    private String startAt;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    @ApiModelProperty(value = "结束时间")
    private String endAt;

    /**
     * 金额，单位为分
     */
    @Schema(description = "金额，单位为分")
    @ApiModelProperty(value = "金额，单位为分")
    private Integer amountFen;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private BookingStatus status;

    /**
     * 支付状态
     */
    @Schema(description = "支付状态")
    @ApiModelProperty(value = "支付状态")
    private BookingPaymentStatus paymentStatus;

    /**
     * 关联的场馆顾客档案 ID
     */
    @Schema(description = "关联的场馆顾客档案 ID")
    @ApiModelProperty(value = "关联的场馆顾客档案 ID")
    private Long venueCustomerId;

    public static VenueBookingVO from(VenueBookingBO source) {
        return new VenueBookingVO(source.getId(), source.getBookingNo(), source.getVenueName(),
                source.getSource(), source.getContactName(), source.getContactPhone(), source.getCourtName(),
                source.getSportName(), source.getStartAt(), source.getEndAt(), source.getAmountFen(),
                source.getStatus(), source.getPaymentStatus(), source.getVenueCustomerId());
    }
}
