package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.PublishPendingEventsCommand;
import com.axioma.loyalty.config.OutboxProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Camino rápido (12.11): después del commit, publica dentro de la misma petición el aviso que
 * escribió la operación, para que la entrega no dependa del CPU después de responder. Usa el mismo
 * reclamo que la red de seguridad. Tiene un tiempo máximo y nunca falla: si Pub/Sub falla o tarda,
 * el aviso queda pendiente para el Scheduler y la respuesta de la operación no cambia.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxFastPath {

    private final PublishPendingEventsHandler publishPendingEventsHandler;
    private final OutboxProperties properties;

    /** eventId null: la operación no escribió aviso. */
    public Mono<Void> publish(Long eventId) {
        if (eventId == null) {
            return Mono.empty();
        }
        return Mono.defer(() -> publishPendingEventsHandler.handle(PublishPendingEventsCommand.only(List.of(eventId))))
                .timeout(properties.fastPathTimeout())
                .onErrorResume(error -> {
                    log.warn("Outbox fast path left event pending id={} reason={}",
                            eventId, error.getClass().getSimpleName());
                    return Mono.empty();
                })
                .then();
    }
}
