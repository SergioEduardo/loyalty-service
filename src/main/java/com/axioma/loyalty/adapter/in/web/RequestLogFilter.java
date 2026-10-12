package com.axioma.loyalty.adapter.in.web;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Asigna un requestId a cada petición (o respeta el que llegue), lo devuelve en la
 * respuesta y registra método, ruta, estado y duración. Nunca registra la query string
 * ni los encabezados, para no filtrar tokens.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter implements WebFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = resolveRequestId(exchange);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        long start = System.nanoTime();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();

        return chain.filter(exchange)
                .doFinally(signal -> {
                    if (path.startsWith("/actuator")) {
                        return;
                    }
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    var status = exchange.getResponse().getStatusCode();
                    log.info("requestId={} method={} path={} status={} durationMs={}",
                            requestId, method, path, status == null ? "-" : status.value(), ms);
                });
    }

    private static String resolveRequestId(ServerWebExchange exchange) {
        String incoming = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (incoming != null && incoming.matches("[A-Za-z0-9\\-]{8,64}")) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
