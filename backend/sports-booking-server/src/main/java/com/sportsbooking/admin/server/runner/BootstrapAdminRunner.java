package com.sportsbooking.admin.server.runner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.base.po.AdminAccount;
import com.sportsbooking.admin.base.mapper.AdminAccountMapper;
import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.EnableStatus;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 功能描述: 数据库没有平台账号时创建本地启动管理员。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Component
@Order(1)
public class BootstrapAdminRunner implements CommandLineRunner {
    private final AdminAccountMapper adminAccountMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public BootstrapAdminRunner(AdminAccountMapper adminAccountMapper, BCryptPasswordEncoder passwordEncoder,
                                @Value("${app.bootstrap.username}") String username,
                                @Value("${app.bootstrap.password}") String password) {
        this.adminAccountMapper = adminAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    /**
     * 功能描述: 数据库没有平台账号时创建本地启动管理员。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void run(String... args) {
        Long count = adminAccountMapper.selectCount(new QueryWrapper<AdminAccount>()
                .eq("username", username));
        if (count > 0) {
            return;
        }
        AdminAccount account = new AdminAccount();
        account.role = AdminRole.PLATFORM_ADMIN;
        account.username = username;
        account.passwordHash = passwordEncoder.encode(password);
        account.displayName = "平台管理员";
        account.status = EnableStatus.ACTIVE;
        account.mustChangePassword = false;
        account.createdAt = LocalDateTime.now();
        account.updatedAt = account.createdAt;
        adminAccountMapper.insert(account);
    }
}
