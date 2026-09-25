package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.TableName;

/** 员工与场馆角色关联。 */
@TableName("s_employee_role")
public class EmployeeRole {
    public Long employeeId;
    public Long roleId;
}
