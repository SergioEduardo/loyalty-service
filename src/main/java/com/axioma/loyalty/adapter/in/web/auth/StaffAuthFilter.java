package com.axioma.loyalty.adapter.in.web.auth;

import com.axioma.loyalty.adapter.in.web.ApiErrorWriter;
import com.axioma.loyalty.adapter.in.web.RequestPaths;
import com.axioma.loyalty.application.handler.AuthenticateStaffHandler;
import com.axioma.loyalty.application.query.AuthenticateStaffQuery;
import com.axioma.loyalty.domain.model.StaffPrincipal;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
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
 * Protege /api/staff/** y /api/auth/me: exige un access token válido de una sesión vigente,
 * aplica @RequireRole y deja el StaffPrincipal en el atributo PRINCIPAL_ATTRIBUTE.
 * Cualquier fallo del token responde UNAUTHORIZED, sin decir qué parte falló.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class StaffAuthFilter implements WebFilter {

    public static final String PRINCIPAL_ATTRIBUTE = "loyalty.staffPrincipal";
    private static final String BEARER_PREFIX = "bearer ";

    private final AuthenticateStaffHandler authenticateStaffHandler;
    private final RequiredRoleLookup requiredRoleLookup;
    private final ApiErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isStaffPath(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }
        String token = bearerToken(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        return authenticateStaffHandler.handle(new AuthenticateStaffQuery(token))
                .flatMap(principal -> authorize(exchange, principal))
                .flatMap(principal -> {
                    exchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, principal);
                    return chain.filter(exchange);
                })
                .onErrorResume(LoyaltyException.class, e -> {
                    log.info("Staff access denied code={}", e.getCode());
                    return errorWriter.write(exchange, e.getCode());
                });
    }

    private Mono<StaffPrincipal> authorize(ServerWebExchange exchange, StaffPrincipal principal) {
        return requiredRoleLookup.requiredRole(exchange)
                .flatMap(required -> required.isEmpty() || principal.hasRole(required.get())
                        ? Mono.just(principal)
                        : Mono.error(new LoyaltyException(ErrorCode.FORBIDDEN)));
    }

    static boolean isStaffPath(PathContainer path) {
        return RequestPaths.isUnder(path, "/api/staff") || RequestPaths.isUnder(path, "/api/auth/me");
    }

    private static String bearerToken(String header) {
        if (header == null || header.length() <= BEARER_PREFIX.length()
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }
}
