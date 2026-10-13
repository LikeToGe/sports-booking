package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 场馆公开资料与预约规则保存请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "场馆资料和预约规则保存请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueSaveRequest {

    /**
     * 场馆名称
     */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String name;

    /**
     * 所在城市
     */
    @Schema(description = "所在城市")
    @ApiModelProperty(value = "所在城市")
    private String city;

    /** 所属区县，旧场馆可为空。 */
    @Schema(description = "所属区县，旧场馆可为空")
    @ApiModelProperty(value = "所属区县，旧场馆可为空")
    private String district;

    /**
     * 详细地址
     */
    @Schema(description = "详细地址")
    @ApiModelProperty(value = "详细地址")
    private String address;

    /**
     * 纬度
     */
    @Schema(description = "纬度")
    @ApiModelProperty(value = "纬度")
    private BigDecimal latitude;

    /**
     * 经度
     */
    @Schema(description = "经度")
    @ApiModelProperty(value = "经度")
    private BigDecimal longitude;

    /**
     * 联系电话
     */
    @Schema(description = "联系电话")
    @ApiModelProperty(value = "联系电话")
    private String phone;

    /**
     * 场馆设施说明
     */
    @Schema(description = "场馆设施说明")
    @ApiModelProperty(value = "场馆设施说明")
    private String facilities;

    /**
     * 入场须知
     */
    @Schema(description = "入场须知")
    @ApiModelProperty(value = "入场须知")
    private String entryNotice;

    /**
     * 可提前预约天数
     */
    @Schema(description = "可提前预约天数")
    @ApiModelProperty(value = "可提前预约天数")
    private Integer bookingDaysAhead;

    /**
     * 最晚提前预约分钟数
     */
    @Schema(description = "最晚提前预约分钟数")
    @ApiModelProperty(value = "最晚提前预约分钟数")
    private Integer minBookingMinutes;

    /**
     * 最晚提前取消分钟数
     */
    @Schema(description = "最晚提前取消分钟数")
    @ApiModelProperty(value = "最晚提前取消分钟数")
    private Integer cancelCutoffMinutes;
}
