package com.axioma.loyalty.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

/**
 * Flujo completo de la API admin contra un Postgres real (Flyway aplica V1 al arrancar).
 * El decoder de ID tokens está simulado; el resto es la aplicación real. Se omite sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.bcrypt-strength=4",
                "app.admin.audience=" + AdminFlowIT.AUDIENCE,
                "app.admin.allowed-emails=" + AdminFlowIT.N8N
        })
class AdminFlowIT {

    static final String AUDIENCE = "https://loyalty-test.run.app";
    static final String N8N = "n8n@test.iam.gserviceaccount.com";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> "postgresql://%s:%s@%s:%d/%s?sslmode=disable".formatted(
                postgres.getUsername(), postgres.getPassword(), postgres.getHost(),
                postgres.getMappedPort(5432), postgres.getDatabaseName()));
    }

    @MockitoBean
    ReactiveJwtDecoder decoder;

    @Autowired
    WebTestClient client;

    @Autowired
    DatabaseClient db;

    @BeforeEach
    void tokens() {
        when(decoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("inválido")));
        when(decoder.decode("n8n-token")).thenReturn(Mono.just(idToken(N8N)));
        when(decoder.decode("intruso-token")).thenReturn(Mono.just(idToken("intruso@gmail.com")));
    }

    private static Jwt idToken(String email) {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of(AUDIENCE))
                .claim("email", email)
                .claim("email_verified", true)
                .build();
    }

    private WebTestClient.RequestBodySpec post(String uri, Object... vars) {
        return client.post().uri(uri, vars)
                .header("Authorization", "Bearer n8n-token")
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void fullAdminFlow() {
        // Alta del negocio y su aviso en el outbox
        var tenant = post("/api/admin/tenants")
                .bodyValue(Map.of("slug", "cafe-flujo", "name", "Café Flujo", "description", "Bienvenido",
                        "brandColor", "#112233", "pointsGoal", 8, "rewardText", "Café gratis"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TenantResponse.class).returnResult().getResponseBody();
        assertThat(tenant).isNotNull();
        assertThat(tenant.cooldownSeconds()).isZero();
        assertThat(tenant.undoWindowSeconds()).isEqualTo(300);
        UUID tenantId = tenant.id();

        Map<String, Object> event = db.sql("""
                        SELECT event_type, status, payload->>'slug' AS slug, (payload->>'pointsGoal')::int AS goal
                          FROM loyalty.outbox WHERE tenant_id = :t""")
                .bind("t", tenantId).fetch().one().block();
        assertThat(event).containsEntry("event_type", "TenantChanged")
                .containsEntry("status", "PENDING")
                .containsEntry("slug", "cafe-flujo")
                .containsEntry("goal", 8);

        post("/api/admin/tenants")
                .bodyValue(Map.of("slug", "cafe-flujo", "name", "Otro", "pointsGoal", 5, "rewardText", "x"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("SLUG_ALREADY_EXISTS");

        // Cuenta del personal
        var user = post("/api/admin/tenants/{t}/users", tenantId)
                .bodyValue(Map.of("username", "  Ana ", "password", "secreto123", "role", "CASHIER"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class).returnResult().getResponseBody();
        assertThat(user).isNotNull();
        assertThat(user.username()).isEqualTo("ana");
        UUID userId = user.id();

        post("/api/admin/tenants/{t}/users", tenantId)
                .bodyValue(Map.of("username", "ANA", "password", "otraClave1", "role", "OWNER"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("USERNAME_TAKEN");

        // Una sesión abierta que debe quedar revocada
        db.sql("""
                        INSERT INTO loyalty.sessions (tenant_id, user_id, refresh_token_hash, expires_at)
                        VALUES (:t, :u, repeat('a', 64), NOW() + INTERVAL '1 day')""")
                .bind("t", tenantId).bind("u", userId).fetch().rowsUpdated().block();

        post("/api/admin/tenants/{t}/users/{u}/reset-password", tenantId, userId)
                .bodyValue(Map.of("password", "nuevaClave1"))
                .exchange()
                .expectStatus().isNoContent();
        String hash = db.sql("SELECT credential_hash FROM loyalty.users WHERE id = :u")
                .bind("u", userId).map(row -> row.get(0, String.class)).one().block();
        assertThat(new BCryptPasswordEncoder().matches("nuevaClave1", hash)).isTrue();
        assertThat(openSessions(userId)).isZero();

        db.sql("""
                        INSERT INTO loyalty.sessions (tenant_id, user_id, refresh_token_hash, expires_at)
                        VALUES (:t, :u, repeat('b', 64), NOW() + INTERVAL '1 day')""")
                .bind("t", tenantId).bind("u", userId).fetch().rowsUpdated().block();

        client.post().uri("/api/admin/tenants/{t}/users/{u}/deactivate", tenantId, userId)
                .header("Authorization", "Bearer n8n-token")
                .exchange()
                .expectStatus().isNoContent();
        Boolean active = db.sql("SELECT is_active FROM loyalty.users WHERE id = :u")
                .bind("u", userId).map(row -> row.get(0, Boolean.class)).one().block();
        assertThat(active).isFalse();
        assertThat(openSessions(userId)).isZero();

        client.post().uri("/api/admin/tenants/{t}/users/{u}/deactivate", tenantId, UUID.randomUUID())
                .header("Authorization", "Bearer n8n-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rejectsMissingOrInvalidTokenWith401() {
        client.post().uri("/api/admin/tenants").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");

        client.post().uri("/api/admin/tenants").header("Authorization", "Bearer basura")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsUnlistedEmailWith403() {
        client.post().uri("/api/admin/tenants").header("Authorization", "Bearer intruso-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void publicEndpointStaysOpen() {
        client.get().uri("/api/public/tenants/no-existe/identity")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_NOT_FOUND");
    }

    private long openSessions(UUID userId) {
        return db.sql("SELECT count(*) FROM loyalty.sessions WHERE user_id = :u AND revoked_at IS NULL")
                .bind("u", userId).map(row -> row.get(0, Long.class)).one().block();
    }
}
