package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.LoginThrottle;
import com.axioma.loyalty.domain.port.out.LoginAttemptStore;
import io.r2dbc.spi.Readable;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** La ventana y el tiempo restante se calculan con el reloj de la base. */
@Component
@RequiredArgsConstructor
public class LoginAttemptAdapter implements LoginAttemptStore {

    private static final String FIND = SqlResource.load("sql/auth/find-login-throttle.sql");
    private static final String RECORD_FAILURE = SqlResource.load("sql/auth/record-login-failure.sql");
    private static final String CLEAR = SqlResource.load("sql/auth/clear-login-failures.sql");

    private final DatabaseClient databaseClient;

    @Override
    public Mono<LoginThrottle> find(UUID tenantId, String username, int windowMinutes) {
        return databaseClient.sql(FIND)
                .bind("tenantId", tenantId)
                .bind("username", username)
                .bind("windowMinutes", windowMinutes)
                .map(LoginAttemptAdapter::toThrottle)
                .one();
    }

    @Override
    public Mono<LoginThrottle> recordFailure(UUID tenantId, String username, int windowMinutes) {
        return databaseClient.sql(RECORD_FAILURE)
                .bind("tenantId", tenantId)
                .bind("username", username)
                .bind("windowMinutes", windowMinutes)
                .map(LoginAttemptAdapter::toThrottle)
                .one();
    }

    @Override
    public Mono<Void> clear(UUID tenantId, String username) {
        return databaseClient.sql(CLEAR)
                .bind("tenantId", tenantId)
                .bind("username", username)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private static LoginThrottle toThrottle(Readable row) {
        return new LoginThrottle(row.get("failures", Integer.class), row.get("retry_after_seconds", Integer.class));
    }
}
