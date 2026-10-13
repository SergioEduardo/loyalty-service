package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.LoginThrottle;
import reactor.core.publisher.Mono;

/** Contadores de los límites del alta por clave, con ventana fija (loyalty.enroll_rate_limits). */
public interface EnrollRateLimiter {

    /** Suma una solicitud y devuelve las de la ventana vigente (incluida esta) y lo que le falta. */
    Mono<LoginThrottle> hit(String key, int windowMinutes);
}
