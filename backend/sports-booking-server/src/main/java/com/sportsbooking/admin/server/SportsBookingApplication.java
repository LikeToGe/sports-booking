package com.sportsbooking.admin.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 功能描述: 约场后端应用启动入口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.sportsbooking.admin")
@MapperScan("com.sportsbooking.admin.base.mapper")
public class SportsBookingApplication {

    /**
     * 功能描述: 启动 Spring Boot 应用。
     * 创建时间: 2026/08/28 16:29
     *
     * @param args 启动参数
     * @author gjm
     */

    public static void main(String[] args) {
        SpringApplication.run(SportsBookingApplication.class, args);
    }
}
