package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.CancelledBy;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 用户或场馆管理员可查看的预约接口模型。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingVO {

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
     * 场馆 ID
     */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;

    /**
     * 场地 ID
     */
    @Schema(description = "场地 ID")
    @ApiModelProperty(value = "场地 ID")
    private Long courtId;

    /**
     * 运动类型 ID
     */
    @Schema(description = "运动类型 ID")
    @ApiModelProperty(value = "运动类型 ID")
    private Long sportTypeId;

    /**
     * 具体场次 ID
     */
    @Schema(description = "具体场次 ID")
    @ApiModelProperty(value = "具体场次 ID")
    private Long sessionSlotId;

    /**
     * 关联的场馆顾客档案 ID
     */
    @Schema(description = "关联的场馆顾客档案 ID")
    @ApiModelProperty(value = "关联的场馆顾客档案 ID")
    private Long venueCustomerId;

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
     * 场馆名称
     */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String venueName;

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
    private Instant startAt;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    @ApiModelProperty(value = "结束时间")
    private Instant endAt;

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
     * 预约整体收款或线下登记收款状态
     */
    @Schema(description = "预约整体收款或线下登记收款状态")
    @ApiModelProperty(value = "预约整体收款或线下登记收款状态")
    private BookingPaymentStatus paymentStatus;

    /**
     * 支付过期时间
     */
    @Schema(description = "支付过期时间")
    @ApiModelProperty(value = "支付过期时间")
    private Instant paymentExpiresAt;

    /**
     * 取消操作方
     */
    @Schema(description = "取消操作方")
    @ApiModelProperty(value = "取消操作方")
    private CancelledBy cancelledBy;

    /**
     * 取消原因
     */
    @Schema(description = "取消原因")
    @ApiModelProperty(value = "取消原因")
    private String cancelReason;

    /**
     * 取消时间
     */
    @Schema(description = "取消时间")
    @ApiModelProperty(value = "取消时间")
    private Instant cancelledAt;

    /**
     * 完成时间
     */
    @Schema(description = "完成时间")
    @ApiModelProperty(value = "完成时间")
    private Instant completedAt;

    /**
     * 功能描述: 将预约实体转换为用户或场馆管理员接口模型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param booking 预约持久化实体
     * @return 预约接口模型
     * @author gjm
     */
    public static BookingVO from(Booking booking) {
        return new BookingVO(booking.id, booking.bookingNo, booking.venueId, booking.courtId,
                booking.sportTypeId, booking.sessionSlotId, booking.venueCustomerId, booking.source, booking.contactName,
                booking.contactPhone, booking.venueName, booking.courtName, booking.sportName,
                booking.startAt, booking.endAt, booking.amountFen, booking.status, booking.paymentStatus,
                booking.paymentExpiresAt, booking.cancelledBy, booking.cancelReason,
                booking.cancelledAt, booking.completedAt);
    }
}
