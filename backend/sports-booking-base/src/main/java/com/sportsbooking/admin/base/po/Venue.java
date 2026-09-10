package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.sportsbooking.admin.base.enums.VenueStatus;

/**
 * 功能描述: 场馆公开资料及预约规则持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_venue")
public class Venue {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 场馆名称。 */
    public String name;
    /** 场馆封面文件路径。 */
    public String coverPath;
    /** 所在城市。 */
    public String city;
    /** 详细地址。 */
    public String address;
    /** 纬度。 */
    public BigDecimal latitude;
    /** 经度。 */
    public BigDecimal longitude;
    /** 场馆联系电话。 */
    public String phone;
    /** 场馆设施说明。 */
    public String facilities;
    /** 入场须知。 */
    public String entryNotice;
    /** 最大可提前预约天数。 */
    public Integer bookingDaysAhead;
    /** 最小预约时长，单位为分钟。 */
    public Integer minBookingMinutes;
    /** 开场前允许取消的截止时长，单位为分钟。 */
    public Integer cancelCutoffMinutes;
    /** 当前平台服务费率，单位为基点。 */
    public Integer serviceFeeRateBasisPoints;
    /** 场馆运营状态。 */
    public VenueStatus status;
    /** 创建时间。 */
    public LocalDateTime createdAt;
    /** 最后更新时间。 */
    public LocalDateTime updatedAt;
}
