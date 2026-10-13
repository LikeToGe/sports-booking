package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.base.po.Venue;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 场馆接口返回模型，隔离持久化实体与 HTTP 协议。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 名称
     */
    @Schema(description = "名称")
    @ApiModelProperty(value = "名称")
    private String name;

    /**
     * 封面访问路径
     */
    @Schema(description = "封面访问路径")
    @ApiModelProperty(value = "封面访问路径")
    private String coverPath;

    /**
     * 所在城市
     */
    @Schema(description = "所在城市")
    @ApiModelProperty(value = "所在城市")
    private String city;

    /** 所属区县。 */
    @Schema(description = "所属区县")
    @ApiModelProperty(value = "所属区县")
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

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private VenueStatus status;

    /**
     * 功能描述: 将场馆持久化实体转换为安全的接口模型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venue 场馆持久化实体
     * @return 场馆接口模型
     * @author gjm
     */
    public static VenueVO from(Venue venue) {
        return new VenueVO(venue.id, venue.name, venue.coverPath, venue.city, venue.district, venue.address,
                venue.latitude, venue.longitude, venue.phone, venue.facilities, venue.entryNotice,
                venue.bookingDaysAhead, venue.minBookingMinutes, venue.cancelCutoffMinutes, venue.status);
    }
}
