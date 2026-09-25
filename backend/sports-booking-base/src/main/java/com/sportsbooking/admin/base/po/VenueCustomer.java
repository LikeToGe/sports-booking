package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sportsbooking.admin.base.enums.VenueCustomerStatus;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 功能描述: 用户与单个场馆之间的顾客或会员关系。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@TableName("s_venue_customer")
public class VenueCustomer {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 所属场馆 ID。 */
    public Long venueId;
    /** 平台用户 ID。 */
    public Long userId;
    /** 顾客或会员状态。 */
    public VenueCustomerStatus status;
    /** 场馆范围内唯一的会员码。 */
    public String memberCode;
    /** 首次充值激活会员的时间。 */
    public Instant activatedAt;
    /** 创建时间。 */
    public LocalDateTime createdAt;
    /** 最后更新时间。 */
    public LocalDateTime updatedAt;
}
