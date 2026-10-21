package com.axioma.loyalty.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Verificación creada (antes del envío). */
public record IssuedVerification(UUID id, Instant expiresAt) {
}
