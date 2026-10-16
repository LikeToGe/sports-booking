package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.TokenService;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.base.mapper.EmployeeMapper;
import com.sportsbooking.admin.biz.service.EmployeeService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.pojo.vo.EmployeeBindingVO;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能描述: 场馆员工建档、绑定和身份校验业务实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class EmployeeServiceImpl implements EmployeeService {
    private final VenueService venueService;
    private final EmployeeMapper employeeMapper;
    private final TokenService tokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmployeeServiceImpl(VenueService venueService, EmployeeMapper employeeMapper, TokenService tokenService) {
        this.venueService = venueService;
        this.employeeMapper = employeeMapper;
        this.tokenService = tokenService;
    }

    /**
     * 功能描述: 查询指定场馆的员工列表。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<Employee> listEmployees(long venueId) {
        venueService.getVenue(venueId);
        return employeeMapper.selectList(new QueryWrapper<Employee>()
                .eq("venue_id", venueId).orderByDesc("created_at"));
    }

    /**
     * 功能描述: 创建场馆员工并签发首次绑定码。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public EmployeeBindingVO createEmployee(long venueId, String name) {
        venueService.getVenue(venueId);
        Employee employee = new Employee();
        employee.venueId = venueId;
        employee.name = requireText(name);
        employee.status = EnableStatus.ACTIVE;
        employee.createdAt = LocalDateTime.now();
        employee.updatedAt = employee.createdAt;
        employeeMapper.insert(employee);
        return issueBindingCode(venueId, employee.id);
    }

    /**
     * 功能描述: 更新当前场馆员工姓名。
     * 创建时间: 2026/09/11
     *
     * @author gjm
     */
    @Override
    public Employee updateEmployeeName(long venueId, long employeeId, String name) {
        Employee employee = requireEmployee(venueId, employeeId);
        employee.name = requireText(name);
        employee.updatedAt = LocalDateTime.now();
        // 仅更新本次资料字段，避免旧对象中的绑定摘要把已消费的凭证重新写回。
        employeeMapper.update(null, new UpdateWrapper<Employee>().eq("id", employee.id)
                .set("name", employee.name).set("updated_at", employee.updatedAt));
        return employee;
    }

    /**
     * 功能描述: 为指定员工签发一次性绑定码。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public EmployeeBindingVO issueBindingCode(long venueId, long employeeId) {
        Employee employee = requireEmployee(venueId, employeeId);
        // 明文绑定码只在本次响应返回，数据库仅保存摘要和过期时间。
        String code = "%06d".formatted(secureRandom.nextInt(BusinessConstants.SHORT_CODE_BOUND));
        employee.bindingCodeHash = tokenService.hash(code);
        employee.bindingExpiresAt = LocalDateTime.now().plus(BusinessConstants.EMPLOYEE_BINDING_TTL);
        employee.updatedAt = LocalDateTime.now();
        employeeMapper.update(null, new UpdateWrapper<Employee>().eq("id", employee.id)
                .set("binding_code_hash", employee.bindingCodeHash)
                .set("binding_expires_at", employee.bindingExpiresAt).set("updated_at", employee.updatedAt));
        return new EmployeeBindingVO(employee.id, code, employee.bindingExpiresAt);
    }

    /**
     * 功能描述: 消费一次性绑定码并绑定当前微信用户。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public Employee bindEmployee(long userId, String code) {
        // 认证到写入之间可能发生注销；先按 User→Employee 顺序锁定当前有效用户。
        if (employeeMapper.lockActiveBindingUser(userId) == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.USER_DISABLED);
        }
        String codeHash = tokenService.hash(requireText(code));
        Employee employee = employeeMapper.selectOne(new QueryWrapper<Employee>()
                .eq("binding_code_hash", codeHash).eq("status", EnableStatus.ACTIVE));
        LocalDateTime now = LocalDateTime.now();
        if (employee == null || employee.bindingExpiresAt == null
                || !employee.bindingExpiresAt.isAfter(now)) {
            throw badRequest();
        }
        Long existingCount = employeeMapper.selectCount(new QueryWrapper<Employee>()
                .eq("venue_id", employee.venueId).eq("user_id", userId).ne("id", employee.id));
        if (existingCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_BOUND);
        }
        // 绑定码只能消费一次，绑定成功后立即清除摘要和过期时间。
        try {
            if (employeeMapper.consumeBindingCode(employee.id, codeHash, userId, now) != 1) {
                throw badRequest();
            }
        } catch (DuplicateKeyException exception) {
            // 同用户并发绑定同馆多个员工时，由唯一约束兜底并回滚码的消费。
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_BOUND);
        }
        employee.userId = userId;
        employee.bindingCodeHash = null;
        employee.bindingExpiresAt = null;
        employee.updatedAt = now;
        return employee;
    }

    /**
     * 功能描述: 更新指定员工的启停状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Employee updateEmployeeStatus(long venueId, long employeeId, EnableStatus status) {
        if (status == null) {
            throw badRequest();
        }
        Employee employee = requireEmployee(venueId, employeeId);
        employee.status = status;
        employee.updatedAt = LocalDateTime.now();
        employeeMapper.update(null, new UpdateWrapper<Employee>().eq("id", employee.id)
                .set("status", employee.status).set("updated_at", employee.updatedAt));
        return employee;
    }

    /**
     * 功能描述: 查询当前微信用户的有效员工身份。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Employee getActiveEmployee(long userId) {
        Employee employee = employeeMapper.selectActiveSessionBinding(null, null, userId);
        if (employee == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.STAFF_REQUIRED);
        }
        return employee;
    }

    @Override
    public Employee getActiveEmployee(long userId, long venueId) {
        Employee employee = employeeMapper.selectActiveSessionBinding(null, venueId, userId);
        if (employee == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.STAFF_REQUIRED);
        }
        return employee;
    }

    @Override
    public Employee unbindEmployee(long venueId, long employeeId) {
        Employee employee = requireEmployee(venueId, employeeId);
        employee.userId = null;
        employee.bindingCodeHash = null;
        employee.bindingExpiresAt = null;
        employee.updatedAt = LocalDateTime.now();
        // 必须显式清 NULL；默认实体更新会跳过空字段，导致解绑只在响应中生效。
        employeeMapper.clearBinding(employeeId, venueId, employee.updatedAt);
        return employee;
    }

    /**
     * 功能描述: 校验员工属于当前场馆。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param employeeId 员工 ID
     * @return 当前场馆员工
     * @author gjm
     */
    private Employee requireEmployee(long venueId, long employeeId) {
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null || employee.venueId != venueId) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return employee;
    }

    /**
     * 功能描述: 校验信任边界上的必填文本。
     * 创建时间: 2026/08/28 16:29
     *
     * @param value 原始文本
     * @return 去除首尾空格的文本
     * @author gjm
     */
    private String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw badRequest();
        }
        return value.trim();
    }

    /**
     * 功能描述: 构造统一参数错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 参数错误异常
     * @author gjm
     */
    private ApiException badRequest() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
    }
}
