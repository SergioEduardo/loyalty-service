package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.query.InspectActivationQuery;
import com.axioma.loyalty.domain.model.ActivationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * A qué cuenta y negocio pertenece un enlace, para mostrarlo antes de elegir contraseña. Cualquier
 * fallo responde INVALID_ACTIVATION, sin decir la causa, y cuenta para el límite de intentos.
 */
@Service
@RequiredArgsConstructor
public class InspectActivationHandler implements Handler<InspectActivationQuery, Mono<ActivationTarget>> {

    private final ActivationThrottle throttle;
    private final ActivationLookup lookup;

    @Override
    public Mono<ActivationTarget> handle(InspectActivationQuery query) {
        var limits = throttle.limits(query.slug(), query.clientIp());
        return throttle.ensureAllowed(limits)
                .then(Mono.defer(() -> lookup.findValid(query.slug(), query.token())))
                .switchIfEmpty(Mono.defer(() -> throttle.reject(limits)));
    }
}
