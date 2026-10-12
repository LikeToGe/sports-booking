package com.sportsbooking.admin.server.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 显式 Demo profile 是初始化前置条件。 */
class DemoConfigurationGuardTest {
    @Test
    void seedingRequiresDemoProfileButDisabledEnvironmentsRemainUsable() {
        MockEnvironment environment = new MockEnvironment();
        assertThatThrownBy(() -> new DemoConfigurationGuard(environment,true))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new DemoConfigurationGuard(environment,false)).doesNotThrowAnyException();
        environment.setActiveProfiles("demo");
        assertThatCode(() -> new DemoConfigurationGuard(environment,true)).doesNotThrowAnyException();
    }
}
