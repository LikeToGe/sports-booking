package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.BookingStatus;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 月度平台服务费逐笔预约明细。
 * 创建时间: 2026/09/08
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceFeeBookingVO {
    /** 预约单号。 */
    @Schema(description = "预约单号")
    @ApiModelProperty(value = "预约单号")
    private String bookingNo;
    /** 场馆 ID。 */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;
    /** 场馆名称快照。 */
    @Schema(description = "场馆名称快照")
    @ApiModelProperty(value = "场馆名称快照")
    private String venueName;
    /** 场地名称快照。 */
    @Schema(description = "场地名称快照")
    @ApiModelProperty(value = "场地名称快照")
    private String courtName;
    /** 场次开始时间。 */
    @Schema(description = "场次开始时间，Asia/Shanghai")
    @ApiModelProperty(value = "场次开始时间，Asia/Shanghai")
    private String startAt;
    /** 场次结束时间。 */
    @Schema(description = "场次结束时间，Asia/Shanghai")
    @ApiModelProperty(value = "场次结束时间，Asia/Shanghai")
    private String endAt;
    /** 预约状态。 */
    @Schema(description = "预约状态")
    @ApiModelProperty(value = "预约状态")
    private BookingStatus status;
    /** 预约金额，单位为分。 */
    @Schema(description = "预约金额，单位为分")
    @ApiModelProperty(value = "预约金额，单位为分")
    private Integer amountFen;
    /** 服务费率快照，单位为基点。 */
    @Schema(description = "服务费率快照基点")
    @ApiModelProperty(value = "服务费率快照基点")
    private Integer serviceFeeRateBasisPoints;
    /** 精确平台服务费，单位为元。 */
    @Schema(description = "精确平台服务费，单位为元，最多六位小数")
    @ApiModelProperty(value = "精确平台服务费，单位为元，最多六位小数")
    private String serviceFeeYuan;
}
