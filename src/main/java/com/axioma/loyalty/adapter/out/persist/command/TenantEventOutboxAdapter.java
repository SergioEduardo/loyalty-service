package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.TenantChanged;
import com.axioma.loyalty.domain.port.out.TenantEventOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TenantEventOutboxAdapter implements TenantEventOutbox {

    private static final String INSERT_EVENT = SqlResource.load("sql/outbox/insert-event.sql");

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Long> append(TenantChanged event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(payload -> databaseClient.sql(INSERT_EVENT)
                        .bind("tenantId", event.tenantId())
                        .bind("eventType", TenantChanged.EVENT_TYPE)
                        .bind("payload", payload)
                        .map(row -> row.get("id", Long.class))
                        .one());
    }
}
