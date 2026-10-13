package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.OutboxEvent;
import reactor.core.publisher.Mono;

/** Publica avisos para el wallet-service (Pub/Sub). */
public interface EventPublisherPort {

    /** false si no hay tópico configurado: la publicación queda desactivada. */
    boolean isEnabled();

    /** Completa cuando el broker confirma el mensaje; falla si no lo confirma. */
    Mono<Void> publish(OutboxEvent event);
}
