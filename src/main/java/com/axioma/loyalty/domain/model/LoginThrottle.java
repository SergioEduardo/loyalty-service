package com.axioma.loyalty.domain.model;

/** Fallos dentro de la ventana vigente y segundos que faltan para que venza. */
public record LoginThrottle(int failures, long retryAfterSeconds) {
}
