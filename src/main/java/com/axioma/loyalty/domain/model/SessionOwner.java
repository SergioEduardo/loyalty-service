package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Sesión con los datos de su cuenta y negocio que se devuelven al iniciar o renovar. */
public record SessionOwner(
        UUID sessionId,
        UUID tenantId,
        UUID userId,
        String userName,
        UserRole role,
        String tenantSlug,
        String tenantName) {
}
