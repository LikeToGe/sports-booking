package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.base.bo.StaffVenueBO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 功能描述: 场馆员工数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface EmployeeMapper extends BaseMapper<Employee> {
    List<StaffVenueBO> selectStaffVenues(@Param("userId") long userId);
}
