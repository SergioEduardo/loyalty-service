package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.port.out.SessionRevoker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SessionRevocationAdapter implements SessionRevoker {

    private static final String REVOKE_ALL_BY_USER = SqlResource.load("sql/session/revoke-all-by-user.sql");

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Long> revokeAllByUser(UUID tenantId, UUID userId) {
        return databaseClient.sql(REVOKE_ALL_BY_USER)
                .bind("tenantId", tenantId)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated();
    }
}
