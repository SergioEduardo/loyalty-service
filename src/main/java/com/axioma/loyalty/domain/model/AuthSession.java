package com.axioma.loyalty.domain.model;

/** Resultado de iniciar o renovar sesión. toString nunca incluye los tokens. */
public record AuthSession(String accessToken, String refreshToken, long expiresInSeconds, SessionOwner owner) {

    @Override
    public String toString() {
        return "AuthSession[sessionId=" + owner.sessionId() + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}
