package com.expensetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Refuses to start if OTP dev mode (OTP echoed in API responses) is enabled outside the local profile. */
@Component
public class DevModeGuard {

    public DevModeGuard(@Value("${app.otp.dev-mode:false}") boolean devOtpMode, Environment env) {
        if (!devOtpMode) return;
        boolean local = env.acceptsProfiles(Profiles.of("local"));
        boolean onRailway = env.getProperty("RAILWAY_ENVIRONMENT") != null;
        if (!local || onRailway) {
            throw new IllegalStateException(
                    "app.otp.dev-mode is enabled outside a local run. Refusing to start.");
        }
    }
}
