package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.PublishPendingEventsCommand;
import com.axioma.loyalty.application.validator.PublishPendingEventsValidator;
import com.axioma.loyalty.config.OutboxProperties;
import com.axioma.loyalty.domain.model.OutboxEvent;
import com.axioma.loyalty.domain.model.PublishReport;
import com.axioma.loyalty.domain.port.out.EventPublisherPort;
import com.axioma.loyalty.domain.port.out.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Publica avisos del outbox en Pub/Sub (diseño 12.11). Reclama los avisos en una transacción corta
 * (OutboxEventRepository) y publica cada uno sin bloqueos tomados: si Pub/Sub confirma, PUBLISHED;
 * si falla o no responde a tiempo, espera creciente o FAILED (OutboxRetryPolicy). Publicar dos
 * veces es inofensivo (el wallet-service ignora repetidos por su secuencia), pero el reclamo lo evita.
 * Solo registra conteos e ids, nunca el payload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishPendingEventsHandler implements Handler<PublishPendingEventsCommand, Mono<PublishReport>> {

    /** Publicaciones simultáneas por ejecución; con el lote de 50 y 10 s por aviso cabe en el plazo del reclamo. */
    static final int PUBLISH_CONCURRENCY = 10;

    private final PublishPendingEventsValidator validator;
    private final OutboxEventRepository outbox;
    private final EventPublisherPort publisher;
    private final OutboxProperties properties;

    @Override
    public Mono<PublishReport> handle(PublishPendingEventsCommand command) {
        return validator.validate(command)
                .then(Mono.defer(() -> publisher.isEnabled() ? publishClaimed(command) : Mono.just(PublishReport.DISABLED)));
    }

    private Mono<PublishReport> publishClaimed(PublishPendingEventsCommand command) {
        return claim(command)
                .flatMap(this::publishOne, PUBLISH_CONCURRENCY)
                .collectList()
                .map(results -> {
                    int published = (int) results.stream().filter(Boolean::booleanValue).count();
                    return PublishReport.of(published, results.size() - published);
                })
                .doOnNext(report -> {
                    if (report.claimed() > 0) {
                        log.info("Outbox publish claimed={} published={} failed={} targeted={}",
                                report.claimed(), report.published(), report.failed(), command.targeted());
                    }
                });
    }

    private Flux<OutboxEvent> claim(PublishPendingEventsCommand command) {
        return command.targeted()
                ? outbox.claim(command.eventIds(), properties.claimLease())
                : outbox.claimDue(command.batchSize(), properties.claimLease());
    }

    /** true si Pub/Sub lo confirmó. Los errores de la base (al marcarlo) sí se propagan. */
    private Mono<Boolean> publishOne(OutboxEvent event) {
        return Mono.defer(() -> publisher.publish(event))
                .timeout(properties.publishTimeout())
                .thenReturn(true)
                .onErrorResume(error -> recordFailure(event, error).thenReturn(false))
                .flatMap(published -> published
                        ? outbox.markPublished(event.id()).thenReturn(true)
                        : Mono.just(false));
    }

    private Mono<Void> recordFailure(OutboxEvent event, Throwable error) {
        var decision = properties.retryPolicy().afterFailure(event.attempts());
        if (decision.exhausted()) {
            // La alerta se arma después sobre este log.
            log.error("Outbox event FAILED id={} eventType={} tenantId={} cardId={} attempts={} error={}",
                    event.id(), event.eventType(), event.tenantId(), event.cardId(), decision.attempts(),
                    describe(error));
        } else {
            log.warn("Outbox publish failed id={} eventType={} attempts={} retryInSeconds={} error={}",
                    event.id(), event.eventType(), decision.attempts(), decision.retryDelay().toSeconds(),
                    describe(error));
        }
        return outbox.recordFailure(event.id(), decision);
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
    }
}
