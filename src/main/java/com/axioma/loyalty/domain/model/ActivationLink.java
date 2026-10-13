package com.axioma.loyalty.domain.model;

import java.time.Instant;

/**
 * Enlace de activación recién emitido. El token solo existe aquí y en la respuesta a la API
 * admin; en la base va su hash. url es null si no hay APP_WEB_BASE_URL. toString nunca incluye
 * el token.
 */
public record ActivationLink(String token, Instant expiresAt, String url) {

    @Override
    public String toString() {
        return "ActivationLink[expiresAt=" + expiresAt + ", token=***]";
    }
}
