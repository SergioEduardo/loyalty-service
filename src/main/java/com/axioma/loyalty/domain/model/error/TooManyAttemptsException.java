package com.axioma.loyalty.domain.model.error;

/** TOO_MANY_ATTEMPTS con los segundos que faltan para poder intentar de nuevo. */
public class TooManyAttemptsException extends LoyaltyException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_ATTEMPTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
