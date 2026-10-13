package com.axioma.loyalty.adapter.in.web.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.application.command.PublishPendingEventsCommand;
import com.axioma.loyalty.application.handler.PublishPendingEventsHandler;
import com.axioma.loyalty.domain.model.PublishReport;
import com.axioma.loyalty.support.FakeEventPublisher;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
 * Outbox de punta a punta con Postgres real y un fake de EventPublisherPort: reclamo con SKIP LOCKED,
 * espera creciente, paso a FAILED, autenticación de /internal/outbox/publish y el camino rápido.
 * Se omite sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.bcrypt-strength=4",
                "app.admin.audience=https://loyalty-test.run.app",
                "app.admin.allowed-emails=n8n@test.iam.gserviceaccount.com",
                "app.auth.jwt-signing-key=clave-de-prueba-para-tests-de-al-menos-32-bytes",
                "app.outbox.max-attempts=3",
                "app.outbox.fast-path-timeout-ms=300",
                "app.outbox.publish-timeout-ms=2000",
                "app.outbox.publish-audience=https://loyalty-test.run.app",
                "app.outbox.publish-allowed-emails=scheduler@test.iam.gserviceaccount.com"
        })
class OutboxFlowIT {

    private static final String AUDIENCE = "https://loyalty-test.run.app";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> "postgresql://%s:%s@%s:%d/%s?sslmode=disable".formatted(
                postgres.getUsername(), postgres.getPassword(), postgres.getHost(),
                postgres.getMappedPort(5432), postgres.getDatabaseName()));
    }

    @TestConfiguration
    static class FakePubSub {
        @Bean
        @Primary
        FakeEventPublisher fakeEventPublisher() {
            return new FakeEventPublisher(true);
        }
    }

    @MockitoBean
    ReactiveJwtDecoder googleDecoder;

    @Autowired
    WebTestClient client;

    @Autowired
    DatabaseClient db;

    @Autowired
    FakeEventPublisher publisher;

    @Autowired
    PublishPendingEventsHandler handler;

    @BeforeEach
    void setUp() {
        publisher.reset();
        db.sql("DELETE FROM loyalty.outbox").fetch().rowsUpdated().block();
        when(googleDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("inválido")));
        when(googleDecoder.decode("n8n-token")).thenReturn(Mono.just(idToken("n8n@test.iam.gserviceaccount.com")));
        when(googleDecoder.decode("scheduler-token"))
                .thenReturn(Mono.just(idToken("scheduler@test.iam.gserviceaccount.com")));
        when(googleDecoder.decode("other-token")).thenReturn(Mono.just(idToken("otro@test.iam.gserviceaccount.com")));
    }

    // --- red de seguridad: autenticación ---

    @Test
    void publishEndpointRequiresTheSchedulerIdentity() {
        client.post().uri("/internal/outbox/publish").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
        post("garbage").expectStatus().isUnauthorized();
        post("other-token").expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
        // El token de n8n es válido para la API admin, no para /internal.
        post("n8n-token").expectStatus().isForbidden();
        // Y el de Scheduler no abre la API admin.
        client.post().uri("/api/admin/tenants").header("Authorization", "Bearer scheduler-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of())
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void publishEndpointPublishesPendingEvents() {
        UUID tenant = insertTenant();
        long first = insertEvent(tenant);
        long second = insertEvent(tenant);

        post("scheduler-token").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.claimed").isEqualTo(2)
                .jsonPath("$.published").isEqualTo(2)
                .jsonPath("$.failed").isEqualTo(0)
                .jsonPath("$.disabled").doesNotExist();

        assertThat(publisher.confirmedIds()).containsExactlyInAnyOrder(first, second);
        assertThat(status(first)).isEqualTo("PUBLISHED");
        assertThat(publishedAtSet(first)).isTrue();
        var event = publisher.confirmed().get(0);
        assertThat(event.tenantId()).isEqualTo(tenant);
        assertThat(event.eventType()).isEqualTo("TenantChanged");
        assertThat(event.payload()).contains("\"slug\"");

        post("scheduler-token").expectStatus().isOk().expectBody().jsonPath("$.claimed").isEqualTo(0);
    }

    // --- reclamo, espera creciente y FAILED ---

    @Test
    void skipLockedLeavesRowsLockedByAnotherTransactionAlone() throws Exception {
        UUID tenant = insertTenant();
        List<Long> ids = IntStream.range(0, 5).mapToObj(i -> insertEvent(tenant)).toList();

        try (Connection other = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            other.setAutoCommit(false);
            other.createStatement().executeQuery(
                    "SELECT id FROM loyalty.outbox WHERE id IN (%d, %d, %d) FOR UPDATE"
                            .formatted(ids.get(0), ids.get(1), ids.get(2)));

            // No espera al otro bloqueo: toma solo los libres.
            PublishReport report = handler.handle(PublishPendingEventsCommand.due(50)).block(Duration.ofSeconds(5));
            assertThat(report.claimed()).isEqualTo(2);
            assertThat(publisher.confirmedIds()).containsExactlyInAnyOrder(ids.get(3), ids.get(4));
            other.rollback();
        }

        PublishReport rest = handler.handle(PublishPendingEventsCommand.due(50)).block();
        assertThat(rest.published()).isEqualTo(3);
        assertThat(publisher.confirmedIds()).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void concurrentRunsNeverClaimTheSameEvent() {
        UUID tenant = insertTenant();
        IntStream.range(0, 40).forEach(i -> insertEvent(tenant));
        publisher.delay(Duration.ofMillis(200));

        var both = Mono.zip(
                Mono.defer(() -> handler.handle(PublishPendingEventsCommand.due(25))),
                Mono.defer(() -> handler.handle(PublishPendingEventsCommand.due(25)))
                        .subscribeOn(reactor.core.scheduler.Schedulers.parallel()))
                .block(Duration.ofSeconds(30));

        assertThat(both.getT1().claimed() + both.getT2().claimed()).isEqualTo(40);
        assertThat(publisher.attemptedIds()).hasSize(40).doesNotHaveDuplicates();
        assertThat(new HashSet<>(publisher.confirmedIds())).hasSize(40);
        assertThat(countByStatus("PUBLISHED")).isEqualTo(40);
    }

    @Test
    void failuresBackOffAndEndInFailed() {
        UUID tenant = insertTenant();
        long id = insertEvent(tenant);
        publisher.mode(FakeEventPublisher.Mode.FAIL);

        assertThat(handler.handle(PublishPendingEventsCommand.due(50)).block())
                .isEqualTo(new PublishReport(1, 0, 1, false));
        assertThat(attempts(id)).isEqualTo(1);
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(secondsUntilNextAttempt(id)).isBetween(3.0, 5.5);
        // Aún no vence: la siguiente ejecución no lo toma.
        assertThat(handler.handle(PublishPendingEventsCommand.due(50)).block().claimed()).isZero();

        makeDue(id);
        handler.handle(PublishPendingEventsCommand.due(50)).block();
        assertThat(attempts(id)).isEqualTo(2);
        assertThat(secondsUntilNextAttempt(id)).isBetween(8.0, 10.5);

        makeDue(id);
        handler.handle(PublishPendingEventsCommand.due(50)).block(); // tercer intento = max-attempts
        assertThat(attempts(id)).isEqualTo(3);
        assertThat(status(id)).isEqualTo("FAILED");

        makeDue(id);
        publisher.mode(FakeEventPublisher.Mode.CONFIRM);
        assertThat(handler.handle(PublishPendingEventsCommand.due(50)).block().claimed()).isZero();
        assertThat(publishedAtSet(id)).isFalse();
    }

    // --- camino rápido ---

    @Test
    void createTenantPublishesItsEventBeforeResponding() {
        UUID tenantId = createTenant(newSlug());

        long id = eventIdOf(tenantId);
        assertThat(status(id)).isEqualTo("PUBLISHED");
        assertThat(publisher.confirmedIds()).containsExactly(id);
    }

    @Test
    void failingPubSubDoesNotChangeTheCreateResponse() {
        publisher.mode(FakeEventPublisher.Mode.FAIL);
        String slug = newSlug();

        var body = adminCreate(slug).expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();

        assertThat(body).containsEntry("slug", slug).containsEntry("pointsGoal", 10).containsKey("id");
        long id = eventIdOf(UUID.fromString(body.get("id").toString()));
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(attempts(id)).isEqualTo(1);
    }

    @Test
    void slowPubSubIsCutAtTheTimeoutAndTheSafetyNetPublishesLater() {
        publisher.mode(FakeEventPublisher.Mode.HANG);
        long start = System.nanoTime();

        UUID tenantId = createTenant(newSlug());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        long id = eventIdOf(tenantId);
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(attempts(id)).isZero();
        // Quedó reclamado por el plazo (120 s): ni siquiera el Scheduler lo toma antes.
        assertThat(secondsUntilNextAttempt(id)).isGreaterThan(100);

        makeDue(id);
        publisher.mode(FakeEventPublisher.Mode.CONFIRM);
        post("scheduler-token").expectStatus().isOk().expectBody().jsonPath("$.published").isEqualTo(1);
        assertThat(status(id)).isEqualTo("PUBLISHED");
    }

    // --- ayudas ---

    private static Jwt idToken(String email) {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of(AUDIENCE))
                .claim("email", email)
                .claim("email_verified", true)
                .build();
    }

    private WebTestClient.ResponseSpec post(String token) {
        return client.post().uri("/internal/outbox/publish").header("Authorization", "Bearer " + token).exchange();
    }

    private static String newSlug() {
        return "cafe-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private WebTestClient.ResponseSpec adminCreate(String slug) {
        return client.post().uri("/api/admin/tenants")
                .header("Authorization", "Bearer n8n-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("slug", slug, "name", "Café " + slug, "pointsGoal", 10, "rewardText", "Café gratis"))
                .exchange();
    }

    private UUID createTenant(String slug) {
        return UUID.fromString(adminCreate(slug).expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody().get("id").toString());
    }

    private UUID insertTenant() {
        String slug = newSlug();
        return db.sql("""
                        INSERT INTO loyalty.tenants (slug, business_name, points_goal, reward_text)
                        VALUES (:slug, 'Café', 10, 'Café gratis') RETURNING id""")
                .bind("slug", slug).map(row -> row.get(0, UUID.class)).one().block();
    }

    private long insertEvent(UUID tenantId) {
        return db.sql("""
                        INSERT INTO loyalty.outbox (tenant_id, event_type, payload)
                        VALUES (:t, 'TenantChanged', CAST(:p AS jsonb)) RETURNING id""")
                .bind("t", tenantId).bind("p", "{\"slug\":\"x\"}")
                .map(row -> row.get(0, Long.class)).one().block();
    }

    private long eventIdOf(UUID tenantId) {
        return db.sql("SELECT id FROM loyalty.outbox WHERE tenant_id = :t").bind("t", tenantId)
                .map(row -> row.get(0, Long.class)).one().block();
    }

    private String status(long id) {
        return db.sql("SELECT status FROM loyalty.outbox WHERE id = :id").bind("id", id)
                .map(row -> row.get(0, String.class)).one().block();
    }

    private int attempts(long id) {
        return db.sql("SELECT attempts FROM loyalty.outbox WHERE id = :id").bind("id", id)
                .map(row -> row.get(0, Integer.class)).one().block();
    }

    private boolean publishedAtSet(long id) {
        return db.sql("SELECT published_at IS NOT NULL FROM loyalty.outbox WHERE id = :id").bind("id", id)
                .map(row -> row.get(0, Boolean.class)).one().block();
    }

    private double secondsUntilNextAttempt(long id) {
        return db.sql("SELECT EXTRACT(EPOCH FROM next_attempt_at - NOW())::float8 FROM loyalty.outbox WHERE id = :id")
                .bind("id", id).map(row -> row.get(0, Double.class)).one().block();
    }

    private long countByStatus(String status) {
        return db.sql("SELECT count(*) FROM loyalty.outbox WHERE status = :s").bind("s", status)
                .map(row -> row.get(0, Long.class)).one().block();
    }

    private void makeDue(long id) {
        db.sql("UPDATE loyalty.outbox SET next_attempt_at = NOW() - INTERVAL '1 second' WHERE id = :id")
                .bind("id", id).fetch().rowsUpdated().block();
    }
}
