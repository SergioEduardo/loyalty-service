package com.axioma.loyalty.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.out.security.SecureRefreshTokenGenerator;
import com.axioma.loyalty.domain.model.UserRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

/**
 * Sesiones del personal contra un Postgres real. Los negocios y cuentas se crean con la API
 * admin (su decoder de Google está simulado). Cada prueba usa su propio negocio. Se omite sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.bcrypt-strength=4",
                "app.admin.audience=https://loyalty-test.run.app",
                "app.admin.allowed-emails=n8n@test.iam.gserviceaccount.com",
                "app.auth.jwt-signing-key=clave-de-prueba-para-tests-de-al-menos-32-bytes"
        })
class AuthFlowIT {

    private static final String PASSWORD = "secreto123";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> "postgresql://%s:%s@%s:%d/%s?sslmode=disable".formatted(
                postgres.getUsername(), postgres.getPassword(), postgres.getHost(),
                postgres.getMappedPort(5432), postgres.getDatabaseName()));
    }

    /** Endpoint de staff solo para dueños, para probar @RequireRole de punta a punta. */
    @TestConfiguration
    static class OwnerOnlyEndpoint {
        @Bean
        OwnerOnlyController ownerOnlyController() {
            return new OwnerOnlyController();
        }
    }

    @RestController
    static class OwnerOnlyController {
        @GetMapping("/api/staff/test/owner-only")
        @RequireRole(UserRole.OWNER)
        Mono<String> ownerOnly() {
            return Mono.just("ok");
        }
    }

    @MockitoBean
    ReactiveJwtDecoder googleDecoder;

    @Autowired
    WebTestClient client;

    @Autowired
    DatabaseClient db;

    @BeforeEach
    void adminToken() {
        when(googleDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("inválido")));
        when(googleDecoder.decode("n8n-token")).thenReturn(Mono.just(Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of("https://loyalty-test.run.app"))
                .claim("email", "n8n@test.iam.gserviceaccount.com")
                .claim("email_verified", true)
                .build()));
    }

    // --- pruebas ---

    @Test
    void loginMeRefreshRotationAndReuseDetection() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        UUID userId = createUser(tenantId, "caja1", UserRole.CASHIER);

        var first = login(slug, " CAJA1 ", PASSWORD).expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
        assertThat(first.tokenType()).isEqualTo("Bearer");
        assertThat(first.expiresIn()).isEqualTo(900);
        assertThat(first.user().id()).isEqualTo(userId);
        assertThat(first.user().role()).isEqualTo("CASHIER");
        assertThat(first.tenant().slug()).isEqualTo(slug);

        // En la base solo está el hash del refresh token.
        String storedHash = db.sql("SELECT refresh_token_hash FROM loyalty.sessions WHERE user_id = :u")
                .bind("u", userId).map(row -> row.get(0, String.class)).one().block();
        assertThat(storedHash).isEqualTo(new SecureRefreshTokenGenerator().hash(first.refreshToken()))
                .isNotEqualTo(first.refreshToken());

        var me = me(first.accessToken()).expectStatus().isOk()
                .expectBody(MeResponse.class).returnResult().getResponseBody();
        assertThat(me.userId()).isEqualTo(userId);
        assertThat(me.tenantId()).isEqualTo(tenantId);
        assertThat(me.role()).isEqualTo("CASHIER");

        // Rotación: el nuevo funciona, el viejo ya no.
        var second = refresh(first.refreshToken()).expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.user().id()).isEqualTo(userId);
        assertThat(second.tenant().slug()).isEqualTo(slug);
        me(second.accessToken()).expectStatus().isOk();

        // Reutilizar el viejo revoca toda la sesión: el refresh nuevo y el access token dejan de servir.
        refresh(first.refreshToken()).expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_SESSION");
        refresh(second.refreshToken()).expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_SESSION");
        me(second.accessToken()).expectStatus().isUnauthorized();
    }

    @Test
    void logoutRevokesSessionAndIsIdempotent() {
        String slug = newSlug();
        createUser(createTenant(slug), "caja1", UserRole.CASHIER);
        var tokens = loginOk(slug, "caja1");

        logout(tokens.refreshToken()).expectStatus().isNoContent();
        logout(tokens.refreshToken()).expectStatus().isNoContent();
        logout("token-que-no-existe").expectStatus().isNoContent();

        refresh(tokens.refreshToken()).expectStatus().isUnauthorized();
        me(tokens.accessToken()).expectStatus().isUnauthorized();
    }

    @Test
    void badCredentialsAllLookTheSame() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "caja1", UserRole.CASHIER);
        UUID inactiveId = createUser(tenantId, "baja", UserRole.CASHIER);
        post("/api/admin/tenants/{t}/users/{u}/deactivate", tenantId, inactiveId).exchange().expectStatus().isNoContent();
        String closedSlug = newSlug();
        UUID closedId = createTenant(closedSlug);
        createUser(closedId, "caja1", UserRole.CASHIER);
        db.sql("UPDATE loyalty.tenants SET is_active = FALSE WHERE id = :t").bind("t", closedId)
                .fetch().rowsUpdated().block();

        List<Map<String, String>> attempts = List.of(
                Map.of("slug", "no-existe-" + slug, "username", "caja1", "password", PASSWORD),
                Map.of("slug", closedSlug, "username", "caja1", "password", PASSWORD),
                Map.of("slug", slug, "username", "nadie", "password", PASSWORD),
                Map.of("slug", slug, "username", "baja", "password", PASSWORD),
                Map.of("slug", slug, "username", "caja1", "password", "incorrecta"));
        for (var body : attempts) {
            client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS")
                    .jsonPath("$.message").isEqualTo("Negocio, usuario o contraseña incorrectos.");
        }
    }

    @Test
    void locksOnFifthFailureAndSuccessClearsFailures() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "caja1", UserRole.CASHIER);
        createUser(tenantId, "caja2", UserRole.CASHIER);

        for (int i = 1; i <= 4; i++) {
            login(slug, "caja1", "incorrecta").expectStatus().isUnauthorized();
        }
        login(slug, "caja1", "incorrecta").expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody().jsonPath("$.code").isEqualTo("TOO_MANY_ATTEMPTS");
        // Bloqueado aun con la contraseña correcta.
        login(slug, "caja1", PASSWORD).expectStatus().isEqualTo(429);
        // El bloqueo es por usuario: otra cuenta del negocio sigue entrando.
        loginOk(slug, "caja2");

        // Un inicio correcto limpia los fallos.
        login(slug, "caja2", "incorrecta").expectStatus().isUnauthorized();
        loginOk(slug, "caja2");
        Long rows = db.sql("SELECT count(*) FROM loyalty.login_attempts WHERE tenant_id = :t AND username = 'caja2'")
                .bind("t", tenantId).map(row -> row.get(0, Long.class)).one().block();
        assertThat(rows).isZero();
    }

    @Test
    void meRequiresValidToken() {
        client.get().uri("/api/auth/me").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
        me("no-es-un-jwt").expectStatus().isUnauthorized();
    }

    @Test
    void resetPasswordAndDeactivateInvalidateTokens() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        UUID userId = createUser(tenantId, "caja1", UserRole.CASHIER);

        var beforeReset = loginOk(slug, "caja1");
        post("/api/admin/tenants/{t}/users/{u}/reset-password", tenantId, userId)
                .bodyValue(Map.of("password", "nuevaClave1")).exchange().expectStatus().isNoContent();
        me(beforeReset.accessToken()).expectStatus().isUnauthorized();
        refresh(beforeReset.refreshToken()).expectStatus().isUnauthorized();
        login(slug, "caja1", PASSWORD).expectStatus().isUnauthorized();

        var afterReset = login(slug, "caja1", "nuevaClave1").expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
        post("/api/admin/tenants/{t}/users/{u}/deactivate", tenantId, userId).exchange().expectStatus().isNoContent();
        me(afterReset.accessToken()).expectStatus().isUnauthorized();
        refresh(afterReset.refreshToken()).expectStatus().isUnauthorized();
        login(slug, "caja1", "nuevaClave1").expectStatus().isUnauthorized();
    }

    @Test
    void requireRoleAllowsOwnerAndRejectsCashier() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "caja1", UserRole.CASHIER);
        createUser(tenantId, "duena", UserRole.OWNER);

        client.get().uri("/api/staff/test/owner-only")
                .header("Authorization", "Bearer " + loginOk(slug, "caja1").accessToken())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
        client.get().uri("/api/staff/test/owner-only")
                .header("Authorization", "Bearer " + loginOk(slug, "duena").accessToken())
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/staff/test/owner-only").exchange().expectStatus().isUnauthorized();
    }

    // --- ayudas ---

    private static String newSlug() {
        return "cafe-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private WebTestClient.RequestBodySpec post(String uri, Object... vars) {
        return client.post().uri(uri, vars)
                .header("Authorization", "Bearer n8n-token")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private UUID createTenant(String slug) {
        return post("/api/admin/tenants")
                .bodyValue(Map.of("slug", slug, "name", "Café " + slug, "pointsGoal", 10, "rewardText", "Café gratis"))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody()
                .get("id").toString().transform(UUID::fromString);
    }

    private UUID createUser(UUID tenantId, String username, UserRole role) {
        return post("/api/admin/tenants/{t}/users", tenantId)
                .bodyValue(Map.of("username", username, "password", PASSWORD, "role", role.name()))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody()
                .get("id").toString().transform(UUID::fromString);
    }

    private WebTestClient.ResponseSpec login(String slug, String username, String password) {
        return client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("slug", slug, "username", username, "password", password))
                .exchange();
    }

    private TokenResponse loginOk(String slug, String username) {
        return login(slug, username, PASSWORD).expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
    }

    private WebTestClient.ResponseSpec refresh(String refreshToken) {
        return client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", refreshToken)).exchange();
    }

    private WebTestClient.ResponseSpec logout(String refreshToken) {
        return client.post().uri("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", refreshToken)).exchange();
    }

    private WebTestClient.ResponseSpec me(String accessToken) {
        return client.get().uri("/api/auth/me").header("Authorization", "Bearer " + accessToken).exchange();
    }
}
