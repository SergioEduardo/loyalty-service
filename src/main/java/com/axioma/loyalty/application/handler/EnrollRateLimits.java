package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.config.EnrollProperties;
import com.axioma.loyalty.domain.model.error.RateLimitedException;
import com.axioma.loyalty.domain.port.out.EnrollRateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Límites del alta por hora con ventana fija (loyalty.enroll_rate_limits): solicitudes de código por IP
 * y por negocio, y altas por IP. Cada llamada cuenta, se acepte o no. La IP va a la clave como SHA-256,
 * nunca en claro, y no se registra en logs. Sin IP conocida no hay límite por IP (el de negocio y el de
 * teléfono no dependen de ella). Excedido: RATE_LIMITED (429 con Retry-After).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollRateLimits {

    private final EnrollRateLimiter limiter;
    private final EnrollProperties properties;

    public Mono<Void> checkVerificationIp(String clientIp) {
        return clientIp == null ? Mono.empty()
                : check("verify-ip:" + sha256(clientIp), properties.ipLimitPerHour(), "verification-ip", null);
    }

    public Mono<Void> checkVerificationTenant(UUID tenantId) {
        return check("verify-tenant:" + tenantId, properties.tenantLimitPerHour(), "verification-tenant", tenantId);
    }

    public Mono<Void> checkEnrollIp(String clientIp) {
        return clientIp == null ? Mono.empty()
                : check("enroll-ip:" + sha256(clientIp), properties.cardIpLimitPerHour(), "enroll-ip", null);
    }

    private Mono<Void> check(String key, int limit, String scope, UUID tenantId) {
        return limiter.hit(key, EnrollProperties.WINDOW_MINUTES)
                .filter(window -> window.failures() > limit)
                .flatMap(window -> {
                    log.info("Enrollment rate limited scope={} tenantId={}", scope, tenantId == null ? "-" : tenantId);
                    return Mono.<Void>error(new RateLimitedException(window.retryAfterSeconds()));
                });
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
