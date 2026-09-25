package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.VenueRole;
import com.sportsbooking.admin.base.bo.VenueRoleBO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 场馆角色数据访问。 */
public interface VenueRoleMapper extends BaseMapper<VenueRole> {
    List<VenueRoleBO> selectRoleRows(@Param("venueId") long venueId);
}
