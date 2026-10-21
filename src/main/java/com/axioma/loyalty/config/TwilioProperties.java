package com.axioma.loyalty.config;

import static com.axioma.loyalty.config.WhatsAppProperties.blankToDefault;
import static com.axioma.loyalty.config.WhatsAppProperties.blankToNull;
import static com.axioma.loyalty.config.WhatsAppProperties.requireHttpUrl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envío del código por SMS con Twilio Programmable Messaging (POST
 * {apiBaseUrl}/2010-04-01/Accounts/{accountSid}/Messages.json, autenticación básica accountSid:authToken).
 * Remitente: messagingServiceSid (preferido) o from (E.164). authToken (TWILIO_AUTH_TOKEN) viene de Secret
 * Manager. timeoutMs es el tope del único intento. Los valores vacíos toman su default; los mal formados
 * no arrancan. toString nunca incluye el token.
 */
@ConfigurationProperties(prefix = "app.verification.twilio")
public record TwilioProperties(
        String accountSid,
        String authToken,
        String messagingServiceSid,
        String from,
        Long timeoutMs,
        String apiBaseUrl) {

    public static final long DEFAULT_TIMEOUT_MS = 5_000L;
    public static final String DEFAULT_API_BASE_URL = "https://api.twilio.com";

    public TwilioProperties {
        accountSid = blankToNull(accountSid);
        authToken = blankToNull(authToken);
        messagingServiceSid = blankToNull(messagingServiceSid);
        from = blankToNull(from);
        timeoutMs = timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;
        apiBaseUrl = blankToDefault(apiBaseUrl, DEFAULT_API_BASE_URL).replaceAll("/+$", "");

        if (accountSid != null && !accountSid.matches("AC[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("TWILIO_ACCOUNT_SID debe empezar con AC seguido de 32 caracteres hexadecimales");
        }
        if (messagingServiceSid != null && !messagingServiceSid.matches("MG[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException(
                    "TWILIO_MESSAGING_SERVICE_SID debe empezar con MG seguido de 32 caracteres hexadecimales");
        }
        if (from != null && !from.matches("\\+[1-9][0-9]{6,14}")) {
            throw new IllegalArgumentException("TWILIO_FROM debe ser un número E.164 (+525512345678)");
        }
        if (timeoutMs < 1 || timeoutMs > 30_000) {
            throw new IllegalArgumentException("TWILIO_TIMEOUT_MS debe estar entre 1 y 30000");
        }
        requireHttpUrl(apiBaseUrl, "TWILIO_API_BASE_URL");
    }

    /** Variables obligatorias que faltan para enviar por SMS (solo los nombres). */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (accountSid == null) {
            missing.add("TWILIO_ACCOUNT_SID");
        }
        if (authToken == null) {
            missing.add("TWILIO_AUTH_TOKEN");
        }
        if (messagingServiceSid == null && from == null) {
            missing.add("TWILIO_MESSAGING_SERVICE_SID o TWILIO_FROM");
        }
        return missing;
    }

    public Duration timeout() {
        return Duration.ofMillis(timeoutMs);
    }

    @Override
    public String toString() {
        return "TwilioProperties[accountSid=" + accountSid + ", authToken=" + (authToken == null ? "<vacía>" : "***")
                + ", messagingServiceSid=" + messagingServiceSid + ", from=" + (from == null ? "<vacía>" : "***")
                + ", timeoutMs=" + timeoutMs + ", apiBaseUrl=" + apiBaseUrl + "]";
    }
}
