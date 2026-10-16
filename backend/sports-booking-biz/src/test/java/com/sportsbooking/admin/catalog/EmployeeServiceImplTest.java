package com.sportsbooking.admin.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.mapper.EmployeeMapper;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.TokenService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.impl.EmployeeServiceImpl;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.Test;

/**
 * 功能描述: 员工资料更新单元测试。
 * 创建时间: 2026/09/11
 *
 * @author gjm
 */
class EmployeeServiceImplTest {

    @Test
    void updatesOwnedEmployeeName() {
        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        EmployeeServiceImpl service = new EmployeeServiceImpl(
                mock(VenueService.class), employeeMapper, mock(TokenService.class));
        Employee employee = new Employee();
        employee.id = 1L;
        employee.venueId = 2L;
        employee.name = "旧姓名";
        when(employeeMapper.selectById(1L)).thenReturn(employee);

        Employee result = service.updateEmployeeName(2L, 1L, " 新姓名 ");

        assertThat(result.name).isEqualTo("新姓名");
        verify(employeeMapper).update(eq(null), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(employeeMapper, never()).updateById(any(Employee.class));
    }

    @Test
    void consumesCodeOnceAndRejectsConcurrentLoser() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        TokenService tokens = mock(TokenService.class);
        when(mapper.lockActiveBindingUser(5L)).thenReturn(5L);
        when(tokens.hash("123456")).thenReturn("digest");
        when(mapper.selectOne(any())).thenAnswer(invocation -> unboundEmployee());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.consumeBindingCode(eq(1L), eq("digest"), eq(5L), any()))
                .thenReturn(1).thenReturn(0);
        EmployeeServiceImpl service = new EmployeeServiceImpl(mock(VenueService.class), mapper, tokens);

        Employee result = service.bindEmployee(5L, "123456");
        assertThat(result.userId).isEqualTo(5L);
        assertThat(result.bindingCodeHash).isNull();
        assertThat(result.bindingExpiresAt).isNull();
        org.mockito.InOrder order = inOrder(mapper);
        order.verify(mapper).lockActiveBindingUser(5L);
        order.verify(mapper).selectOne(any());
        order.verify(mapper).consumeBindingCode(eq(1L), eq("digest"), eq(5L), any());
        assertThatThrownBy(() -> service.bindEmployee(5L, "123456"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        verify(mapper, never()).updateById(any(Employee.class));
    }

    @Test
    void translatesSameVenueConcurrentBindingConstraint() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        TokenService tokens = mock(TokenService.class);
        when(mapper.lockActiveBindingUser(5L)).thenReturn(5L);
        when(tokens.hash("123456")).thenReturn("digest");
        when(mapper.selectOne(any())).thenReturn(unboundEmployee());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.consumeBindingCode(eq(1L), eq("digest"), eq(5L), any()))
                .thenThrow(new DuplicateKeyException("mock unique binding"));
        EmployeeServiceImpl service = new EmployeeServiceImpl(mock(VenueService.class), mapper, tokens);

        assertThatThrownBy(() -> service.bindEmployee(5L, "123456"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.ALREADY_BOUND));
    }

    @Test
    void rejectsInvalidatedUserBeforeReadingOrConsumingEmployeeCode() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        // MyBatis无匹配行返回null；Mockito对Long默认返回0，需显式模拟失效用户的查询结果。
        when(mapper.lockActiveBindingUser(5L)).thenReturn((Long) null);
        EmployeeServiceImpl service = new EmployeeServiceImpl(mock(VenueService.class), mapper, mock(TokenService.class));

        assertThatThrownBy(() -> service.bindEmployee(5L, "123456"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.USER_DISABLED));
        verify(mapper, never()).selectOne(any());
        verify(mapper, never()).consumeBindingCode(anyLong(), any(), anyLong(), any());
    }

    @Test
    void clearsPersistedBindingAndPendingCodeWithinOwnedVenue() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        Employee employee = unboundEmployee();
        employee.userId = 5L;
        when(mapper.selectById(1L)).thenReturn(employee);
        EmployeeServiceImpl service = new EmployeeServiceImpl(mock(VenueService.class), mapper, mock(TokenService.class));

        Employee result = service.unbindEmployee(2L, 1L);

        verify(mapper).clearBinding(eq(1L), eq(2L), any());
        verify(mapper, never()).updateById(any(Employee.class));
        assertThat(result.userId).isNull();
        assertThat(result.bindingCodeHash).isNull();
        assertThat(result.bindingExpiresAt).isNull();
        assertThatThrownBy(() -> service.unbindEmployee(3L, 1L)).isInstanceOf(ApiException.class);
    }

    private Employee unboundEmployee() {
        Employee employee = new Employee();
        employee.id = 1L;
        employee.venueId = 2L;
        employee.status = EnableStatus.ACTIVE;
        employee.bindingCodeHash = "digest";
        employee.bindingExpiresAt = LocalDateTime.now().plusMinutes(10);
        return employee;
    }
}
