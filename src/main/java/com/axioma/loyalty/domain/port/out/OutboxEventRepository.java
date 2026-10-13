package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.OutboxEvent;
import com.axioma.loyalty.domain.model.OutboxRetryPolicy;
import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Avisos del outbox por publicar. Reclamar un aviso lo aparta durante lease (corre su
 * next_attempt_at) en una transacción corta que salta los que otra ejecución está reclamando;
 * así ninguna otra ejecución lo toma mientras se publica y no se retienen bloqueos durante la
 * llamada a Pub/Sub. Si nadie lo marca antes de que venza el plazo, vuelve a estar disponible.
 */
public interface OutboxEventRepository {

    /** Hasta limit avisos PENDING vencidos, en orden de id. */
    Flux<OutboxEvent> claimDue(int limit, Duration lease);

    /** Los de esta lista que sigan PENDING y vencidos (el camino rápido), en orden de id. */
    Flux<OutboxEvent> claim(List<Long> eventIds, Duration lease);

    Mono<Void> markPublished(long eventId);

    /** Guarda los intentos y la próxima fecha, o lo deja FAILED si se agotaron. */
    Mono<Void> recordFailure(long eventId, OutboxRetryPolicy.Decision decision);
}
