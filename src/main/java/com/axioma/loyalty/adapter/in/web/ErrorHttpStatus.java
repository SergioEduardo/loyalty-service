package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import org.springframework.http.HttpStatus;

final class ErrorHttpStatus {

    private ErrorHttpStatus() {
    }

    static HttpStatus of(ErrorCode code) {
        return switch (code) {
            case INVALID_SLUG, INVALID_TEXT, INVALID_BRAND_COLOR, INVALID_SETTINGS, INVALID_USER,
                 WEAK_PASSWORD, INVALID_REQUEST, IMAGE_DIMENSIONS_TOO_LARGE, INVALID_IMAGE -> HttpStatus.BAD_REQUEST;
            case LOGO_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_IMAGE_FORMAT -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case UNAUTHORIZED, INVALID_CREDENTIALS, INVALID_SESSION -> HttpStatus.UNAUTHORIZED;
            case TOO_MANY_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
            case AUTH_UNAVAILABLE, STORAGE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case TENANT_NOT_FOUND, USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SLUG_ALREADY_EXISTS, USERNAME_TAKEN -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
