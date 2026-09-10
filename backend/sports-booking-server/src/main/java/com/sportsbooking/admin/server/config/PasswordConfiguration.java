package com.sportsbooking.admin.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 功能描述: 后台密码摘要算法配置。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Configuration
public class PasswordConfiguration {

    /**
     * 功能描述: 创建后台密码 BCrypt 编码器。
     * 创建时间: 2026/08/28 16:29
     *
     * @return BCrypt 密码编码器
     * @author gjm
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
