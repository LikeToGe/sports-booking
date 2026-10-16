package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.base.bo.StaffVenueBO;
import java.util.List;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/**
 * 功能描述: 场馆员工数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface EmployeeMapper extends BaseMapper<Employee> {
    List<StaffVenueBO> selectStaffVenues(@Param("userId") long userId);
    /** 绑定与注销共用用户行锁，当前读取拒绝等待期间已停用或注销的用户。 */
    Long lockActiveBindingUser(@Param("userId") long userId);
    /** 单条查询复核有效用户绑定；可空员工/场馆范围仅用于既有小程序身份入口。 */
    Employee selectActiveSessionBinding(@Param("employeeId") Long employeeId,
                                        @Param("venueId") Long venueId, @Param("userId") long userId);
    int consumeBindingCode(@Param("employeeId") long employeeId, @Param("codeHash") String codeHash,
                           @Param("userId") long userId, @Param("now") LocalDateTime now);
    int clearBinding(@Param("employeeId") long employeeId, @Param("venueId") long venueId,
                     @Param("now") LocalDateTime now);
}
