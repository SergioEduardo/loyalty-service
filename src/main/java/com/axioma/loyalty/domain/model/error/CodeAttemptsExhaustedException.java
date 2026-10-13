package com.axioma.loyalty.domain.model.error;

/**
 * TOO_MANY_ATTEMPTS del alta (12.7): se agotaron los intentos de un código. No se arregla esperando
 * sino pidiendo otro código, así que responde 400 sin Retry-After y con su propio mensaje; el
 * TOO_MANY_ATTEMPTS del login y la activación (TooManyAttemptsException) sigue siendo 429.
 */
public class CodeAttemptsExhaustedException extends LoyaltyException {

    public static final String MESSAGE = "Demasiados intentos. Pide un código nuevo.";

    public CodeAttemptsExhaustedException() {
        super(ErrorCode.TOO_MANY_ATTEMPTS, MESSAGE);
    }
}
