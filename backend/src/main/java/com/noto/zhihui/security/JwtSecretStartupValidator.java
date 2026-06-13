package com.noto.zhihui.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Component
public class JwtSecretStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretStartupValidator.class);
    private static final String DEFAULT_SECRET = "noto-zhihui-default-secret-key-32chars";

    private final Environment environment;
    private final String jwtSecret;

    public JwtSecretStartupValidator(
            Environment environment,
            @Value("${noto.security.jwt-secret:" + DEFAULT_SECRET + "}") String jwtSecret
    ) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        boolean prodProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prodProfile) {
            if (DEFAULT_SECRET.equals(jwtSecret)) {
                log.warn("Using default JWT secret in non-prod profile. Set NOTO_JWT_SECRET for production.");
            }
            return;
        }
        if (!StringUtils.hasText(jwtSecret) || DEFAULT_SECRET.equals(jwtSecret) || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "Production requires NOTO_JWT_SECRET with at least 32 random characters. Default secret is not allowed."
            );
        }
    }
}
