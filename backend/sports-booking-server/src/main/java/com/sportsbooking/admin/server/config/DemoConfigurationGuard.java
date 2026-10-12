package com.sportsbooking.admin.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** 防止非演示环境仅靠开关误执行初始化。 */
@Component
public class DemoConfigurationGuard {
    public DemoConfigurationGuard(Environment environment, @Value("${app.demo.seed-enabled:false}") boolean enabled) {
        if (enabled && !environment.acceptsProfiles(Profiles.of("demo"))) {
            throw new IllegalStateException("Demo seeding requires the explicit demo profile");
        }
    }
}
