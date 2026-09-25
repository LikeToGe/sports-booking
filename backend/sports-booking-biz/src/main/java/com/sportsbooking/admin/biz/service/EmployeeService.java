package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.biz.pojo.vo.EmployeeBindingVO;
import java.util.List;

/**
 * 功能描述: 场馆员工建档、绑定和身份校验业务接口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface EmployeeService {

    /**
     * 功能描述: 查询指定场馆员工。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @return 员工列表
     * @author gjm
     */
    List<Employee> listEmployees(long venueId);

    /**
     * 功能描述: 创建员工并签发首次绑定码。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param name 员工姓名
     * @return 一次性绑定码
     * @author gjm
     */
    EmployeeBindingVO createEmployee(long venueId, String name);

    /**
     * 功能描述: 更新员工姓名。
     * 创建时间: 2026/09/11
     *
     * @param venueId 场馆 ID
     * @param employeeId 员工 ID
     * @param name 新姓名
     * @return 更新后的员工
     * @author gjm
     */
    Employee updateEmployeeName(long venueId, long employeeId, String name);

    /**
     * 功能描述: 为指定员工重新签发一次性绑定码。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param employeeId 员工 ID
     * @return 一次性绑定码
     * @author gjm
     */
    EmployeeBindingVO issueBindingCode(long venueId, long employeeId);

    /**
     * 功能描述: 消费绑定码并绑定当前微信用户。
     * 创建时间: 2026/08/28 16:29
     *
     * @param userId 微信用户 ID
     * @param code 一次性绑定码
     * @return 已绑定员工
     * @author gjm
     */
    Employee bindEmployee(long userId, String code);

    /**
     * 功能描述: 更新员工启停状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param employeeId 员工 ID
     * @param status 启停状态
     * @return 更新后的员工
     * @author gjm
     */
    Employee updateEmployeeStatus(long venueId, long employeeId, EnableStatus status);

    /**
     * 功能描述: 查询微信用户当前有效的场馆员工身份。
     * 创建时间: 2026/08/28 16:29
     *
     * @param userId 微信用户 ID
     * @return 有效员工身份
     * @author gjm
     */
    Employee getActiveEmployee(long userId);

    /** 查询用户在指定场馆的有效员工身份。 */
    Employee getActiveEmployee(long userId, long venueId);

    /** 解除员工与小程序用户的绑定。 */
    Employee unbindEmployee(long venueId, long employeeId);
}
