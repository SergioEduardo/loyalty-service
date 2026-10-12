package com.axioma.loyalty.adapter.out.persist.query;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.TenantIdentity;
import com.axioma.loyalty.domain.port.out.TenantIdentityReader;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TenantIdentityQueryAdapter implements TenantIdentityReader {

    private static final String FIND_ACTIVE_BY_SLUG =
            SqlResource.load("sql/tenant/find-active-identity-by-slug.sql");

    private final DatabaseClient databaseClient;

    public TenantIdentityQueryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<TenantIdentity> findActiveBySlug(String slug) {
        return databaseClient.sql(FIND_ACTIVE_BY_SLUG)
                .bind("slug", slug)
                .map((row, metadata) -> new TenantIdentity(
                        row.get("slug", String.class),
                        row.get("business_name", String.class),
                        row.get("description", String.class),
                        row.get("logo_url", String.class),
                        row.get("brand_color", String.class)))
                .one();
    }
}
