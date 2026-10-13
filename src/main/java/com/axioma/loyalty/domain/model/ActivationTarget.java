package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Cuenta y negocio a los que pertenece un enlace de activación válido. */
public record ActivationTarget(UUID tenantId, UUID userId, String tenantSlug, String tenantName, String username) {
}
