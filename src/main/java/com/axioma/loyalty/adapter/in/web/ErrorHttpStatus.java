package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.domain.model.error.CodeAttemptsExhaustedException;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import org.springframework.http.HttpStatus;

final class ErrorHttpStatus {

    private ErrorHttpStatus() {
    }

    static HttpStatus of(LoyaltyException ex) {
        return ex instanceof CodeAttemptsExhaustedException ? HttpStatus.BAD_REQUEST : of(ex.getCode());
    }

    static HttpStatus of(ErrorCode code) {
        return switch (code) {
            case INVALID_SLUG, INVALID_TEXT, INVALID_BRAND_COLOR, INVALID_SETTINGS, INVALID_USER,
                 WEAK_PASSWORD, INVALID_REQUEST, INVALID_ACTIVATION, IMAGE_DIMENSIONS_TOO_LARGE, INVALID_IMAGE,
                 INVALID_PHONE, INVALID_NAME, INVALID_CODE, CODE_EXPIRED, CHANNEL_NOT_AVAILABLE,
                 RECIPIENT_UNREACHABLE -> HttpStatus.BAD_REQUEST;
            case LOGO_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_IMAGE_FORMAT -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case UNAUTHORIZED, INVALID_CREDENTIALS, INVALID_SESSION -> HttpStatus.UNAUTHORIZED;
            case TOO_MANY_ATTEMPTS, RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case AUTH_UNAVAILABLE, STORAGE_UNAVAILABLE, VERIFICATION_UNAVAILABLE, PASS_SERVICE_UNAVAILABLE ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case TENANT_NOT_FOUND, USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SLUG_ALREADY_EXISTS, USERNAME_TAKEN, USER_INACTIVE, TEMPLATE_NOT_READY -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
