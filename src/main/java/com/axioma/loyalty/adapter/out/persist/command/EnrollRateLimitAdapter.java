package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.LoginThrottle;
import com.axioma.loyalty.domain.port.out.EnrollRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Igual que ActivationAttemptAdapter: una sentencia atómica; la ventana sale del reloj de la base. */
@Component
@RequiredArgsConstructor
public class EnrollRateLimitAdapter implements EnrollRateLimiter {

    private static final String HIT = SqlResource.load("sql/enroll/hit-rate-limit.sql");

    private final DatabaseClient databaseClient;

    @Override
    public Mono<LoginThrottle> hit(String key, int windowMinutes) {
        return databaseClient.sql(HIT)
                .bind("key", key)
                .bind("windowMinutes", windowMinutes)
                .map(row -> new LoginThrottle(row.get("hits", Integer.class), row.get("retry_after_seconds", Integer.class)))
                .one();
    }
}
