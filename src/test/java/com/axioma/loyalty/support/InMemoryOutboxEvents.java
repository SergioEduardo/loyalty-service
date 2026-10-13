package com.axioma.loyalty.support;

import com.axioma.loyalty.domain.model.OutboxEvent;
import com.axioma.loyalty.domain.model.OutboxRetryPolicy;
import com.axioma.loyalty.domain.port.out.OutboxEventRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fake de OutboxEventRepository sin reloj: un aviso PENDING está disponible si nadie lo tiene
 * reclamado; marcarlo (publicado o fallido) lo libera. Los plazos reales se prueban con Postgres.
 */
public class InMemoryOutboxEvents implements OutboxEventRepository {

    public record Row(OutboxEvent event, String status, boolean claimed, Duration lastRetryDelay) {
    }

    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, Row> rows = new ConcurrentSkipListMap<>();

    public synchronized long add(UUID tenantId, String eventType, String payload) {
        long id = sequence.incrementAndGet();
        rows.put(id, new Row(new OutboxEvent(id, tenantId, null, eventType, payload, 0), "PENDING", false, null));
        return id;
    }

    public Row row(long id) {
        return rows.get(id);
    }

    @Override
    public synchronized Flux<OutboxEvent> claimDue(int limit, Duration lease) {
        return Flux.fromIterable(rows.values().stream()
                .filter(row -> row.status().equals("PENDING") && !row.claimed())
                .limit(limit)
                .map(this::claim)
                .toList());
    }

    @Override
    public synchronized Flux<OutboxEvent> claim(List<Long> eventIds, Duration lease) {
        return Flux.fromIterable(rows.values().stream()
                .filter(row -> eventIds.contains(row.event().id()))
                .filter(row -> row.status().equals("PENDING") && !row.claimed())
                .map(this::claim)
                .toList());
    }

    @Override
    public synchronized Mono<Void> markPublished(long eventId) {
        return Mono.fromRunnable(() -> rows.computeIfPresent(eventId,
                (id, row) -> new Row(row.event(), "PUBLISHED", false, row.lastRetryDelay())));
    }

    @Override
    public synchronized Mono<Void> recordFailure(long eventId, OutboxRetryPolicy.Decision decision) {
        return Mono.fromRunnable(() -> rows.computeIfPresent(eventId, (id, row) -> {
            OutboxEvent e = row.event();
            return new Row(new OutboxEvent(e.id(), e.tenantId(), e.cardId(), e.eventType(), e.payload(),
                    decision.attempts()), decision.exhausted() ? "FAILED" : "PENDING", false, decision.retryDelay());
        }));
    }

    private OutboxEvent claim(Row row) {
        rows.put(row.event().id(), new Row(row.event(), row.status(), true, row.lastRetryDelay()));
        return row.event();
    }
}
