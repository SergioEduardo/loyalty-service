package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import org.springframework.http.HttpStatus;

final class ErrorHttpStatus {

    private ErrorHttpStatus() {
    }

    static HttpStatus of(ErrorCode code) {
        return switch (code) {
            case INVALID_SLUG, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case TENANT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
