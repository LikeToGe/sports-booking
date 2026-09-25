package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.EnableStatus;
import lombok.Data;

/** 场馆角色聚合查询结果。 */
@Data
public class VenueRoleBO {
    private Long id;
    private String name;
    private EnableStatus status;
    private String permissions;
    private Long employeeCount;
}
