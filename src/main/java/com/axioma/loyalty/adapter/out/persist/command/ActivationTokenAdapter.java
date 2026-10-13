package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.ActivationTarget;
import com.axioma.loyalty.domain.port.out.ActivationTokenRepository;
import io.r2dbc.spi.Readable;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Vigencia y uso se comparan con el reloj de la base. */
@Component
@RequiredArgsConstructor
public class ActivationTokenAdapter implements ActivationTokenRepository {

    private static final String INSERT = SqlResource.load("sql/activation/insert-token.sql");
    private static final String INVALIDATE_BY_USER = SqlResource.load("sql/activation/invalidate-by-user.sql");
    private static final String FIND_VALID = SqlResource.load("sql/activation/find-valid.sql");
    private static final String CONSUME = SqlResource.load("sql/activation/consume.sql");

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> create(UUID tenantId, UUID userId, String tokenHash, Instant expiresAt) {
        return databaseClient.sql(INSERT)
                .bind("tenantId", tenantId)
                .bind("userId", userId)
                .bind("tokenHash", tokenHash)
                .bind("expiresAt", expiresAt)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> invalidateAll(UUID tenantId, UUID userId) {
        return databaseClient.sql(INVALIDATE_BY_USER)
                .bind("tenantId", tenantId)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<ActivationTarget> findValid(String slug, String tokenHash) {
        return databaseClient.sql(FIND_VALID)
                .bind("slug", slug)
                .bind("tokenHash", tokenHash)
                .map(ActivationTokenAdapter::toTarget)
                .one();
    }

    @Override
    public Mono<ActivationTarget> consume(String slug, String tokenHash) {
        return databaseClient.sql(CONSUME)
                .bind("slug", slug)
                .bind("tokenHash", tokenHash)
                .map(ActivationTokenAdapter::toTarget)
                .one();
    }

    private static ActivationTarget toTarget(Readable row) {
        return new ActivationTarget(
                row.get("tenant_id", UUID.class),
                row.get("user_id", UUID.class),
                row.get("slug", String.class),
                row.get("business_name", String.class),
                row.get("username", String.class));
    }
}
