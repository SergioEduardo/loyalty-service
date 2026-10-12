package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import org.springframework.http.HttpStatus;

final class ErrorHttpStatus {

    private ErrorHttpStatus() {
    }

    static HttpStatus of(ErrorCode code) {
        return switch (code) {
            case INVALID_SLUG, INVALID_TEXT, INVALID_BRAND_COLOR, INVALID_SETTINGS, INVALID_USER,
                 WEAK_PASSWORD, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case TENANT_NOT_FOUND, USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SLUG_ALREADY_EXISTS, USERNAME_TAKEN -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
