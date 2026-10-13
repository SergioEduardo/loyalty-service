package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.OutboxEvent;
import com.axioma.loyalty.domain.model.OutboxRetryPolicy;
import com.axioma.loyalty.domain.port.out.OutboxEventRepository;
import io.r2dbc.spi.Readable;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reclamo y cierre de avisos del outbox. Cada sentencia es su propia transacción (autocommit):
 * no se debe llamar dentro de una transacción larga, o el bloqueo del reclamo duraría toda la publicación.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventAdapter implements OutboxEventRepository {

    private static final String CLAIM_DUE = SqlResource.load("sql/outbox/claim-due.sql");
    private static final String CLAIM_BY_IDS = SqlResource.load("sql/outbox/claim-by-ids.sql");
    private static final String MARK_PUBLISHED = SqlResource.load("sql/outbox/mark-published.sql");
    private static final String RECORD_FAILURE = SqlResource.load("sql/outbox/record-failure.sql");

    private final DatabaseClient databaseClient;

    @Override
    public Flux<OutboxEvent> claimDue(int limit, Duration lease) {
        return inIdOrder(databaseClient.sql(CLAIM_DUE)
                .bind("limit", limit)
                .bind("leaseSeconds", lease.toSeconds())
                .map(OutboxEventAdapter::toEvent)
                .all());
    }

    @Override
    public Flux<OutboxEvent> claim(List<Long> eventIds, Duration lease) {
        return inIdOrder(databaseClient.sql(CLAIM_BY_IDS)
                .bind("ids", eventIds.toArray(Long[]::new))
                .bind("leaseSeconds", lease.toSeconds())
                .map(OutboxEventAdapter::toEvent)
                .all());
    }

    @Override
    public Mono<Void> markPublished(long eventId) {
        return databaseClient.sql(MARK_PUBLISHED)
                .bind("id", eventId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> recordFailure(long eventId, OutboxRetryPolicy.Decision decision) {
        return databaseClient.sql(RECORD_FAILURE)
                .bind("id", eventId)
                .bind("attempts", decision.attempts())
                .bind("status", decision.exhausted() ? "FAILED" : "PENDING")
                .bind("delaySeconds", decision.retryDelay().toSeconds())
                .fetch()
                .rowsUpdated()
                .then();
    }

    /** RETURNING no garantiza orden. */
    private static Flux<OutboxEvent> inIdOrder(Flux<OutboxEvent> events) {
        return events.sort(Comparator.comparingLong(OutboxEvent::id));
    }

    private static OutboxEvent toEvent(Readable row) {
        return new OutboxEvent(
                row.get("id", Long.class),
                row.get("tenant_id", UUID.class),
                row.get("card_id", UUID.class),
                row.get("event_type", String.class),
                row.get("payload", String.class),
                row.get("attempts", Integer.class));
    }
}
