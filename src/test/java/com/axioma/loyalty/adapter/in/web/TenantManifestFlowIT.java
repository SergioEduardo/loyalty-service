package com.axioma.loyalty.adapter.in.web;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Manifest por negocio con la aplicación completa (filtros incluidos) y Postgres real: la ruta es
 * pública y ningún filtro de autenticación la intercepta. Se omite sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantManifestFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> "postgresql://%s:%s@%s:%d/%s?sslmode=disable".formatted(
                postgres.getUsername(), postgres.getPassword(), postgres.getHost(),
                postgres.getMappedPort(5432), postgres.getDatabaseName()));
    }

    @Autowired
    WebTestClient client;

    @Autowired
    DatabaseClient db;

    @Test
    void servesManifestWithoutAuthentication() {
        String slug = newSlug();
        insertTenant(slug, "Panadería La Espiga", true, null);

        client.get().uri("/caja/" + slug + "/manifest.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/manifest+json")
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .expectBody()
                .jsonPath("$.id").isEqualTo("/caja/" + slug)
                .jsonPath("$.start_url").isEqualTo("/caja/" + slug)
                .jsonPath("$.name").isEqualTo("Panadería La Espiga")
                .jsonPath("$.short_name").isEqualTo("Panadería La")
                .jsonPath("$.icons[0].src").isEqualTo("/icons/icon-192.png")
                .jsonPath("$.icons[1].src").isEqualTo("/icons/icon-512.png");
    }

    @Test
    void ignoresAuthorizationHeader() {
        String slug = newSlug();
        insertTenant(slug, "Mi Café", true, null);

        client.get().uri("/caja/" + slug + "/manifest.webmanifest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer basura")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void usesTenantLogo() {
        String slug = newSlug();
        String logo = "https://storage.googleapis.com/logos/tenants/x/logo-abc.png";
        insertTenant(slug, "Mi Café", true, logo);

        client.get().uri("/caja/" + slug + "/manifest.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.icons[0].src").isEqualTo(logo)
                .jsonPath("$.icons[0].sizes").isEqualTo("512x512")
                .jsonPath("$.icons[1].src").isEqualTo(logo)
                .jsonPath("$.icons[1].sizes").isEqualTo("192x192");
    }

    @Test
    void returns404WithoutBodyForInactiveTenant() {
        String slug = newSlug();
        insertTenant(slug, "Cerrado", false, null);

        client.get().uri("/caja/" + slug + "/manifest.webmanifest")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty();
    }

    @Test
    void returns404WithoutBodyForUnknownOrInvalidSlug() {
        client.get().uri("/caja/no-existe-" + newSlug() + "/manifest.webmanifest")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty();
        client.get().uri("/caja/MAL_slug/manifest.webmanifest")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty();
    }

    private void insertTenant(String slug, String name, boolean active, String logoUrl) {
        var spec = db.sql("""
                        INSERT INTO loyalty.tenants (slug, business_name, points_goal, reward_text, is_active,
                                                     logo_url, logo_object_key)
                        VALUES (:slug, :name, 10, 'Café gratis', :active, :logoUrl, :logoKey)
                        """)
                .bind("slug", slug)
                .bind("name", name)
                .bind("active", active);
        spec = logoUrl == null
                ? spec.bindNull("logoUrl", String.class).bindNull("logoKey", String.class)
                : spec.bind("logoUrl", logoUrl).bind("logoKey", "tenants/x/logo-abc.png");
        spec.fetch().rowsUpdated().block();
    }

    private static String newSlug() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
