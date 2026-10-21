package com.axioma.loyalty.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envío del código por WhatsApp Cloud API (POST {apiBaseUrl}/{graphApiVersion}/{phoneNumberId}/messages)
 * con la plantilla de autenticación templateName en templateLanguage. accessToken (WHATSAPP_ACCESS_TOKEN)
 * viene de Secret Manager. timeoutMs es el tope de cada intento. Los valores vacíos toman su default;
 * los mal formados no arrancan. toString nunca incluye el token.
 */
@ConfigurationProperties(prefix = "app.verification.whatsapp")
public record WhatsAppProperties(
        String accessToken,
        String phoneNumberId,
        String templateName,
        String templateLanguage,
        String graphApiVersion,
        Long timeoutMs,
        String apiBaseUrl) {

    public static final String DEFAULT_TEMPLATE_NAME = "yiupi_codigo_verificacion";
    public static final String DEFAULT_TEMPLATE_LANGUAGE = "es_MX";
    public static final String DEFAULT_GRAPH_API_VERSION = "v26.0";
    public static final long DEFAULT_TIMEOUT_MS = 5_000L;
    public static final String DEFAULT_API_BASE_URL = "https://graph.facebook.com";

    public WhatsAppProperties {
        accessToken = blankToNull(accessToken);
        phoneNumberId = blankToNull(phoneNumberId);
        templateName = blankToDefault(templateName, DEFAULT_TEMPLATE_NAME);
        templateLanguage = blankToDefault(templateLanguage, DEFAULT_TEMPLATE_LANGUAGE);
        graphApiVersion = blankToDefault(graphApiVersion, DEFAULT_GRAPH_API_VERSION);
        timeoutMs = timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;
        apiBaseUrl = blankToDefault(apiBaseUrl, DEFAULT_API_BASE_URL).replaceAll("/+$", "");

        if (phoneNumberId != null && !phoneNumberId.matches("[0-9]{1,30}")) {
            throw new IllegalArgumentException("WHATSAPP_PHONE_NUMBER_ID debe ser numérico");
        }
        if (!templateName.matches("[a-z0-9_]{1,512}")) {
            throw new IllegalArgumentException("WHATSAPP_TEMPLATE_NAME solo admite minúsculas, números y guion bajo");
        }
        if (!templateLanguage.matches("[a-z]{2,3}(_[A-Z]{2})?")) {
            throw new IllegalArgumentException("WHATSAPP_TEMPLATE_LANGUAGE debe tener la forma es o es_MX");
        }
        if (!graphApiVersion.matches("v[0-9]{1,3}\\.[0-9]{1,2}")) {
            throw new IllegalArgumentException("WHATSAPP_GRAPH_API_VERSION debe tener la forma v26.0");
        }
        if (timeoutMs < 1 || timeoutMs > 30_000) {
            throw new IllegalArgumentException("WHATSAPP_TIMEOUT_MS debe estar entre 1 y 30000");
        }
        requireHttpUrl(apiBaseUrl, "WHATSAPP_API_BASE_URL");
    }

    /** Variables obligatorias que faltan para enviar por WhatsApp (solo los nombres). */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (accessToken == null) {
            missing.add("WHATSAPP_ACCESS_TOKEN");
        }
        if (phoneNumberId == null) {
            missing.add("WHATSAPP_PHONE_NUMBER_ID");
        }
        return missing;
    }

    public Duration timeout() {
        return Duration.ofMillis(timeoutMs);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    static void requireHttpUrl(String value, String variable) {
        URI uri = URI.create(value);
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
            throw new IllegalArgumentException(variable + " debe ser una URL http(s) absoluta");
        }
    }

    @Override
    public String toString() {
        return "WhatsAppProperties[accessToken=" + (accessToken == null ? "<vacía>" : "***")
                + ", phoneNumberId=" + phoneNumberId + ", templateName=" + templateName
                + ", templateLanguage=" + templateLanguage + ", graphApiVersion=" + graphApiVersion
                + ", timeoutMs=" + timeoutMs + ", apiBaseUrl=" + apiBaseUrl + "]";
    }
}
