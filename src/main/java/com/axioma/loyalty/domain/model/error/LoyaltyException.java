package com.axioma.loyalty.domain.model.error;

public class LoyaltyException extends RuntimeException {

    private final ErrorCode code;

    public LoyaltyException(ErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public LoyaltyException(ErrorCode code, Throwable cause) {
        super(code.defaultMessage(), cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
