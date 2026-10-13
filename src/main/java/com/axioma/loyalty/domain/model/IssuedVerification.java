package com.axioma.loyalty.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Verificación creada: lo que se responde al pedir un código. */
public record IssuedVerification(UUID id, Instant expiresAt) {
}
