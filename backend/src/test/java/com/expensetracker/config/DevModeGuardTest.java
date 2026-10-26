package com.expensetracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevModeGuardTest {

    @Test
    void devModeIsAllowedOnTheLocalProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        assertThatCode(() -> new DevModeGuard(true, env)).doesNotThrowAnyException();
    }

    @Test
    void devModeOffIsAlwaysFine() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatCode(() -> new DevModeGuard(false, env)).doesNotThrowAnyException();
    }

    @Test
    void devModeOutsideLocalRefusesToStart() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new DevModeGuard(true, env)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devModeOnRailwayRefusesToStartEvenWithTheLocalProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("RAILWAY_ENVIRONMENT", "production");

        assertThatThrownBy(() -> new DevModeGuard(true, env)).isInstanceOf(IllegalStateException.class);
    }
}
