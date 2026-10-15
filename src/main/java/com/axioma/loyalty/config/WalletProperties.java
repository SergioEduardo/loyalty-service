package com.axioma.loyalty.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Llamada al wallet-service (IssuePass). serviceUrl: base del servicio (WALLET_SERVICE_URL); sin ella,
 * el alta responde PASS_SERVICE_UNAVAILABLE. audience: audiencia del ID token (WALLET_SERVICE_AUDIENCE,
 * la URL base que valida el wallet-service); vacía = serviceUrl. issueTimeoutMs: tope de obtener el token y
 * de cada intento (hay un reintento); el defecto cubre un arranque en frío del wallet-service y su base.
 */
@ConfigurationProperties(prefix = "app.wallet")
public record WalletProperties(String serviceUrl, String audience, Long issueTimeoutMs) {

    public static final long DEFAULT_ISSUE_TIMEOUT_MS = 20_000L;

    public WalletProperties {
        serviceUrl = serviceUrl == null || serviceUrl.isBlank() ? "" : serviceUrl.strip().replaceAll("/+$", "");
        audience = audience == null || audience.isBlank() ? serviceUrl : audience.strip();
        issueTimeoutMs = issueTimeoutMs == null ? DEFAULT_ISSUE_TIMEOUT_MS : issueTimeoutMs;

        if (!serviceUrl.isEmpty()) {
            URI uri = URI.create(serviceUrl);
            if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                throw new IllegalArgumentException("WALLET_SERVICE_URL debe ser una URL http(s) absoluta");
            }
        }
        if (issueTimeoutMs <= 0) {
            throw new IllegalArgumentException("WALLET_ISSUE_TIMEOUT_MS debe ser positivo");
        }
    }

    public boolean configured() {
        return !serviceUrl.isEmpty();
    }

    public Duration issueTimeout() {
        return Duration.ofMillis(issueTimeoutMs);
    }
}
