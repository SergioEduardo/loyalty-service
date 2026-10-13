package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.adapter.in.web.ApiErrorWriter;
import com.axioma.loyalty.adapter.in.web.RequestPaths;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
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
    private final ApiErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isAdminPath(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return verifier.verify(authorization)
                .map(email -> Optional.<ErrorCode>empty())
                .onErrorResume(LoyaltyException.class, e -> Mono.just(Optional.of(e.getCode())))
                .flatMap(denied -> {
                    if (denied.isEmpty()) {
                        return chain.filter(exchange);
                    }
                    log.warn("Admin access denied code={}", denied.get());
                    return errorWriter.write(exchange, denied.get());
                });
    }

    static boolean isAdminPath(PathContainer path) {
        return RequestPaths.isUnder(path, ADMIN_PREFIX);
    }
}
