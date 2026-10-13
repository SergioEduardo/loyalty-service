package com.axioma.loyalty.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.out.security.SecureActivationTokenGenerator;
import com.axioma.loyalty.domain.model.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
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
 * Activación por enlace de punta a punta contra un Postgres real: alta sin contraseña, enlaces,
 * inspect/activate, errores indistinguibles, límite de intentos y que el token no llegue a los logs.
 * Cada prueba usa su propio negocio. Se omite sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.bcrypt-strength=4",
                "app.admin.audience=https://loyalty-test.run.app",
                "app.admin.allowed-emails=n8n@test.iam.gserviceaccount.com",
                "app.auth.jwt-signing-key=clave-de-prueba-para-tests-de-al-menos-32-bytes",
                "app.activation.web-base-url=https://axioma-loyalty-dev-web.web.app/"
        })
class ActivationFlowIT {

    private static final String PASSWORD = "secreto123";
    private static final String WEB = "https://axioma-loyalty-dev-web.web.app";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> "postgresql://%s:%s@%s:%d/%s?sslmode=disable".formatted(
                postgres.getUsername(), postgres.getPassword(), postgres.getHost(),
                postgres.getMappedPort(5432), postgres.getDatabaseName()));
    }

    @MockitoBean
    ReactiveJwtDecoder googleDecoder;

    @Autowired
    WebTestClient client;

    @Autowired
    DatabaseClient db;

    @BeforeEach
    void adminTokens() {
        when(googleDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("inválido")));
        when(googleDecoder.decode("n8n-token")).thenReturn(Mono.just(idToken("n8n@test.iam.gserviceaccount.com")));
        when(googleDecoder.decode("intruso-token")).thenReturn(Mono.just(idToken("intruso@example.com")));
    }

    // --- alta ---

    @Test
    void createUserWithoutPasswordReturnsLinkAndAccountIsPending() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);

        Map<String, Object> created = createUser(tenantId, "duena", UserRole.OWNER, null);
        @SuppressWarnings("unchecked")
        var activation = (Map<String, Object>) created.get("activation");
        String token = (String) activation.get("token");
        assertThat(token).matches("[A-Za-z0-9_-]{43}"); // 256 bits en base64url
        assertThat(activation.get("activationUrl")).isEqualTo(WEB + "/caja/" + slug + "/activar#t=" + token);
        Instant expiresAt = Instant.parse((String) activation.get("expiresAt"));
        assertThat(Duration.between(Instant.now(), expiresAt)).isBetween(Duration.ofHours(71), Duration.ofHours(72));

        // En la base: cuenta sin hash y solo el SHA-256 del token.
        UUID userId = UUID.fromString((String) created.get("id"));
        assertThat(db.sql("SELECT credential_hash FROM loyalty.users WHERE id = :u").bind("u", userId)
                .map(row -> java.util.Optional.ofNullable(row.get(0, String.class))).one().block()).isEmpty();
        assertThat(db.sql("SELECT token_hash FROM loyalty.activation_tokens WHERE user_id = :u").bind("u", userId)
                .map(row -> row.get(0, String.class)).all().collectList().block())
                .containsExactly(new SecureActivationTokenGenerator().hash(token));

        // Pendiente: no puede iniciar sesión, y responde igual que credenciales malas.
        String pending = login(slug, "duena", "cualquier-cosa").expectStatus().isUnauthorized()
                .expectBody(String.class).returnResult().getResponseBody();
        String wrong = login(slug, "nadie", "cualquier-cosa").expectStatus().isUnauthorized()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(pending).isEqualTo(wrong).contains("INVALID_CREDENTIALS");
    }

    @Test
    void ownerBodyWithoutPasswordKeyCreatesPendingAccount() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);

        // Exactamente el cuerpo de n8n: username, name y role, sin la clave password.
        String body = """
                {"username": "admin", "name": "Admin Las Carnitas", "role": "OWNER"}
                """;
        assertThat(body).doesNotContain("password");
        client.post().uri("/api/admin/tenants/{t}/users", tenantId)
                .header("Authorization", "Bearer n8n-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo("admin")
                .jsonPath("$.role").isEqualTo("OWNER")
                .jsonPath("$.activation.token").exists();
    }

    @Test
    void createUserWithPasswordBehavesAsBefore() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);

        Map<String, Object> created = createUser(tenantId, "caja1", UserRole.CASHIER, PASSWORD);
        assertThat(created).containsOnlyKeys("id", "tenantId", "name", "username", "role", "active");
        login(slug, "caja1", PASSWORD).expectStatus().isOk();
        Long links = db.sql("SELECT count(*) FROM loyalty.activation_tokens WHERE tenant_id = :t")
                .bind("t", tenantId).map(row -> row.get(0, Long.class)).one().block();
        assertThat(links).isZero();
    }

    // --- inspect y activate ---

    @Test
    void inspectAndActivateHappyPath() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String token = pendingOwnerToken(tenantId, "duena");

        inspect(slug, token).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tenantName").isEqualTo("Café " + slug)
                .jsonPath("$.username").isEqualTo("duena")
                .jsonPath("$.userId").doesNotExist()
                .jsonPath("$.tenantId").doesNotExist();

        var session = activate(slug, token, "miNuevaClave").expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
        assertThat(session.tokenType()).isEqualTo("Bearer");
        assertThat(session.user().role()).isEqualTo("OWNER");
        assertThat(session.tenant().slug()).isEqualTo(slug);
        me(session.accessToken()).expectStatus().isOk();
        refreshOk(session.refreshToken());

        login(slug, "duena", "miNuevaClave").expectStatus().isOk();

        // Un solo uso.
        inspect(slug, token).expectStatus().isBadRequest();
        activate(slug, token, "otraClave123").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_ACTIVATION");
        login(slug, "duena", "otraClave123").expectStatus().isUnauthorized();
    }

    @Test
    void everyInvalidLinkGetsTheSameAnswer() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String otherSlug = newSlug();
        createTenant(otherSlug);

        String expired = pendingOwnerToken(tenantId, "vencida");
        db.sql("UPDATE loyalty.activation_tokens SET expires_at = NOW() - INTERVAL '1 minute' WHERE token_hash = :h")
                .bind("h", new SecureActivationTokenGenerator().hash(expired)).fetch().rowsUpdated().block();
        String used = pendingOwnerToken(tenantId, "usada");
        activate(slug, used, "miNuevaClave").expectStatus().isOk();
        String deactivated = pendingOwnerToken(tenantId, "baja");
        post("/api/admin/tenants/{t}/users/{u}/deactivate", tenantId, userId(tenantId, "baja"))
                .exchange().expectStatus().isNoContent();
        String closedSlug = newSlug();
        UUID closedId = createTenant(closedSlug);
        String closed = pendingOwnerToken(closedId, "duena");
        db.sql("UPDATE loyalty.tenants SET is_active = FALSE WHERE id = :t").bind("t", closedId)
                .fetch().rowsUpdated().block();
        String valid = pendingOwnerToken(tenantId, "duena");

        List<Map<String, String>> cases = List.of(
                Map.of("slug", slug, "token", expired),
                Map.of("slug", slug, "token", used),
                Map.of("slug", otherSlug, "token", valid),               // de otro negocio
                Map.of("slug", slug, "token", "no-existe-" + valid),
                Map.of("slug", slug, "token", deactivated),               // cuenta inactiva
                Map.of("slug", closedSlug, "token", closed),              // negocio inactivo
                Map.of("slug", "no-existe-" + slug, "token", valid));    // slug inexistente
        List<String> bodies = new ArrayList<>();
        int ip = 0;
        // Una IP distinta por intento para no llegar al límite por slug + IP (5).
        for (var body : cases) {
            bodies.add(client.post().uri("/api/auth/activation/inspect").header("X-Forwarded-For", "10.0.0." + ++ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body).exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(String.class).returnResult().getResponseBody());
            var withPassword = new HashMap<>(body);
            withPassword.put("password", "miNuevaClave");
            bodies.add(client.post().uri("/api/auth/activate").header("X-Forwarded-For", "10.0.0." + ++ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(withPassword).exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(String.class).returnResult().getResponseBody());
        }
        assertThat(bodies).allSatisfy(body -> assertThat(body).isEqualTo(bodies.get(0)));
        assertThat(bodies.get(0)).contains("\"code\":\"INVALID_ACTIVATION\"");

        // El enlace válido sigue sirviendo en su negocio.
        inspect(slug, valid).expectStatus().isOk();
    }

    @Test
    void passwordPolicyIsTheSameAsTheAdminOne() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String token = pendingOwnerToken(tenantId, "duena");

        for (String weak : new String[] {"corta", "ñ".repeat(37)}) {
            activate(slug, token, weak).expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("WEAK_PASSWORD");
            post("/api/admin/tenants/{t}/users/{u}/reset-password", tenantId, userId(tenantId, "duena"))
                    .bodyValue(Map.of("password", weak)).exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("WEAK_PASSWORD");
        }
        // Un rechazo por política no gasta el enlace.
        activate(slug, token, "ocho8888").expectStatus().isOk();
    }

    @Test
    void activationLinkOnActiveAccountRevokesPreviousSessions() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "duena", UserRole.OWNER, PASSWORD);
        var before = login(slug, "duena", PASSWORD).expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();

        String token = issueLink(tenantId, userId(tenantId, "duena"));
        // La contraseña actual sirve hasta que se use el enlace.
        login(slug, "duena", PASSWORD).expectStatus().isOk();
        me(before.accessToken()).expectStatus().isOk();

        var after = activate(slug, token, "claveNueva99").expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
        me(before.accessToken()).expectStatus().isUnauthorized();
        refresh(before.refreshToken()).expectStatus().isUnauthorized();
        login(slug, "duena", PASSWORD).expectStatus().isUnauthorized();
        me(after.accessToken()).expectStatus().isOk();
        login(slug, "duena", "claveNueva99").expectStatus().isOk();
    }

    @Test
    void reissuingInvalidatesThePreviousLink() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String first = pendingOwnerToken(tenantId, "duena");
        String second = issueLink(tenantId, userId(tenantId, "duena"));
        assertThat(second).isNotEqualTo(first);

        inspect(slug, first).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_ACTIVATION");
        inspect(slug, second).expectStatus().isOk();
    }

    @Test
    void adminResetPasswordInvalidatesPendingLinks() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String token = pendingOwnerToken(tenantId, "duena");
        post("/api/admin/tenants/{t}/users/{u}/reset-password", tenantId, userId(tenantId, "duena"))
                .bodyValue(Map.of("password", PASSWORD)).exchange().expectStatus().isNoContent();

        inspect(slug, token).expectStatus().isBadRequest();
        login(slug, "duena", PASSWORD).expectStatus().isOk();
    }

    @Test
    void pendingAccountCannotUseTokens() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "duena", UserRole.OWNER, PASSWORD);
        var session = login(slug, "duena", PASSWORD).expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
        me(session.accessToken()).expectStatus().isOk();

        // La cuenta pasa a pendiente (sin credencial): sus tokens dejan de servir.
        db.sql("UPDATE loyalty.users SET credential_hash = NULL WHERE tenant_id = :t AND username = 'duena'")
                .bind("t", tenantId).fetch().rowsUpdated().block();
        me(session.accessToken()).expectStatus().isUnauthorized();
        client.get().uri("/api/staff/tenant").header("Authorization", "Bearer " + session.accessToken())
                .exchange().expectStatus().isUnauthorized();
        refresh(session.refreshToken()).expectStatus().isUnauthorized();
    }

    // --- límite de intentos ---

    @Test
    void failuresAreLimitedPerSlugAndIpAndPerSlug() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String token = pendingOwnerToken(tenantId, "duena");

        // Por slug + IP: el quinto fallo responde 429 y después ni el enlace válido pasa desde esa IP.
        for (int i = 1; i <= 4; i++) {
            inspectFrom("198.51.100.1", slug, "malo-" + i).expectStatus().isBadRequest();
        }
        inspectFrom("198.51.100.1", slug, "malo-5").expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody().jsonPath("$.code").isEqualTo("TOO_MANY_ATTEMPTS");
        inspectFrom("198.51.100.1", slug, token).expectStatus().isEqualTo(429);
        inspectFrom("198.51.100.2", slug, token).expectStatus().isOk();

        // Por slug: 20 fallos en total, aunque cambie la IP.
        for (int i = 6; i <= 19; i++) {
            inspectFrom("198.51.100." + (i + 10), slug, "malo-" + i).expectStatus().isBadRequest();
        }
        inspectFrom("198.51.100.99", slug, "malo-20").expectStatus().isEqualTo(429);
        inspectFrom("198.51.100.100", slug, token).expectStatus().isEqualTo(429);
        client.post().uri("/api/auth/activate").header("X-Forwarded-For", "198.51.100.101")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("slug", slug, "token", token, "password", "miNuevaClave"))
                .exchange().expectStatus().isEqualTo(429);
    }

    @Test
    void unknownSlugIsLimitedExactlyLikeAnExistingOne() {
        String unknown = "no-existe-" + newSlug();
        for (int i = 1; i <= 4; i++) {
            inspectFrom("192.0.2.1", unknown, "malo-" + i).expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_ACTIVATION");
        }
        inspectFrom("192.0.2.1", unknown, "malo-5").expectStatus().isEqualTo(429);
    }

    // --- logs y acceso admin ---

    @Test
    void tokenNeverReachesTheLogs(CapturedOutput output) {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String first = pendingOwnerToken(tenantId, "duena");
        String token = issueLink(tenantId, userId(tenantId, "duena"));
        inspect(slug, first).expectStatus().isBadRequest();
        inspect(slug, token).expectStatus().isOk();
        activate(slug, token, "corta").expectStatus().isBadRequest();
        activate(slug, token, "miNuevaClave").expectStatus().isOk();
        activate(slug, token, "miNuevaClave").expectStatus().isBadRequest();

        var hashes = new SecureActivationTokenGenerator();
        // Las líneas esperadas sí están: la captura funciona.
        assertThat(output.getAll()).contains("Activation link issued").contains("Account activated")
                .contains("Activation rejected");
        for (String secret : List.of(first, token, hashes.hash(first), hashes.hash(token), "miNuevaClave")) {
            assertThat(output.getAll()).doesNotContain(secret);
        }
    }

    @Test
    void onlyAdminCanIssueLinks() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "duena", UserRole.OWNER, PASSWORD);
        UUID userId = userId(tenantId, "duena");
        String staffToken = login(slug, "duena", PASSWORD).expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody().accessToken();
        String uri = "/api/admin/tenants/{t}/users/{u}/activation-link";

        client.post().uri(uri, tenantId, userId).exchange()
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
        client.post().uri(uri, tenantId, userId).header("Authorization", "Bearer " + staffToken).exchange()
                .expectStatus().isUnauthorized();
        client.post().uri(uri, tenantId, userId).header("Authorization", "Bearer intruso-token").exchange()
                .expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
        Long links = db.sql("SELECT count(*) FROM loyalty.activation_tokens WHERE user_id = :u")
                .bind("u", userId).map(row -> row.get(0, Long.class)).one().block();
        assertThat(links).isZero();

        // Cuenta de otro negocio o inexistente: 404.
        UUID otherTenant = createTenant(newSlug());
        post(uri, otherTenant, userId).exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("USER_NOT_FOUND");
    }

    // --- ayudas ---

    private static Jwt idToken(String email) {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of("https://loyalty-test.run.app"))
                .claim("email", email)
                .claim("email_verified", true)
                .build();
    }

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> createUser(UUID tenantId, String username, UserRole role, String password) {
        var body = new HashMap<String, Object>(Map.of("username", username, "role", role.name()));
        if (password != null) {
            body.put("password", password);
        }
        return post("/api/admin/tenants/{t}/users", tenantId).bodyValue(body)
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private String pendingOwnerToken(UUID tenantId, String username) {
        var activation = (Map<String, Object>) createUser(tenantId, username, UserRole.OWNER, null).get("activation");
        return (String) activation.get("token");
    }

    private UUID userId(UUID tenantId, String username) {
        return db.sql("SELECT id FROM loyalty.users WHERE tenant_id = :t AND username = :u")
                .bind("t", tenantId).bind("u", username).map(row -> row.get(0, UUID.class)).one().block();
    }

    private String issueLink(UUID tenantId, UUID userId) {
        return (String) post("/api/admin/tenants/{t}/users/{u}/activation-link", tenantId, userId)
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody().get("token");
    }

    private WebTestClient.ResponseSpec inspect(String slug, String token) {
        return inspectFrom("203.0.113.10", slug, token);
    }

    private WebTestClient.ResponseSpec inspectFrom(String ip, String slug, String token) {
        return client.post().uri("/api/auth/activation/inspect").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("slug", slug, "token", token)).exchange();
    }

    private WebTestClient.ResponseSpec activate(String slug, String token, String password) {
        return client.post().uri("/api/auth/activate").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("slug", slug, "token", token, "password", password)).exchange();
    }

    private WebTestClient.ResponseSpec login(String slug, String username, String password) {
        return client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("slug", slug, "username", username, "password", password)).exchange();
    }

    private WebTestClient.ResponseSpec refresh(String refreshToken) {
        return client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", refreshToken)).exchange();
    }

    private void refreshOk(String refreshToken) {
        refresh(refreshToken).expectStatus().isOk();
    }

    private WebTestClient.ResponseSpec me(String accessToken) {
        return client.get().uri("/api/auth/me").header("Authorization", "Bearer " + accessToken).exchange();
    }
}
