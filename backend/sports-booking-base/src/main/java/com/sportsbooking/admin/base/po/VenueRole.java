package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sportsbooking.admin.base.enums.EnableStatus;
import java.time.LocalDateTime;

/** 场馆自定义角色。 */
@TableName("s_venue_role")
public class VenueRole {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long venueId;
    public String name;
    public EnableStatus status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
