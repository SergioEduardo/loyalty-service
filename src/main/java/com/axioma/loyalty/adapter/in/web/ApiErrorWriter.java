package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Escribe un ApiError desde un WebFilter, con el mismo formato y estado que GlobalExceptionHandler. */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code) {
        HttpStatus status = ErrorHttpStatus.of(code);
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(new ApiError(code.name(), code.defaultMessage()));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
