package com.sportsbooking.admin.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.mapper.EmployeeMapper;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.biz.service.TokenService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.impl.EmployeeServiceImpl;
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
        verify(employeeMapper).updateById(employee);
    }
}
