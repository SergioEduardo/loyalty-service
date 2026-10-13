package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.LoginThrottle;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Fallos de inicio de sesión por (negocio, usuario) dentro de una ventana de tiempo. */
public interface LoginAttemptStore {

    /** Vacío si no hay fallos en la ventana vigente. */
    Mono<LoginThrottle> find(UUID tenantId, String username, int windowMinutes);

    Mono<LoginThrottle> recordFailure(UUID tenantId, String username, int windowMinutes);

    Mono<Void> clear(UUID tenantId, String username);
}
