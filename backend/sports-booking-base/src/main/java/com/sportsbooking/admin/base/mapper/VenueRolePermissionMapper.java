package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.base.po.VenueRolePermission;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 角色权限数据访问。 */
public interface VenueRolePermissionMapper extends BaseMapper<VenueRolePermission> {
    List<VenuePermission> selectByEmployee(@Param("employeeId") long employeeId,
                                           @Param("venueId") long venueId);
}
