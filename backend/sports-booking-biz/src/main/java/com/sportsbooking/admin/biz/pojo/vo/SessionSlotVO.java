package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.SessionSlotBO;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 场馆后台某日具体场次列表项。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSlotVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 场地 ID
     */
    @Schema(description = "场地 ID")
    @ApiModelProperty(value = "场地 ID")
    private Long courtId;

    /**
     * 场地名称
     */
    @Schema(description = "场地名称")
    @ApiModelProperty(value = "场地名称")
    private String courtName;

    /**
     * 运动类型 ID
     */
    @Schema(description = "运动类型 ID")
    @ApiModelProperty(value = "运动类型 ID")
    private Long sportTypeId;

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
     * 价格，单位为分
     */
    @Schema(description = "价格，单位为分")
    @ApiModelProperty(value = "价格，单位为分")
    private Integer priceFen;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private SessionSlotStatus status;

    /**
     * 当前关联预约 ID
     */
    @Schema(description = "当前关联预约 ID")
    @ApiModelProperty(value = "当前关联预约 ID")
    private Long bookingId;

    /**
     * 当前关联预约来源
     */
    @Schema(description = "当前关联预约来源")
    @ApiModelProperty(value = "当前关联预约来源")
    private BookingSource bookingSource;

    /**
     * 当前关联预约状态
     */
    @Schema(description = "当前关联预约状态")
    @ApiModelProperty(value = "当前关联预约状态")
    private BookingStatus bookingStatus;

    /**
     * 当前关联预约的独立收款状态
     */
    @Schema(description = "当前关联预约的独立收款状态")
    @ApiModelProperty(value = "当前关联预约的独立收款状态")
    private BookingPaymentStatus paymentStatus;

    /**
     * 当前关联预约联系人姓名
     */
    @Schema(description = "当前关联预约联系人姓名")
    @ApiModelProperty(value = "当前关联预约联系人姓名")
    private String contactName;

    /**
     * 当前关联预约联系人手机号
     */
    @Schema(description = "当前关联预约联系人手机号")
    @ApiModelProperty(value = "当前关联预约联系人手机号")
    private String contactPhone;

    public static SessionSlotVO from(SessionSlotBO source) {
        return new SessionSlotVO(source.getId(), source.getCourtId(), source.getCourtName(),
                source.getSportTypeId(), source.getSportName(), source.getStartAt(), source.getEndAt(),
                source.getPriceFen(), source.getStatus(), source.getBookingId(), source.getBookingSource(),
                source.getBookingStatus(), source.getPaymentStatus(), source.getContactName(),
                source.getContactPhone());
    }
}
