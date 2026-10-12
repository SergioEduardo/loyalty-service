package com.axioma.loyalty.adapter.out.persist;

import com.axioma.loyalty.adapter.out.persist.query.TenantIdentityQueryAdapter;
import com.axioma.loyalty.domain.model.TenantIdentity;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

/**
 * Prueba contra un Postgres real: aplica la migración V1 y ejecuta el SQL externo.
 * Se omite sola si no hay Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class TenantIdentityQueryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static TenantIdentityQueryAdapter adapter;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("loyalty", "wallet")
                .defaultSchema("loyalty")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        var factory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(postgres.getHost())
                .port(postgres.getMappedPort(5432))
                .database(postgres.getDatabaseName())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .build());
        var client = DatabaseClient.create(factory);

        client.sql("""
                INSERT INTO loyalty.tenants (slug, business_name, description, brand_color, points_goal, reward_text)
                VALUES ('mi-cafe', 'Mi Café', 'Bienvenido', '#112233', 10, 'Café gratis')
                """).fetch().rowsUpdated().block();
        client.sql("""
                INSERT INTO loyalty.tenants (slug, business_name, points_goal, reward_text, is_active)
                VALUES ('cerrado', 'Cerrado', 5, 'Nada', FALSE)
                """).fetch().rowsUpdated().block();

        adapter = new TenantIdentityQueryAdapter(client);
    }

    @Test
    void findsActiveTenantBySlug() {
        StepVerifier.create(adapter.findActiveBySlug("mi-cafe"))
                .expectNext(new TenantIdentity("mi-cafe", "Mi Café", "Bienvenido", null, "#112233"))
                .verifyComplete();
    }

    @Test
    void ignoresInactiveTenant() {
        StepVerifier.create(adapter.findActiveBySlug("cerrado")).verifyComplete();
    }

    @Test
    void returnsEmptyWhenSlugDoesNotExist() {
        StepVerifier.create(adapter.findActiveBySlug("no-existe")).verifyComplete();
    }
}
