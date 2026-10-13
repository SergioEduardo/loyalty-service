package com.axioma.loyalty.domain.model;

import java.time.Duration;

/**
 * Qué hacer con un aviso cuya publicación falló: la n-ésima falla espera 5 s × 2^(n−1) (5 s, 10 s,
 * 20 s, ...) con tope de 15 min, y al llegar a maxAttempts intentos el aviso queda FAILED.
 */
public record OutboxRetryPolicy(int maxAttempts) {

    static final Duration BASE_DELAY = Duration.ofSeconds(5);
    static final Duration MAX_DELAY = Duration.ofMinutes(15);

    public OutboxRetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser al menos 1");
        }
    }

    /** previousAttempts: intentos fallidos antes de este. */
    public Decision afterFailure(int previousAttempts) {
        int attempts = previousAttempts + 1;
        return new Decision(attempts, attempts >= maxAttempts, delayFor(previousAttempts));
    }

    static Duration delayFor(int previousAttempts) {
        // 5 s × 2^17 ya pasa del tope; acotar el exponente evita el desbordamiento.
        int exponent = Math.min(Math.max(previousAttempts, 0), 17);
        Duration delay = BASE_DELAY.multipliedBy(1L << exponent);
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    /** attempts: intentos fallidos contando este; exhausted: pasa a FAILED. */
    public record Decision(int attempts, boolean exhausted, Duration retryDelay) {
    }
}
