package com.axioma.loyalty.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Sesión por crear. Solo lleva el hash del token de renovación. */
public record NewSession(UUID id, UUID tenantId, UUID userId, String refreshTokenHash, Instant expiresAt) {

    @Override
    public String toString() {
        return "NewSession[id=" + id + ", tenantId=" + tenantId + ", userId=" + userId + "]";
    }
}
