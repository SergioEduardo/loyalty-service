package com.axioma.loyalty.adapter.in.web.enroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.out.wallet.ServiceIdTokenSource;
import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import com.axioma.loyalty.support.FakeWalletService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

/**
 * Alta de la tarjeta de punta a punta (12.6 y 12.7) contra un Postgres real y un wallet-service falso.
 * El envío del código se sustituye por un mock que guarda el código por verificationId. Cada prueba usa
 * su propio negocio, teléfono e IPs. Se omite sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.enroll.otp-hash-salt=" + EnrollmentItSupport.SALT,
                "app.enroll.phone-limit-per-hour=3",
                "app.enroll.ip-limit-per-hour=5",
                "app.enroll.tenant-limit-per-hour=6",
                "app.enroll.card-ip-limit-per-hour=8",
                "app.enroll.code-ttl-minutes=10",
                "app.enroll.max-attempts=5",
                "app.wallet.audience=https://wallet.test",
                "app.wallet.issue-timeout-ms=800"
        })
class EnrollmentFlowIT extends EnrollmentItSupport {

    private static final String ID_TOKEN = "id-token-de-prueba";
    private static final FakeWalletService WALLET = new FakeWalletService();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        database(registry);
        registry.add("app.wallet.service-url", WALLET::baseUrl);
    }

    @AfterAll
    static void stopWallet() {
        WALLET.close();
    }

    @MockitoBean
    PhoneVerificationSenderPort sender;

    @MockitoBean
    ServiceIdTokenSource idTokens;

    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @BeforeEach
    void stubs() throws Exception {
        WALLET.mode(FakeWalletService.Mode.OK);
        when(idTokens.idToken(anyString())).thenReturn(ID_TOKEN);
        when(sender.ensureReady()).thenReturn(Mono.empty());
        when(sender.send(any())).thenAnswer(invocation -> {
            VerificationMessage message = invocation.getArgument(0);
            codes.put(message.verificationId().toString(), message.code());
            return Mono.empty();
        });
    }

    // --- pedir el código (12.6) ---

    @Test
    void requestCodeStoresOnlyTheHashWithItsTtl() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();

        Instant before = Instant.now();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = requestCode(slug, phone).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body).containsOnlyKeys("verificationId", "expiresAt");
        String id = (String) body.get("verificationId");
        Instant expiresAt = Instant.parse((String) body.get("expiresAt"));
        assertThat(Duration.between(before, expiresAt)).isBetween(Duration.ofMinutes(9), Duration.ofMinutes(11));

        String code = codes.get(id);
        assertThat(code).matches("[0-9]{6}");
        var row = db.sql("SELECT phone_e164, code_hash, attempts, verified_at, expires_at FROM loyalty.phone_verifications WHERE id = :id")
                .bind("id", UUID.fromString(id))
                .map(r -> Map.of(
                        "phone", r.get("phone_e164", String.class),
                        "hash", r.get("code_hash", String.class),
                        "attempts", r.get("attempts", Integer.class),
                        "verified", r.get("verified_at", OffsetDateTime.class) != null,
                        "expires", r.get("expires_at", OffsetDateTime.class).toInstant()))
                .one().block();
        assertThat(row.get("phone")).isEqualTo(e164(phone));
        assertThat((String) row.get("hash")).hasSize(64).isEqualTo(hmac(tenantId, e164(phone), code)).doesNotContain(code);
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("verified")).isEqualTo(false);
        assertThat(row.get("expires")).isEqualTo(expiresAt);
    }

    @Test
    void invalidPhone() {
        String slug = newSlug();
        createTenant(slug);
        for (String phone : List.of("123", "abc", "", "+52 55 1234", "+999123456789", "5".repeat(50))) {
            requestCode(slug, phone).expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_PHONE")
                    .jsonPath("$.message").isEqualTo("Revisa tu teléfono y tu nombre.");
        }
    }

    @Test
    void acceptsCommonMexicanFormats() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        requestCode(slug, "+52 " + phone.substring(0, 2) + " " + phone.substring(2, 6) + " " + phone.substring(6))
                .expectStatus().isOk();
        requestCode(slug, "(" + phone.substring(0, 2) + ") " + phone.substring(2)).expectStatus().isOk();
        assertThat(db.sql("SELECT DISTINCT phone_e164 FROM loyalty.phone_verifications WHERE tenant_id = :t")
                .bind("t", tenantId).map(r -> r.get(0, String.class)).all().collectList().block())
                .containsExactly(e164(phone));
    }

    @Test
    void unknownOrInactiveTenant() {
        String closed = newSlug();
        deactivate(createTenant(closed));
        for (String slug : List.of("no-existe-" + newSlug(), closed, "Slug Inválido!")) {
            requestCode(slug, freshNationalPhone()).expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo("TENANT_NOT_FOUND");
        }
    }

    @Test
    void phoneLimitThreePerHour() {
        String slug = newSlug();
        createTenant(slug);
        String phone = freshNationalPhone();
        for (int i = 0; i < 3; i++) {
            requestCode(slug, phone).expectStatus().isOk();
        }
        requestCode(slug, phone).expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED")
                .jsonPath("$.message").isEqualTo("Demasiados intentos. Espera unos minutos.");
        // Otro teléfono en el mismo negocio sigue pudiendo.
        requestCode(slug, freshNationalPhone()).expectStatus().isOk();
    }

    @Test
    void phoneLimitHoldsUnderConcurrency() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        List<CompletableFuture<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(CompletableFuture.supplyAsync(() -> requestCode(slug, phone).returnResult(String.class)
                    .getStatus().value()));
        }
        List<Integer> statuses = calls.stream().map(CompletableFuture::join).toList();
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(3);
        assertThat(statuses).filteredOn(s -> s == 429).hasSize(3);
        assertThat(count("SELECT count(*) FROM loyalty.phone_verifications WHERE tenant_id = :t", tenantId)).isEqualTo(3);
    }

    @Test
    void ipLimitPerHour() {
        String ip = "198.51.100.10";
        // El límite por IP cuenta en todos los negocios.
        for (int i = 0; i < 5; i++) {
            String slug = newSlug();
            createTenant(slug);
            requestCode(slug, freshNationalPhone(), ip).expectStatus().isOk();
        }
        String slug = newSlug();
        createTenant(slug);
        requestCode(slug, freshNationalPhone(), ip).expectStatus().isEqualTo(429)
                .expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
        requestCode(slug, freshNationalPhone(), "198.51.100.11").expectStatus().isOk();
        // La IP no se guarda en claro.
        assertThat(db.sql("SELECT count(*) FROM loyalty.enroll_rate_limits WHERE limit_key LIKE '%198.51.100%'")
                .map(r -> r.get(0, Long.class)).one().block()).isZero();
    }

    @Test
    void tenantLimitPerHour() {
        String slug = newSlug();
        createTenant(slug);
        for (int i = 0; i < 6; i++) {
            requestCode(slug, freshNationalPhone()).expectStatus().isOk();
        }
        requestCode(slug, freshNationalPhone()).expectStatus().isEqualTo(429)
                .expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
        String other = newSlug();
        createTenant(other);
        requestCode(other, freshNationalPhone()).expectStatus().isOk();
    }

    @Test
    void failedSendAnswers503AndStillCounts() {
        doReturn(Mono.error(new IllegalStateException("WhatsApp caído"))).when(sender).send(any());
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        for (int i = 0; i < 3; i++) {
            requestCode(slug, phone).expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.code").isEqualTo("VERIFICATION_UNAVAILABLE")
                    .jsonPath("$.message").isEqualTo("No pudimos enviar el código. Intenta de nuevo.");
        }
        assertThat(count("SELECT count(*) FROM loyalty.phone_verifications WHERE tenant_id = :t", tenantId)).isEqualTo(3);
        requestCode(slug, phone).expectStatus().isEqualTo(429);
    }

    @Test
    void responseDoesNotRevealExistingCard() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String withCard = freshNationalPhone();
        db.sql("INSERT INTO loyalty.cards (tenant_id, phone_e164, holder_name, points) VALUES (:t, :p, 'Ana', 4)")
                .bind("t", tenantId).bind("p", e164(withCard)).fetch().rowsUpdated().block();
        String withoutCard = freshNationalPhone();

        var a = requestCode(slug, withCard).returnResult(Map.class);
        var b = requestCode(slug, withoutCard).returnResult(Map.class);
        assertThat(a.getStatus()).isEqualTo(b.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyA = a.getResponseBody().blockFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyB = b.getResponseBody().blockFirst();
        assertThat(bodyA.keySet()).isEqualTo(bodyB.keySet());
        assertThat(bodyA).containsOnlyKeys("verificationId", "expiresAt");
        assertThat(a.getResponseHeaders().keySet()).isEqualTo(b.getResponseHeaders().keySet());
    }

    // --- dar la tarjeta (12.7) ---

    @Test
    void enrollNewCard() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = enroll(slug, phone, "  Ana López  ", id, codes.get(id)).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body).containsOnlyKeys("isNew", "apple", "google");
        assertThat(body.get("isNew")).isEqualTo(true);
        assertThat(body.get("google")).isNull();
        @SuppressWarnings("unchecked")
        var apple = (Map<String, Object>) body.get("apple");
        assertThat(apple).containsOnlyKeys("url", "expiresAt");

        var card = db.sql("SELECT id, holder_name, points FROM loyalty.cards WHERE tenant_id = :t AND phone_e164 = :p")
                .bind("t", tenantId).bind("p", e164(phone))
                .map(r -> Map.of("id", r.get("id", UUID.class), "name", r.get("holder_name", String.class),
                        "points", r.get("points", Integer.class)))
                .one().block();
        assertThat(card.get("name")).isEqualTo("Ana López");
        assertThat(apple.get("url")).isEqualTo(FakeWalletService.linkFor(card.get("id").toString()));
        // El cardId solo va dentro del enlace firmado del wallet-service (así lo arma el 12.8).
        assertThat(body.toString()).doesNotContain("Ana").doesNotContain(phone);

        var request = WALLET.received().stream().filter(r -> r.body().contains(card.get("id").toString()))
                .findFirst().orElseThrow();
        assertThat(request.authorization()).isEqualTo("Bearer " + ID_TOKEN);
        assertThat(request.body()).contains("\"tenantId\":\"" + tenantId + "\"")
                .contains("\"holderName\":\"Ana López\"").contains("\"points\":0");

        assertThat(db.sql("SELECT verified_at IS NOT NULL FROM loyalty.phone_verifications WHERE id = :id")
                .bind("id", UUID.fromString(id)).map(r -> r.get(0, Boolean.class)).one().block()).isTrue();
        assertThat(attempts(id)).isEqualTo(1);
    }

    @Test
    void wrongCodeCountsAttemptsAndLocksAtTheFifth() {
        String slug = newSlug();
        createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        String wrong = codes.get(id).equals("000000") ? "111111" : "000000";

        for (int i = 1; i <= 4; i++) {
            enroll(slug, phone, "Ana", id, wrong).expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_CODE")
                    .jsonPath("$.message").isEqualTo("El código no es correcto.");
            assertThat(attempts(id)).isEqualTo(i);
        }
        enroll(slug, phone, "Ana", id, wrong).expectStatus().isBadRequest()
                .expectHeader().doesNotExist("Retry-After")
                .expectBody().jsonPath("$.code").isEqualTo("TOO_MANY_ATTEMPTS")
                .jsonPath("$.message").isEqualTo("Demasiados intentos. Pide un código nuevo.");
        assertThat(attempts(id)).isEqualTo(5);
        // Ya no sirve ni con el código correcto, y no suma más intentos.
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("TOO_MANY_ATTEMPTS");
        assertThat(attempts(id)).isEqualTo(5);
    }

    @Test
    void correctCodeOnTheFifthAttemptStillWorks() {
        String slug = newSlug();
        createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        String wrong = codes.get(id).equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            enroll(slug, phone, "Ana", id, wrong).expectStatus().isBadRequest();
        }
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isOk();
    }

    @Test
    void expiredCode() {
        String slug = newSlug();
        createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        db.sql("UPDATE loyalty.phone_verifications SET expires_at = NOW() - INTERVAL '1 second' WHERE id = :id")
                .bind("id", UUID.fromString(id)).fetch().rowsUpdated().block();
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("CODE_EXPIRED")
                .jsonPath("$.message").isEqualTo("El código venció. Pide uno nuevo.");
        assertThat(attempts(id)).isZero();
    }

    @Test
    void verificationOfAnotherPhoneOrTenantIsInvalidCode() {
        String slug = newSlug();
        createTenant(slug);
        String otherSlug = newSlug();
        createTenant(otherSlug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        String code = codes.get(id);

        List<String> bodies = new ArrayList<>();
        bodies.add(enroll(slug, freshNationalPhone(), "Ana", id, code).expectStatus().isBadRequest()
                .expectBody(String.class).returnResult().getResponseBody());
        bodies.add(enroll(otherSlug, phone, "Ana", id, code).expectStatus().isBadRequest()
                .expectBody(String.class).returnResult().getResponseBody());
        bodies.add(enroll(slug, phone, "Ana", UUID.randomUUID().toString(), code).expectStatus().isBadRequest()
                .expectBody(String.class).returnResult().getResponseBody());
        bodies.add(enroll(slug, phone, "Ana", "no-es-un-uuid", code).expectStatus().isBadRequest()
                .expectBody(String.class).returnResult().getResponseBody());
        assertThat(Set.copyOf(bodies)).hasSize(1);
        assertThat(bodies.get(0)).contains("INVALID_CODE");
        // No gastaron intentos de la verificación real, que sigue sirviendo.
        assertThat(attempts(id)).isZero();
        enroll(slug, phone, "Ana", id, code).expectStatus().isOk();
    }

    @Test
    void verifiedVerificationWorksAgainWithoutComparing() {
        String slug = newSlug();
        createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isOk()
                .expectBody().jsonPath("$.isNew").isEqualTo(true);
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isOk()
                .expectBody().jsonPath("$.isNew").isEqualTo(false);
        assertThat(attempts(id)).isEqualTo(1);
    }

    @Test
    void existingCardKeepsItsName() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        UUID cardId = db.sql("INSERT INTO loyalty.cards (tenant_id, phone_e164, holder_name, points) "
                        + "VALUES (:t, :p, 'Nombre Original', 7) RETURNING id")
                .bind("t", tenantId).bind("p", e164(phone)).map(r -> r.get(0, UUID.class)).one().block();
        String id = verificationId(slug, phone);

        enroll(slug, phone, "Otro Nombre", id, codes.get(id)).expectStatus().isOk()
                .expectBody().jsonPath("$.isNew").isEqualTo(false)
                .jsonPath("$.apple.url").isEqualTo(FakeWalletService.linkFor(cardId.toString()));
        assertThat(count("SELECT count(*) FROM loyalty.cards WHERE tenant_id = :t", tenantId)).isEqualTo(1);
        assertThat(db.sql("SELECT holder_name FROM loyalty.cards WHERE id = :c").bind("c", cardId)
                .map(r -> r.get(0, String.class)).one().block()).isEqualTo("Nombre Original");
        var request = WALLET.received().stream().filter(r -> r.body().contains(cardId.toString())).findFirst().orElseThrow();
        assertThat(request.body()).contains("\"holderName\":\"Nombre Original\"").contains("\"points\":7");
    }

    @Test
    void simultaneousEnrollmentsCreateOneCard() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        String first = verificationId(slug, phone);
        String second = verificationId(slug, phone);

        var a = CompletableFuture.supplyAsync(() -> enroll(slug, phone, "Ana", first, codes.get(first))
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody());
        var b = CompletableFuture.supplyAsync(() -> enroll(slug, phone, "Ana", second, codes.get(second))
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody());
        List<Object> isNew = List.of(a.join().get("isNew"), b.join().get("isNew"));
        assertThat(isNew).containsExactlyInAnyOrder(true, false);
        assertThat(count("SELECT count(*) FROM loyalty.cards WHERE tenant_id = :t", tenantId)).isEqualTo(1);
    }

    @Test
    void slowWalletAnswers503WithinTheTimeout() {
        WALLET.mode(FakeWalletService.Mode.SLOW);
        WALLET.slowMillis(3000);
        String slug = newSlug();
        createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        long start = System.nanoTime();
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("PASS_SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("No pudimos crear tu tarjeta. Intenta de nuevo.");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
    }

    @Test
    void walletDownThenRetrySucceedsWithTheSameVerification() {
        WALLET.mode(FakeWalletService.Mode.DOWN);
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("PASS_SERVICE_UNAVAILABLE");

        WALLET.mode(FakeWalletService.Mode.OK);
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isOk()
                .expectBody().jsonPath("$.apple.url").exists();
        assertThat(count("SELECT count(*) FROM loyalty.cards WHERE tenant_id = :t", tenantId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM loyalty.phone_verifications WHERE tenant_id = :t", tenantId)).isEqualTo(1);
        assertThat(attempts(id)).isEqualTo(1);
    }

    @Test
    void templateNotReadyIs409() {
        WALLET.mode(FakeWalletService.Mode.TEMPLATE_NOT_READY);
        String slug = newSlug();
        createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("TEMPLATE_NOT_READY")
                .jsonPath("$.message").isEqualTo("Estamos preparando la tarjeta. Intenta en un momento.");
    }

    @Test
    void rejectedAuthenticationIs503AndLogsError(CapturedOutput output) {
        WALLET.mode(FakeWalletService.Mode.UNAUTHORIZED);
        String slug = newSlug();
        createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        enroll(slug, phone, "Ana", id, codes.get(id)).expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("PASS_SERVICE_UNAVAILABLE");
        assertThat(output.getOut()).containsPattern("ERROR .*Authentication to wallet-service was rejected")
                .doesNotContain(ID_TOKEN);
    }

    @Test
    void invalidEnrollmentInput() {
        String slug = newSlug();
        createTenant(slug);
        String phone = freshNationalPhone();
        String id = verificationId(slug, phone);
        String code = codes.get(id);
        for (String name : List.of("", "   ", "a".repeat(81), "<b>Ana</b>", "Ana\u0000", "Ana\nLópez", "‮anA")) {
            enroll(slug, phone, name, id, code).expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_NAME");
        }
        enroll(slug, "123", "Ana", id, code).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_PHONE");
        for (String bad : List.of("12345", "1234567", "abcdef", "")) {
            enroll(slug, phone, "Ana", id, bad).expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_CODE");
        }
        assertThat(attempts(id)).isZero();
        enroll(slug, phone, "a".repeat(80), id, code).expectStatus().isOk();
    }

    @Test
    void enrollUnknownTenant() {
        enroll("no-existe-" + newSlug(), freshNationalPhone(), "Ana", UUID.randomUUID().toString(), "123456")
                .expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("TENANT_NOT_FOUND");
    }

    @Test
    void enrollIpLimit() {
        String slug = newSlug();
        createTenant(slug);
        String ip = "203.0.113.50";
        for (int i = 0; i < 8; i++) {
            enroll(slug, freshNationalPhone(), "Ana", UUID.randomUUID().toString(), "123456", ip)
                    .expectStatus().isBadRequest();
        }
        enroll(slug, freshNationalPhone(), "Ana", UUID.randomUUID().toString(), "123456", ip)
                .expectStatus().isEqualTo(429).expectHeader().exists("Retry-After")
                .expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
    }

    // --- privacidad ---

    @Test
    void logsNeverContainPhoneNameCodeHashOrIp(CapturedOutput output) {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        String name = "Zacarías Privado Quintanilla";
        String ip = "192.0.2.123";

        requestCode(slug, "123", ip).expectStatus().isBadRequest();
        String id = (String) requestCode(slug, phone, ip).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody().get("verificationId");
        String code = codes.get(id);
        String wrong = code.equals("000000") ? "111111" : "000000";
        enroll(slug, phone, name, id, wrong, ip).expectStatus().isBadRequest();
        WALLET.mode(FakeWalletService.Mode.DOWN);
        enroll(slug, phone, name, id, code, ip).expectStatus().isEqualTo(503);
        WALLET.mode(FakeWalletService.Mode.OK);
        enroll(slug, phone, name, id, code, ip).expectStatus().isOk();
        doReturn(Mono.error(new IllegalStateException("fallo con " + phone))).when(sender).send(any());
        requestCode(slug, phone, ip).expectStatus().isEqualTo(503);

        String hash = hmac(tenantId, e164(phone), code);
        String logs = output.getAll();
        assertThat(logs).contains("Card enrolled tenantId=" + tenantId);
        assertThat(logs).doesNotContain(phone).doesNotContain(e164(phone)).doesNotContain(name)
                .doesNotContain("Zacarías").doesNotContain(hash).doesNotContain(ip);
        assertThat(Pattern.compile("(?<![0-9A-Fa-f])" + code + "(?![0-9A-Fa-f])").matcher(logs).find()).isFalse();
        // El verificationId nunca va en la misma línea que el teléfono (que de por sí no aparece).
        logs.lines().filter(line -> line.contains(id)).forEach(line -> assertThat(line).doesNotContain(phone));
    }
}
