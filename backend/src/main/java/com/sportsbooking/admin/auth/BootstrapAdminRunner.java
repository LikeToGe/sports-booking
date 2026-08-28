package com.sportsbooking.admin.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public BootstrapAdminRunner(JdbcTemplate jdbcTemplate, BCryptPasswordEncoder passwordEncoder,
                                @Value("${app.bootstrap.admin.username:}") String username,
                                @Value("${app.bootstrap.admin.password:}") String password) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!StringUtils.hasText(username) && !StringUtils.hasText(password)) {
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("首次平台管理员用户名和密码必须同时配置");
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM admin_account WHERE username = ?", Integer.class, username);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO admin_account (username, password_hash, display_name, role, status, create_operator_id, modify_operator_id) VALUES (?, ?, ?, 'PLATFORM_ADMIN', 'ACTIVE', 0, 0)",
            username, passwordEncoder.encode(password), "平台管理员");
        jdbcTemplate.update("INSERT INTO audit_log (action, target_type, target_id, result, reason) VALUES ('PLATFORM_ADMIN_BOOTSTRAPPED', 'ACCOUNT', LAST_INSERT_ID(), 'SUCCEEDED', '系统初始化')");
    }
}
