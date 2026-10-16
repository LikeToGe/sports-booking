package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.biz.pojo.request.RoleSaveRequest;
import com.sportsbooking.admin.biz.pojo.vo.PermissionCatalogVO;
import com.sportsbooking.admin.biz.pojo.vo.StaffVenueVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueRoleVO;
import java.util.List;

/** 场馆固定权限、角色和员工授权统一入口。 */
public interface PermissionService {
    List<PermissionCatalogVO> listPermissions();
    List<VenueRoleVO> listRoles(long venueId);
    VenueRoleVO createRole(long venueId, RoleSaveRequest request);
    VenueRoleVO updateRole(long venueId, long roleId, RoleSaveRequest request);
    void deleteRole(long venueId, long roleId);
    List<Long> listEmployeeRoleIds(long venueId, long employeeId);
    void replaceEmployeeRoles(long venueId, long employeeId, List<Long> roleIds);
    /** 复核会话原用户仍绑定当前员工，并实时解析权限。 */
    AdminPrincipal resolveEmployee(long employeeId, long venueId, long userId);
    Employee requireEmployee(long userId, long venueId, VenuePermission permission);
    List<StaffVenueVO> listStaffVenues(long userId);
}
