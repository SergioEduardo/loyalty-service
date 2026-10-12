package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Traduce errores a respuestas HTTP. Los errores técnicos nunca llegan al cliente. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LoyaltyException.class)
    public ResponseEntity<ApiError> handleBusiness(LoyaltyException ex) {
        HttpStatus status = ErrorHttpStatus.of(ex.getCode());
        if (status.is5xxServerError()) {
            log.error("Business error code={}", ex.getCode(), ex);
        } else {
            log.info("Business error code={}", ex.getCode());
        }
        return ResponseEntity.status(status)
                .body(new ApiError(ex.getCode().name(), ex.getMessage()));
    }

    /** Rutas inexistentes, cuerpos mal formados, parámetros inválidos. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex) {
        ErrorCode code = ex.getStatusCode().is4xxClientError()
                ? ErrorCode.INVALID_REQUEST
                : ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiError(code.name(), code.defaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(code.name(), code.defaultMessage()));
    }
}
