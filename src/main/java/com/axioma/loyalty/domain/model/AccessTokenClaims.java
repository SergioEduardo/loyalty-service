package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Claims del access token ya verificados (firma, issuer y vigencia). */
public record AccessTokenClaims(UUID userId, UUID tenantId, UserRole role, UUID sessionId) {
}
