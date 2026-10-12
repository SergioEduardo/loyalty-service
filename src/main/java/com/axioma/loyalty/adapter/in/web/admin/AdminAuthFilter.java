package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.adapter.in.web.ApiError;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Protege /api/admin/: exige un ID token de Google válido (ver AdminTokenVerifier).
 * Corre después de RequestLogFilter para que el rechazo quede en el log. Nunca registra el token.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AdminAuthFilter implements WebFilter {

    private static final String ADMIN_PREFIX = "/api/admin";

    private final AdminTokenVerifier verifier;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isAdminPath(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return verifier.verify(authorization)
                .map(email -> Optional.<ErrorCode>empty())
                .onErrorResume(LoyaltyException.class, e -> Mono.just(Optional.of(e.getCode())))
                .flatMap(denied -> denied.isPresent()
                        ? reject(exchange, denied.get())
                        : chain.filter(exchange));
    }

    /**
     * Compara con la ruta decodificada y sin segmentos vacíos, igual que el ruteo de Spring,
     * para que /api/%61dmin o //api/admin no se salten el filtro.
     */
    static boolean isAdminPath(PathContainer path) {
        StringBuilder normalized = new StringBuilder();
        for (PathContainer.Element element : path.elements()) {
            if (element instanceof PathContainer.PathSegment segment && !segment.valueToMatch().isEmpty()) {
                normalized.append('/').append(segment.valueToMatch());
            }
        }
        String value = normalized.toString();
        return value.equals(ADMIN_PREFIX) || value.startsWith(ADMIN_PREFIX + "/");
    }

    private Mono<Void> reject(ServerWebExchange exchange, ErrorCode code) {
        HttpStatus status = code == ErrorCode.FORBIDDEN ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
        log.warn("Admin access denied code={}", code);
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
