package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.LoginThrottle;
import reactor.core.publisher.Mono;

/** Fallos de activación por clave (slug, o slug + IP) dentro de una ventana de tiempo. */
public interface ActivationAttemptStore {

    /** Vacío si no hay fallos en la ventana vigente. */
    Mono<LoginThrottle> find(String key, int windowMinutes);

    Mono<LoginThrottle> recordFailure(String key, int windowMinutes);

    Mono<Void> clear(String key);
}
