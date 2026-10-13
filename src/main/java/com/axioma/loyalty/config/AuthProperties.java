package com.axioma.loyalty.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sesiones del personal. Sin una clave de firma de al menos 32 bytes el servicio arranca, pero
 * el inicio de sesión responde 503. toString nunca incluye la clave.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSigningKey,
        @DefaultValue("900") long accessTokenTtlSeconds,
        @DefaultValue("30") int refreshTokenTtlDays,
        @DefaultValue("5") int loginMaxAttempts,
        @DefaultValue("15") int loginWindowMinutes) {

    public static final int MIN_KEY_BYTES = 32;

    public AuthProperties {
        jwtSigningKey = jwtSigningKey == null ? "" : jwtSigningKey;
    }

    public boolean hasUsableSigningKey() {
        return jwtSigningKey.getBytes(StandardCharsets.UTF_8).length >= MIN_KEY_BYTES;
    }

    @Override
    public String toString() {
        return "AuthProperties[jwtSigningKey=***, accessTokenTtlSeconds=" + accessTokenTtlSeconds
                + ", refreshTokenTtlDays=" + refreshTokenTtlDays + ", loginMaxAttempts=" + loginMaxAttempts
                + ", loginWindowMinutes=" + loginWindowMinutes + "]";
    }
}
