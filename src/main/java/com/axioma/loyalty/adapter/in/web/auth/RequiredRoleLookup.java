package com.axioma.loyalty.adapter.in.web.auth;

import com.axioma.loyalty.domain.model.UserRole;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Encuentra el endpoint que atenderá la petición y lee su @RequireRole. */
@Component
public class RequiredRoleLookup {

    private final RequestMappingHandlerMapping handlerMapping;

    public RequiredRoleLookup(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    public Mono<Optional<UserRole>> requiredRole(ServerWebExchange exchange) {
        return handlerMapping.getHandler(exchange)
                .map(RequiredRoleLookup::requiredRole)
                // Sin endpoint (404, 405...) no hay rol que exigir: el dispatcher responde el error.
                .onErrorResume(e -> Mono.just(Optional.empty()))
                .defaultIfEmpty(Optional.empty());
    }

    /** La anotación del método tiene prioridad sobre la del controlador. */
    static Optional<UserRole> requiredRole(Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return Optional.empty();
        }
        RequireRole onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequireRole.class);
        if (onMethod != null) {
            return Optional.of(onMethod.value());
        }
        RequireRole onType = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequireRole.class);
        return Optional.ofNullable(onType).map(RequireRole::value);
    }
}
