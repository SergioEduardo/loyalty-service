package com.axioma.loyalty.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quién puede usar la API admin: ID tokens de Google con esta audiencia y uno de estos correos.
 * Si falta cualquiera de los dos, la API admin queda cerrada.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String audience, List<String> allowedEmails) {

    public AdminProperties {
        audience = audience == null ? "" : audience.trim();
        allowedEmails = allowedEmails == null ? List.of() : allowedEmails.stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .toList();
    }

    public boolean isConfigured() {
        return !audience.isEmpty() && !allowedEmails.isEmpty();
    }
}
