package com.axioma.loyalty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Enlaces de activación. webBaseUrl es la URL de la PWA (APP_WEB_BASE_URL); sin ella la API admin
 * entrega el token sin URL. Los límites de intentos fallidos usan la misma ventana que el login.
 */
@ConfigurationProperties(prefix = "app.activation")
public record ActivationProperties(
        String webBaseUrl,
        @DefaultValue("72") int tokenTtlHours,
        @DefaultValue("5") int maxAttemptsPerIp,
        @DefaultValue("20") int maxAttemptsPerSlug,
        @DefaultValue("15") int windowMinutes) {

    public ActivationProperties {
        webBaseUrl = webBaseUrl == null || webBaseUrl.isBlank() ? null : webBaseUrl.strip().replaceAll("/+$", "");
    }
}
