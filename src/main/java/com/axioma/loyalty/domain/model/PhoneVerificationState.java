package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Estado de una verificación al pedir la tarjeta; expired y verified según el reloj de la base. */
public record PhoneVerificationState(UUID id, int attempts, boolean verified, boolean expired) {
}
