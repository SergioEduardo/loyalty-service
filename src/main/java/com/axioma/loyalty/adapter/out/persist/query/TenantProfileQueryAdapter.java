package com.axioma.loyalty.adapter.out.persist.query;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.TenantProfile;
import com.axioma.loyalty.domain.port.out.TenantProfileReader;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TenantProfileQueryAdapter implements TenantProfileReader {

    private static final String FIND_ACTIVE_BY_ID = SqlResource.load("sql/tenant/find-active-profile-by-id.sql");

    private final DatabaseClient databaseClient;

    public TenantProfileQueryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<TenantProfile> findActiveById(UUID tenantId) {
        return databaseClient.sql(FIND_ACTIVE_BY_ID)
                .bind("tenantId", tenantId)
                .map((row, metadata) -> new TenantProfile(
                        row.get("slug", String.class),
                        row.get("business_name", String.class),
                        row.get("description", String.class),
                        row.get("logo_url", String.class),
                        row.get("brand_color", String.class),
                        row.get("points_goal", Integer.class),
                        row.get("reward_text", String.class),
                        row.get("cooldown_seconds", Integer.class),
                        row.get("undo_window_seconds", Integer.class)))
                .one();
    }
}
