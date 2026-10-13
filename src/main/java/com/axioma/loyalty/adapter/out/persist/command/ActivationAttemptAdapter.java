package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.LoginThrottle;
import com.axioma.loyalty.domain.port.out.ActivationAttemptStore;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Igual que LoginAttemptAdapter: la ventana y el tiempo restante salen del reloj de la base. */
@Component
@RequiredArgsConstructor
public class ActivationAttemptAdapter implements ActivationAttemptStore {

    private static final String FIND = SqlResource.load("sql/activation/find-throttle.sql");
    private static final String RECORD_FAILURE = SqlResource.load("sql/activation/record-failure.sql");
    private static final String CLEAR = SqlResource.load("sql/activation/clear-failures.sql");

    private final DatabaseClient databaseClient;

    @Override
    public Mono<LoginThrottle> find(String key, int windowMinutes) {
        return databaseClient.sql(FIND)
                .bind("key", key)
                .bind("windowMinutes", windowMinutes)
                .map(ActivationAttemptAdapter::toThrottle)
                .one();
    }

    @Override
    public Mono<LoginThrottle> recordFailure(String key, int windowMinutes) {
        return databaseClient.sql(RECORD_FAILURE)
                .bind("key", key)
                .bind("windowMinutes", windowMinutes)
                .map(ActivationAttemptAdapter::toThrottle)
                .one();
    }

    @Override
    public Mono<Void> clear(String key) {
        return databaseClient.sql(CLEAR).bind("key", key).fetch().rowsUpdated().then();
    }

    private static LoginThrottle toThrottle(Readable row) {
        return new LoginThrottle(row.get("failures", Integer.class), row.get("retry_after_seconds", Integer.class));
    }
}
