package com.sportsbooking.admin.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.base.mapper.EmployeeMapper;
import com.sportsbooking.admin.base.mapper.EmployeeRoleMapper;
import com.sportsbooking.admin.base.mapper.VenueRoleMapper;
import com.sportsbooking.admin.base.mapper.VenueRolePermissionMapper;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.impl.PermissionServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionServiceImplTest {
    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    private final VenueRolePermissionMapper permissionMapper = mock(VenueRolePermissionMapper.class);
    private final PermissionServiceImpl service = new PermissionServiceImpl(employeeMapper,
            mock(VenueRoleMapper.class), permissionMapper, mock(EmployeeRoleMapper.class));
    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.id = 10L;
        employee.venueId = 20L;
        employee.userId = 30L;
        employee.name = "员工";
        employee.status = EnableStatus.ACTIVE;
        when(employeeMapper.selectActiveSessionBinding(10L, 20L, 30L)).thenReturn(employee);
    }

    @Test
    void resolvesUnionFromAllActiveRolesAndRevocationOnNextRequest() {
        when(permissionMapper.selectByEmployee(10L, 20L))
                .thenReturn(List.of(VenuePermission.BOOKING_VIEW, VenuePermission.MEMBER_RECHARGE))
                .thenReturn(List.of(VenuePermission.BOOKING_VIEW));

        AdminPrincipal first = service.resolveEmployee(10L, 20L, 30L);
        AdminPrincipal second = service.resolveEmployee(10L, 20L, 30L);

        assertThat(first.getPermissions()).containsExactlyInAnyOrder(
                VenuePermission.BOOKING_VIEW, VenuePermission.MEMBER_RECHARGE);
        assertThat(second.getPermissions()).containsExactly(VenuePermission.BOOKING_VIEW);
    }

    @Test
    void rejectsCrossVenueDisabledAndUnboundEmployees() {
        assertThatThrownBy(() -> service.resolveEmployee(10L, 21L, 30L)).isInstanceOf(ApiException.class);

        employee.status = EnableStatus.DISABLED;
        assertThatThrownBy(() -> service.resolveEmployee(10L, 20L, 30L)).isInstanceOf(ApiException.class);

        employee.status = EnableStatus.ACTIVE;
        employee.userId = null;
        assertThatThrownBy(() -> service.resolveEmployee(10L, 20L, 30L)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsPreviousUserAfterEmployeeIsRebound() {
        employee.userId = 31L;

        assertThatThrownBy(() -> service.resolveEmployee(10L, 20L, 30L)).isInstanceOf(ApiException.class);
        when(permissionMapper.selectByEmployee(10L, 20L)).thenReturn(List.of());
        when(employeeMapper.selectActiveSessionBinding(10L, 20L, 31L)).thenReturn(employee);
        assertThat(service.resolveEmployee(10L, 20L, 31L).getEmployeeId()).isEqualTo(10L);
    }

    @Test
    void rejectsExistingStaffIdentityAfterItsUserIsDisabled() {
        when(employeeMapper.selectActiveSessionBinding(10L, 20L, 30L)).thenReturn(employee).thenReturn(null);
        when(permissionMapper.selectByEmployee(10L, 20L)).thenReturn(List.of(VenuePermission.BOOKING_VIEW));

        assertThat(service.resolveEmployee(10L, 20L, 30L).getPermissions()).contains(VenuePermission.BOOKING_VIEW);
        assertThatThrownBy(() -> service.resolveEmployee(10L, 20L, 30L)).isInstanceOf(ApiException.class);
        // 员工坏数据仍为ACTIVE且关联原用户，也不能让失效用户再次拥有权限。
        assertThat(employee.status).isEqualTo(EnableStatus.ACTIVE);
        assertThat(employee.userId).isEqualTo(30L);
        verify(permissionMapper, times(1)).selectByEmployee(10L, 20L);
    }

    @Test
    void rejectsMiniEmployeeActionAfterItsUserIsDisabled() {
        when(employeeMapper.selectActiveSessionBinding(null, 20L, 30L)).thenReturn(employee).thenReturn(null);
        when(permissionMapper.selectByEmployee(10L, 20L)).thenReturn(List.of(VenuePermission.BOOKING_VIEW));

        assertThat(service.requireEmployee(30L, 20L, VenuePermission.BOOKING_VIEW)).isSameAs(employee);
        assertThatThrownBy(() -> service.requireEmployee(30L, 20L, VenuePermission.BOOKING_VIEW))
                .isInstanceOf(ApiException.class);
        verify(permissionMapper, times(1)).selectByEmployee(10L, 20L);
    }
}
