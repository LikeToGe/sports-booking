package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.base.bo.StaffVenueBO;
import com.sportsbooking.admin.base.bo.VenueRoleBO;
import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.base.mapper.EmployeeMapper;
import com.sportsbooking.admin.base.mapper.EmployeeRoleMapper;
import com.sportsbooking.admin.base.mapper.VenueRoleMapper;
import com.sportsbooking.admin.base.mapper.VenueRolePermissionMapper;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.base.po.EmployeeRole;
import com.sportsbooking.admin.base.po.VenueRole;
import com.sportsbooking.admin.base.po.VenueRolePermission;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.RoleSaveRequest;
import com.sportsbooking.admin.biz.pojo.vo.PermissionCatalogVO;
import com.sportsbooking.admin.biz.pojo.vo.StaffVenueVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueRoleVO;
import com.sportsbooking.admin.biz.service.PermissionService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 场馆权限实时解析与角色维护实现。 */
@Service
public class PermissionServiceImpl implements PermissionService {
    private final EmployeeMapper employeeMapper;
    private final VenueRoleMapper roleMapper;
    private final VenueRolePermissionMapper rolePermissionMapper;
    private final EmployeeRoleMapper employeeRoleMapper;

    public PermissionServiceImpl(EmployeeMapper employeeMapper, VenueRoleMapper roleMapper,
                                 VenueRolePermissionMapper rolePermissionMapper,
                                 EmployeeRoleMapper employeeRoleMapper) {
        this.employeeMapper = employeeMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.employeeRoleMapper = employeeRoleMapper;
    }

    @Override
    public List<PermissionCatalogVO> listPermissions() {
        return java.util.Arrays.stream(VenuePermission.values())
                .map(permission -> new PermissionCatalogVO(
                        permission, permission.getDisplayName(), permission.getGroup()))
                .toList();
    }

    @Override
    public List<VenueRoleVO> listRoles(long venueId) {
        return roleMapper.selectRoleRows(venueId).stream().map(this::toRole).toList();
    }

    @Override
    @Transactional
    public VenueRoleVO createRole(long venueId, RoleSaveRequest request) {
        VenueRole role = new VenueRole();
        role.venueId = venueId;
        role.name = requireName(request == null ? null : request.getName());
        role.status = request.getStatus() == null ? EnableStatus.ACTIVE : request.getStatus();
        role.createdAt = LocalDateTime.now();
        role.updatedAt = role.createdAt;
        try {
            roleMapper.insert(role);
            replacePermissions(role.id, request.getPermissions());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE);
        }
        return findRole(venueId, role.id);
    }

    @Override
    @Transactional
    public VenueRoleVO updateRole(long venueId, long roleId, RoleSaveRequest request) {
        VenueRole role = requireRole(venueId, roleId);
        role.name = requireName(request == null ? null : request.getName());
        role.status = request.getStatus() == null ? EnableStatus.ACTIVE : request.getStatus();
        role.updatedAt = LocalDateTime.now();
        try {
            roleMapper.updateById(role);
            replacePermissions(role.id, request.getPermissions());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE);
        }
        return findRole(venueId, roleId);
    }

    @Override
    @Transactional
    public void deleteRole(long venueId, long roleId) {
        roleMapper.deleteById(requireRole(venueId, roleId).id);
    }

    @Override
    public List<Long> listEmployeeRoleIds(long venueId, long employeeId) {
        requireEmployee(venueId, employeeId);
        return employeeRoleMapper.selectList(new QueryWrapper<EmployeeRole>()
                        .eq("employee_id", employeeId).orderByAsc("role_id"))
                .stream().map(role -> role.roleId).toList();
    }

    @Override
    @Transactional
    public void replaceEmployeeRoles(long venueId, long employeeId, List<Long> roleIds) {
        requireEmployee(venueId, employeeId);
        Set<Long> uniqueRoleIds = roleIds == null ? Set.of() : new LinkedHashSet<>(roleIds);
        if (uniqueRoleIds.contains(null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        if (!uniqueRoleIds.isEmpty()) {
            Long ownedCount = roleMapper.selectCount(new QueryWrapper<VenueRole>()
                    .eq("venue_id", venueId).in("id", uniqueRoleIds));
            if (ownedCount != uniqueRoleIds.size()) {
                throw notFound();
            }
        }
        employeeRoleMapper.delete(new QueryWrapper<EmployeeRole>().eq("employee_id", employeeId));
        for (Long roleId : uniqueRoleIds) {
            EmployeeRole relation = new EmployeeRole();
            relation.employeeId = employeeId;
            relation.roleId = roleId;
            employeeRoleMapper.insert(relation);
        }
    }

    /** 绑定关系改变后拒绝原用户会话，权限仍按当前有效角色实时解析。 */
    @Override
    public AdminPrincipal resolveEmployee(long employeeId, long venueId, long userId) {
        Employee employee = employeeMapper.selectActiveSessionBinding(employeeId, venueId, userId);
        if (employee == null || employee.status != EnableStatus.ACTIVE
                || employee.userId == null || employee.userId != userId) {
            throw forbidden();
        }
        Set<VenuePermission> permissions = new LinkedHashSet<>(
                rolePermissionMapper.selectByEmployee(employeeId, venueId));
        return new AdminPrincipal(0, AdminRole.VENUE_EMPLOYEE, venueId, employee.name,
                employee.id, permissions);
    }

    @Override
    public Employee requireEmployee(long userId, long venueId, VenuePermission permission) {
        Employee employee = employeeMapper.selectActiveSessionBinding(null, venueId, userId);
        if (employee == null || !rolePermissionMapper.selectByEmployee(employee.id, venueId).contains(permission)) {
            throw forbidden();
        }
        return employee;
    }

    @Override
    public List<StaffVenueVO> listStaffVenues(long userId) {
        return employeeMapper.selectStaffVenues(userId).stream().map(row -> new StaffVenueVO(
                row.getEmployeeId(), row.getVenueId(), row.getVenueName(), row.getEmployeeName(),
                parsePermissions(row.getPermissions()))).toList();
    }

    private void replacePermissions(long roleId, List<VenuePermission> permissions) {
        rolePermissionMapper.delete(new QueryWrapper<VenueRolePermission>().eq("role_id", roleId));
        Set<VenuePermission> uniquePermissions = permissions == null ? Set.of() : new LinkedHashSet<>(permissions);
        if (uniquePermissions.contains(null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        for (VenuePermission permission : uniquePermissions) {
            VenueRolePermission relation = new VenueRolePermission();
            relation.roleId = roleId;
            relation.permissionCode = permission;
            rolePermissionMapper.insert(relation);
        }
    }

    private VenueRole requireRole(long venueId, long roleId) {
        VenueRole role = roleMapper.selectById(roleId);
        if (role == null || role.venueId != venueId) {
            throw notFound();
        }
        return role;
    }

    private Employee requireEmployee(long venueId, long employeeId) {
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null || employee.venueId != venueId) {
            throw notFound();
        }
        return employee;
    }

    private VenueRoleVO findRole(long venueId, long roleId) {
        return listRoles(venueId).stream().filter(role -> role.getId() == roleId).findFirst().orElseThrow(this::notFound);
    }

    private VenueRoleVO toRole(VenueRoleBO role) {
        return new VenueRoleVO(role.getId(), role.getName(), role.getStatus(),
                parsePermissions(role.getPermissions()), role.getEmployeeCount());
    }

    private List<VenuePermission> parsePermissions(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<VenuePermission> permissions = new ArrayList<>();
        for (String code : value.split(",")) {
            permissions.add(VenuePermission.valueOf(code));
        }
        return permissions;
    }

    private String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        return name.trim();
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    private ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }
}
