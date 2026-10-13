package com.axioma.loyalty.domain.model.error;

/** RATE_LIMITED (alta de tarjetas): demasiadas solicitudes por IP, teléfono o negocio en la última hora. */
public class RateLimitedException extends LoyaltyException implements RetryAfter {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
