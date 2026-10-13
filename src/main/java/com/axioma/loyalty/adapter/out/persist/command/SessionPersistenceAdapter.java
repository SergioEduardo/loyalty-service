package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.NewSession;
import com.axioma.loyalty.domain.model.SessionOwner;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.port.out.SessionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SessionPersistenceAdapter implements SessionRepository {

    private static final String ROTATE = SqlResource.load("sql/session/rotate-refresh-token.sql");
    private static final String REVOKE_BY_TOKEN = SqlResource.load("sql/session/revoke-by-refresh-token.sql");
    private static final String FIND_ACTIVE_PRINCIPAL = SqlResource.load("sql/session/find-active-principal.sql");

    private final SessionR2dbcRepository repository;
    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> create(NewSession session) {
        return repository.save(new SessionEntity(session.id(), session.tenantId(), session.userId(),
                session.refreshTokenHash(), session.expiresAt(), true)).then();
    }

    @Override
    public Mono<SessionOwner> rotate(String currentHash, String newHash, Instant expiresAt) {
        return databaseClient.sql(ROTATE)
                .bind("oldHash", currentHash)
                .bind("newHash", newHash)
                .bind("expiresAt", expiresAt)
                .map((row, metadata) -> new SessionOwner(
                        row.get("session_id", UUID.class),
                        row.get("tenant_id", UUID.class),
                        row.get("user_id", UUID.class),
                        row.get("user_name", String.class),
                        UserRole.valueOf(row.get("role", String.class)),
                        row.get("slug", String.class),
                        row.get("business_name", String.class)))
                .one();
    }

    @Override
    public Mono<Long> revokeByRefreshTokenHash(String refreshTokenHash) {
        return databaseClient.sql(REVOKE_BY_TOKEN)
                .bind("hash", refreshTokenHash)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public Mono<UserRole> findActiveRole(UUID sessionId, UUID tenantId, UUID userId) {
        return databaseClient.sql(FIND_ACTIVE_PRINCIPAL)
                .bind("sessionId", sessionId)
                .bind("tenantId", tenantId)
                .bind("userId", userId)
                .map((row, metadata) -> UserRole.valueOf(row.get("role", String.class)))
                .one();
    }
}
