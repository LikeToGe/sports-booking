package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 场馆内顾客标签。 */
@TableName("s_venue_customer_tag")
public class VenueCustomerTag {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 所属场馆 ID。 */
    public Long venueId;
    /** 标签名称。 */
    public String name;
    /** 创建后台账号 ID。 */
    public Long createdByAdminId;
    /** 创建员工 ID。 */
    public Long createdByEmployeeId;
    /** 创建人姓名快照。 */
    public String createdByName;
    /** 创建时间。 */
    public LocalDateTime createdAt;
}
