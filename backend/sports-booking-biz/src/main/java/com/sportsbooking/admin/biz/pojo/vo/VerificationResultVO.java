package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.enums.BookingStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 员工核销结果模型，不暴露联系人和手机号。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResultVO {

    /**
     * 预约 ID
     */
    @Schema(description = "预约 ID")
    @ApiModelProperty(value = "预约 ID")
    private Long bookingId;

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
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private BookingStatus status;

    /**
     * 完成时间
     */
    @Schema(description = "完成时间")
    @ApiModelProperty(value = "完成时间")
    private Instant completedAt;

    /**
     * 功能描述: 将已核销预约转换为员工可查看的最小结果。
     * 创建时间: 2026/08/28 16:29
     *
     * @param booking 已核销预约实体
     * @return 核销结果接口模型
     * @author gjm
     */
    public static VerificationResultVO from(Booking booking) {
        return new VerificationResultVO(booking.id, booking.bookingNo, booking.venueName,
                booking.courtName, booking.sportName, booking.startAt, booking.endAt,
                booking.status, booking.completedAt);
    }
}
