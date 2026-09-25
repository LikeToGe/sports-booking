package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sportsbooking.admin.base.enums.VenuePermission;

/** 场馆角色与固定权限关联。 */
@TableName("s_venue_role_permission")
public class VenueRolePermission {
    public Long roleId;
    public VenuePermission permissionCode;
}
