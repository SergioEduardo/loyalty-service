package com.axioma.loyalty.adapter.in.web.internal;

import com.axioma.loyalty.adapter.in.web.ApiErrorWriter;
import com.axioma.loyalty.adapter.in.web.GoogleIdTokenVerifier;
import com.axioma.loyalty.adapter.in.web.RequestPaths;
import com.axioma.loyalty.config.OutboxProperties;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.PathContainer;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Protege /internal/ (hoy solo /internal/outbox/publish, que llama Cloud Scheduler): exige un ID token
 * de Google con OUTBOX_PUBLISH_AUDIENCE y un correo de OUTBOX_PUBLISH_ALLOWED_EMAILS. Sin ellas,
 * todo /internal/ queda cerrado (403). Nunca registra el token.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalAuthFilter implements WebFilter {

    private static final String INTERNAL_PREFIX = "/internal";

    private final GoogleIdTokenVerifier verifier;
    private final ApiErrorWriter errorWriter;

    public InternalAuthFilter(ReactiveJwtDecoder decoder, OutboxProperties properties, ApiErrorWriter errorWriter) {
        this.verifier = new GoogleIdTokenVerifier(decoder, properties.publishAudience(), properties.publishAllowedEmails());
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isInternalPath(exchange.getRequest().getPath().pathWithinApplication())) {
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
                    log.warn("Internal access denied code={}", denied.get());
                    return errorWriter.write(exchange, denied.get());
                });
    }

    static boolean isInternalPath(PathContainer path) {
        return RequestPaths.isUnder(path, INTERNAL_PREFIX);
    }
}
