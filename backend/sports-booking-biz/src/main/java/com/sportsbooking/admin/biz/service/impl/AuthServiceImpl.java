package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.po.AdminAccount;
import com.sportsbooking.admin.base.po.UserAccount;
import com.sportsbooking.admin.base.mapper.AdminAccountMapper;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.model.MiniPrincipal;
import com.sportsbooking.admin.biz.service.AuthService;
import com.sportsbooking.admin.biz.service.SessionService;
import com.sportsbooking.admin.biz.pojo.vo.AdminLoginVO;
import com.sportsbooking.admin.biz.pojo.vo.EmployeeIdentityVO;
import com.sportsbooking.admin.biz.pojo.vo.MiniLoginVO;
import com.sportsbooking.admin.biz.pojo.vo.UserProfileVO;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.base.mapper.EmployeeMapper;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能描述: 认证与小程序用户资料业务实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class AuthServiceImpl implements AuthService {
    private final AdminAccountMapper adminAccountMapper;
    private final UserAccountMapper userAccountMapper;
    private final EmployeeMapper employeeMapper;
    private final SessionService sessionService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthServiceImpl(AdminAccountMapper adminAccountMapper, UserAccountMapper userAccountMapper,
                           EmployeeMapper employeeMapper, SessionService sessionService,
                           BCryptPasswordEncoder passwordEncoder) {
        this.adminAccountMapper = adminAccountMapper;
        this.userAccountMapper = userAccountMapper;
        this.employeeMapper = employeeMapper;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 功能描述: 校验后台账号并创建 Redis 登录会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public AdminLoginVO loginAdmin(String username, String password) {
        AdminAccount account = adminAccountMapper.selectOne(new QueryWrapper<AdminAccount>()
                .eq("username", requireText(username)));
        if (account == null || account.status != EnableStatus.ACTIVE
                || !passwordEncoder.matches(password, account.passwordHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.LOGIN_FAILED);
        }
        AdminPrincipal principal = new AdminPrincipal(account.id, account.role, account.venueId, account.displayName);
        return new AdminLoginVO(sessionService.createAdminSession(principal), principal);
    }

    /**
     * 功能描述: 使用模拟微信 code 创建或读取用户并建立会话。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public MiniLoginVO loginMini(String code) {
        String mockOpenid = "mock:" + requireText(code);
        UserAccount user = userAccountMapper.selectOne(new QueryWrapper<UserAccount>()
                .eq("mock_openid", mockOpenid));
        if (user == null) {
            // 模拟登录首次进入时创建用户；正式 code2Session 接入后仍按微信标识幂等建档。
            user = new UserAccount();
            user.mockOpenid = mockOpenid;
            user.status = EnableStatus.ACTIVE;
            user.createdAt = LocalDateTime.now();
            user.updatedAt = user.createdAt;
            userAccountMapper.insert(user);
        }
        MiniPrincipal principal = new MiniPrincipal(user.id);
        return new MiniLoginVO(sessionService.createMiniSession(principal), getMiniProfile(user.id));
    }

    /**
     * 功能描述: 查询当前小程序用户资料和附加员工身份。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public UserProfileVO getMiniProfile(long userId) {
        UserAccount user = requireActiveUser(userId);
        Employee employee = employeeMapper.selectOne(new QueryWrapper<Employee>().eq("user_id", userId));
        EmployeeIdentityVO staff = employee == null ? null : new EmployeeIdentityVO(
                employee.id, employee.venueId, employee.name, employee.status
        );
        return new UserProfileVO(user.id, emptyIfNull(user.contactName), emptyIfNull(user.phone), staff);
    }

    /**
     * 功能描述: 更新当前用户的预约联系人资料。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public UserProfileVO updateMiniProfile(long userId, String contactName, String phone) {
        UserAccount user = requireActiveUser(userId);
        user.contactName = requireText(contactName);
        user.phone = requireText(phone);
        user.updatedAt = LocalDateTime.now();
        userAccountMapper.updateById(user);
        return getMiniProfile(userId);
    }

    /**
     * 功能描述: 查询并校验可用用户，避免停用账号继续调用业务接口。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private UserAccount requireActiveUser(long userId) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || user.status != EnableStatus.ACTIVE) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.USER_DISABLED);
        }
        return user;
    }

    /**
     * 功能描述: 校验信任边界上的必填文本并统一去除首尾空格。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        return value.trim();
    }

    /**
     * 功能描述: 资料未填写时返回空字符串，保持小程序表单契约稳定。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
