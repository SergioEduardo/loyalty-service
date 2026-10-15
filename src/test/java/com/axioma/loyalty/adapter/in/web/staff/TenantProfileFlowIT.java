package com.axioma.loyalty.adapter.in.web.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.in.web.auth.TokenResponse;
import com.axioma.loyalty.domain.model.TenantLogo;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.support.InMemoryLogoStorage;
import com.axioma.loyalty.support.TestImages;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

/**
 * Perfil del negocio de punta a punta: filtro de staff, roles, multipart real, Postgres real y
 * un fake de LogoStoragePort en lugar de Cloud Storage. Se omite sin Docker.
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
class TenantProfileFlowIT {

    private static final String PASSWORD = "secreto123";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> "postgresql://%s:%s@%s:%d/%s?sslmode=disable".formatted(
                postgres.getUsername(), postgres.getPassword(), postgres.getHost(),
                postgres.getMappedPort(5432), postgres.getDatabaseName()));
    }

    @TestConfiguration
    static class FakeStorage {
        @Bean
        @Primary
        InMemoryLogoStorage inMemoryLogoStorage() {
            return new InMemoryLogoStorage();
        }
    }

    @MockitoBean
    ReactiveJwtDecoder googleDecoder;

    @Autowired
    WebTestClient client;

    @Autowired
    DatabaseClient db;

    @Autowired
    InMemoryLogoStorage storage;

    @BeforeEach
    void setUp() {
        storage.clear();
        when(googleDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("inválido")));
        when(googleDecoder.decode("n8n-token")).thenReturn(Mono.just(Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of("https://loyalty-test.run.app"))
                .claim("email", "n8n@test.iam.gserviceaccount.com")
                .claim("email_verified", true)
                .build()));
    }

    // --- autenticación y roles ---

    @Test
    void withoutTokenEveryEndpointIs401() {
        for (var request : endpoints(null)) {
            request.exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
        }
    }

    @Test
    void cashierGets403OnEveryEndpointAndNothingChanges() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "caja1", UserRole.CASHIER);
        String token = loginOk(slug, "caja1");

        for (var request : endpoints(token)) {
            request.exchange()
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
        }
        assertThat(storage.objects()).isEmpty();
        assertThat(outboxCount(tenantId)).isEqualTo(1); // solo el alta
    }

    // --- identidad y configuración ---

    @Test
    void ownerReadsAndEditsIdentityAndPublicPageReflectsIt() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "duena", UserRole.OWNER);
        String token = loginOk(slug, "duena");

        client.get().uri("/api/staff/tenant").header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.slug").isEqualTo(slug)
                .jsonPath("$.name").isEqualTo("Café " + slug)
                .jsonPath("$.logoUrl").doesNotExist()
                .jsonPath("$.settings.pointsGoal").isEqualTo(10);

        putJson("/api/staff/tenant/identity", token,
                Map.of("name", "  Café Nuevo  ", "description", "Bienvenidos", "brandColor", "#A1B2C3"))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Café Nuevo")
                .jsonPath("$.description").isEqualTo("Bienvenidos")
                .jsonPath("$.brandColor").isEqualTo("#a1b2c3");

        client.get().uri("/api/public/tenants/{slug}/identity", slug).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Café Nuevo")
                .jsonPath("$.brandColor").isEqualTo("#a1b2c3");
        assertThat(outboxCount(tenantId)).isEqualTo(2);
        // El aviso del cambio lleva el slug: el wallet-service forma con él el id de la clase de Google.
        assertThat(latestTenantChanged(tenantId))
                .containsEntry("slug", slug)
                .containsEntry("name", "Café Nuevo")
                .containsEntry("brand_color", "#a1b2c3");
    }

    @Test
    void identityValidationErrors() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "duena", UserRole.OWNER);
        String token = loginOk(slug, "duena");

        putJson("/api/staff/tenant/identity", token, Map.of("name", " A ", "brandColor", "#112233"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_TEXT")
                .jsonPath("$.message").isEqualTo("Algún texto está vacío o es demasiado largo.");
        putJson("/api/staff/tenant/identity", token,
                Map.of("name", "Café", "description", "d".repeat(141), "brandColor", "#112233"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_TEXT");
        putJson("/api/staff/tenant/identity", token, Map.of("name", "Café", "brandColor", "#12345"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_BRAND_COLOR");
        assertThat(outboxCount(tenantId)).isEqualTo(1);
    }

    @Test
    void ownerEditsSettings() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "duena", UserRole.OWNER);
        String token = loginOk(slug, "duena");

        putJson("/api/staff/tenant/settings", token,
                Map.of("pointsGoal", 8, "rewardText", "Postre", "cooldownSeconds", 60, "undoWindowSeconds", 120))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.settings.pointsGoal").isEqualTo(8)
                .jsonPath("$.settings.rewardText").isEqualTo("Postre")
                .jsonPath("$.settings.cooldownSeconds").isEqualTo(60)
                .jsonPath("$.settings.undoWindowSeconds").isEqualTo(120);
        putJson("/api/staff/tenant/settings", token, Map.of("pointsGoal", 0, "rewardText", "Postre",
                "cooldownSeconds", 0, "undoWindowSeconds", 0))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_SETTINGS");
    }

    @Test
    void tenantCannotBeChangedByParameters() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "duena", UserRole.OWNER);
        String token = loginOk(slug, "duena");
        String otherSlug = newSlug();
        UUID otherId = createTenant(otherSlug);

        String query = "?tenantId=" + otherId + "&slug=" + otherSlug;
        client.get().uri("/api/staff/tenant" + query).header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.slug").isEqualTo(slug);
        putJson("/api/staff/tenant/identity" + query, token, Map.of("name", "Secuestrado", "brandColor", "#000000",
                "tenantId", otherId.toString(), "slug", otherSlug))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.slug").isEqualTo(slug);
        uploadLogo(token, TestImages.png(300, 200), "logo.png").expectStatus().isOk();
        client.method(HttpMethod.DELETE).uri("/api/staff/tenant/logo" + query)
                .header("Authorization", "Bearer " + token).exchange().expectStatus().isNoContent();

        // El otro negocio quedó intacto; el cambio cayó en el del token.
        client.get().uri("/api/public/tenants/{slug}/identity", otherSlug).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Café " + otherSlug);
        assertThat(outboxCount(otherId)).isEqualTo(1);
        client.get().uri("/api/public/tenants/{slug}/identity", slug).exchange()
                .expectBody().jsonPath("$.name").isEqualTo("Secuestrado");
    }

    // --- logo ---

    @Test
    void uploadReplaceAndRemoveLogo() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "duena", UserRole.OWNER);
        String token = loginOk(slug, "duena");

        String firstUrl = uploadLogo(token, TestImages.png(400, 300), "logo.png").expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody().get("logoUrl").toString();
        String firstKey = logoObjectKey(tenantId);
        assertThat(firstKey).matches("tenants/" + tenantId + "/logo-[0-9a-f]{64}\\.png");
        assertThat(firstUrl).isEqualTo(InMemoryLogoStorage.BASE_URL + "/" + firstKey);
        client.get().uri("/api/public/tenants/{slug}/identity", slug).exchange()
                .expectBody().jsonPath("$.logoUrl").isEqualTo(firstUrl);

        // JPEG y WebP también; cada reemplazo borra el objeto anterior.
        uploadLogo(token, TestImages.jpeg(640, 480), "foto.jpg").expectStatus().isOk();
        String secondKey = logoObjectKey(tenantId);
        uploadLogo(token, TestImages.webp(false), "logo.webp").expectStatus().isOk();
        String thirdKey = logoObjectKey(tenantId);
        assertThat(storage.deleted()).containsExactly(firstKey, secondKey);
        assertThat(storage.objects()).containsOnlyKeys(thirdKey);
        assertThat(TestImages.pngSize(storage.objects().get(thirdKey))).containsExactly(512, 512);

        delete("/api/staff/tenant/logo", token).expectStatus().isNoContent();
        delete("/api/staff/tenant/logo", token).expectStatus().isNoContent();
        assertThat(storage.objects()).isEmpty();
        assertThat(logoObjectKey(tenantId)).isNull();
        client.get().uri("/api/public/tenants/{slug}/identity", slug).exchange()
                .expectBody().jsonPath("$.logoUrl").doesNotExist();
        assertThat(outboxCount(tenantId)).isEqualTo(5); // alta, 3 subidas y un borrado
    }

    @Test
    void rejectedLogos() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        createUser(tenantId, "duena", UserRole.OWNER);
        String token = loginOk(slug, "duena");

        uploadLogo(token, TestImages.svg(), "logo.svg").expectStatus().isEqualTo(415)
                .expectBody().jsonPath("$.code").isEqualTo("UNSUPPORTED_IMAGE_FORMAT");
        // Extensión y Content-Type de PNG, pero el contenido no es una imagen.
        uploadLogo(token, "<html>hola</html>".getBytes(StandardCharsets.UTF_8), "logo.png")
                .expectStatus().isEqualTo(415)
                .expectBody().jsonPath("$.code").isEqualTo("UNSUPPORTED_IMAGE_FORMAT");
        uploadLogo(token, Arrays.copyOf(TestImages.png(10, 10), TenantLogo.MAX_BYTES + 1), "logo.png")
                .expectStatus().isEqualTo(413)
                .expectBody().jsonPath("$.code").isEqualTo("LOGO_TOO_LARGE");
        // Más que el límite de la parte multipart (3 MB): se corta antes de leerla completa.
        uploadLogo(token, new byte[5 * 1024 * 1024], "logo.png")
                .expectStatus().isEqualTo(413)
                .expectBody().jsonPath("$.code").isEqualTo("LOGO_TOO_LARGE");
        uploadLogo(token, TestImages.pngBomb(20_000, 20_000), "logo.png")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("IMAGE_DIMENSIONS_TOO_LARGE");

        assertThat(storage.objects()).isEmpty();
        assertThat(logoObjectKey(tenantId)).isNull();
        assertThat(outboxCount(tenantId)).isEqualTo(1);
    }

    // --- ayudas ---

    private List<WebTestClient.RequestHeadersSpec<?>> endpoints(String token) {
        List<WebTestClient.RequestHeadersSpec<?>> requests = List.of(
                client.get().uri("/api/staff/tenant"),
                client.put().uri("/api/staff/tenant/identity").contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("name", "Café", "brandColor", "#112233")),
                client.put().uri("/api/staff/tenant/settings").contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("pointsGoal", 5, "rewardText", "x", "cooldownSeconds", 0,
                                "undoWindowSeconds", 0)),
                client.put().uri("/api/staff/tenant/logo").contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(logoBody(TestImages.png(10, 10), "logo.png"))),
                client.delete().uri("/api/staff/tenant/logo"));
        if (token != null) {
            requests.forEach(request -> request.header("Authorization", "Bearer " + token));
        }
        return requests;
    }

    private static String newSlug() {
        return "cafe-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID createTenant(String slug) {
        return adminPost("/api/admin/tenants")
                .bodyValue(Map.of("slug", slug, "name", "Café " + slug, "pointsGoal", 10, "rewardText", "Café gratis"))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody()
                .get("id").toString().transform(UUID::fromString);
    }

    private void createUser(UUID tenantId, String username, UserRole role) {
        adminPost("/api/admin/tenants/{t}/users", tenantId)
                .bodyValue(Map.of("username", username, "password", PASSWORD, "role", role.name()))
                .exchange().expectStatus().isCreated();
    }

    private WebTestClient.RequestBodySpec adminPost(String uri, Object... vars) {
        return client.post().uri(uri, vars)
                .header("Authorization", "Bearer n8n-token")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String loginOk(String slug, String username) {
        return client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("slug", slug, "username", username, "password", PASSWORD))
                .exchange().expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody().accessToken();
    }

    private WebTestClient.ResponseSpec putJson(String uri, String token, Object body) {
        return client.put().uri(uri).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private WebTestClient.ResponseSpec delete(String uri, String token) {
        return client.delete().uri(uri).header("Authorization", "Bearer " + token).exchange();
    }

    private WebTestClient.ResponseSpec uploadLogo(String token, byte[] content, String filename) {
        return client.put().uri("/api/staff/tenant/logo").header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(logoBody(content, filename)))
                .exchange();
    }

    private static org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> logoBody(
            byte[] content, String filename) {
        var body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.IMAGE_PNG);
        return body.build();
    }

    private String logoObjectKey(UUID tenantId) {
        return db.sql("SELECT logo_object_key FROM loyalty.tenants WHERE id = :t").bind("t", tenantId)
                .map(row -> java.util.Optional.ofNullable(row.get(0, String.class))).one().block().orElse(null);
    }

    private long outboxCount(UUID tenantId) {
        return db.sql("SELECT count(*) FROM loyalty.outbox WHERE tenant_id = :t AND event_type = 'TenantChanged'")
                .bind("t", tenantId).map(row -> row.get(0, Long.class)).one().block();
    }

    private Map<String, Object> latestTenantChanged(UUID tenantId) {
        return db.sql("""
                        SELECT payload->>'slug' AS slug, payload->>'name' AS name, payload->>'brandColor' AS brand_color
                          FROM loyalty.outbox WHERE tenant_id = :t AND event_type = 'TenantChanged'
                         ORDER BY id DESC LIMIT 1""")
                .bind("t", tenantId).fetch().one().block();
    }
}
