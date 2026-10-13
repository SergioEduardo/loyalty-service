package com.axioma.loyalty.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alta de tarjetas (12.6 y 12.7). Límites por hora: códigos por (negocio, teléfono), solicitudes de
 * código por IP y por negocio, y altas por IP. otpHashSalt (OTP_HASH_SALT, Secret Manager
 * otp-hmac-salt) es la llave del HMAC del código; sin ella, o con menos de 32 bytes, pedir un código o
 * la tarjeta responde VERIFICATION_UNAVAILABLE. verificationSender: disabled | log | whatsapp; "log"
 * solo con appEnv = dev. Los números vacíos (una variable de GitHub sin definir llega como "") toman su
 * default. toString nunca incluye la sal.
 */
@ConfigurationProperties(prefix = "app.enroll")
public record EnrollProperties(
        Integer phoneLimitPerHour,
        Integer ipLimitPerHour,
        Integer tenantLimitPerHour,
        Integer cardIpLimitPerHour,
        Integer codeTtlMinutes,
        Integer maxAttempts,
        String otpHashSalt,
        String verificationSender,
        String appEnv) {

    public static final int MIN_SALT_BYTES = 32;
    public static final int WINDOW_MINUTES = 60;

    public EnrollProperties {
        phoneLimitPerHour = phoneLimitPerHour == null ? 3 : phoneLimitPerHour;
        ipLimitPerHour = ipLimitPerHour == null ? 10 : ipLimitPerHour;
        tenantLimitPerHour = tenantLimitPerHour == null ? 60 : tenantLimitPerHour;
        cardIpLimitPerHour = cardIpLimitPerHour == null ? 30 : cardIpLimitPerHour;
        codeTtlMinutes = codeTtlMinutes == null ? 10 : codeTtlMinutes;
        maxAttempts = maxAttempts == null ? 5 : maxAttempts;
        otpHashSalt = otpHashSalt == null || otpHashSalt.isBlank() ? null : otpHashSalt;
        verificationSender = verificationSender == null || verificationSender.isBlank()
                ? "disabled" : verificationSender.strip().toLowerCase(Locale.ROOT);
        appEnv = appEnv == null ? "" : appEnv.strip().toLowerCase(Locale.ROOT);

        if (phoneLimitPerHour < 1 || ipLimitPerHour < 1 || tenantLimitPerHour < 1 || cardIpLimitPerHour < 1) {
            throw new IllegalArgumentException("Los límites ENROLL_*_LIMIT_PER_HOUR deben ser positivos");
        }
        if (codeTtlMinutes < 1 || codeTtlMinutes > 60) {
            throw new IllegalArgumentException("ENROLL_CODE_TTL_MINUTES debe estar entre 1 y 60");
        }
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalArgumentException("ENROLL_MAX_ATTEMPTS debe estar entre 1 y 20");
        }
    }

    /** La sal existe y tiene al menos 32 bytes. */
    public boolean saltConfigured() {
        return otpHashSalt != null && otpHashSalt.getBytes(StandardCharsets.UTF_8).length >= MIN_SALT_BYTES;
    }

    public boolean devEnvironment() {
        return "dev".equals(appEnv);
    }

    @Override
    public String toString() {
        return "EnrollProperties[phoneLimitPerHour=" + phoneLimitPerHour + ", ipLimitPerHour=" + ipLimitPerHour
                + ", tenantLimitPerHour=" + tenantLimitPerHour + ", cardIpLimitPerHour=" + cardIpLimitPerHour
                + ", codeTtlMinutes=" + codeTtlMinutes + ", maxAttempts=" + maxAttempts
                + ", otpHashSalt=" + (otpHashSalt == null ? "<vacía>" : "***")
                + ", verificationSender=" + verificationSender + ", appEnv=" + appEnv + "]";
    }
}
