package com.axioma.loyalty.adapter.in.web.enroll;

import com.axioma.loyalty.domain.model.PhoneE164;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/** Base de los IT del alta: Postgres real, negocios creados por SQL y llamadas a los dos endpoints. */
abstract class EnrollmentItSupport {

    static final String SALT = "sal-de-prueba-para-hmac-de-al-menos-32-bytes";
    private static final AtomicInteger IPS = new AtomicInteger();
    private static final AtomicInteger PHONES = new AtomicInteger();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    WebTestClient client;

    @Autowired
    DatabaseClient db;

    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> "postgresql://%s:%s@%s:%d/%s?sslmode=disable".formatted(
                POSTGRES.getUsername(), POSTGRES.getPassword(), POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName()));
    }

    static String newSlug() {
        return "cafe-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Una IP distinta en cada llamada, para no tocar los límites por IP salvo cuando se prueban. */
    static String freshIp() {
        int n = IPS.incrementAndGet();
        return "10." + (n >> 16 & 255) + "." + (n >> 8 & 255) + "." + (n & 255);
    }

    /** Celular de CDMX distinto en cada llamada, en formato nacional (sin +52). */
    static String freshNationalPhone() {
        return "55" + "%08d".formatted(10_000_000 + PHONES.incrementAndGet());
    }

    static String e164(String nationalPhone) {
        return "+52" + nationalPhone;
    }

    static String hmac(UUID tenantId, String phoneE164, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SALT.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (tenantId + "|" + new PhoneE164(phoneE164).value() + "|" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    UUID createTenant(String slug) {
        return db.sql("""
                        INSERT INTO loyalty.tenants (slug, business_name, points_goal, reward_text)
                        VALUES (:slug, :name, 10, 'Café gratis') RETURNING id""")
                .bind("slug", slug).bind("name", "Café " + slug)
                .map(row -> row.get(0, UUID.class)).one().block();
    }

    void deactivate(UUID tenantId) {
        db.sql("UPDATE loyalty.tenants SET is_active = FALSE WHERE id = :t").bind("t", tenantId)
                .fetch().rowsUpdated().block();
    }

    WebTestClient.ResponseSpec requestCode(String slug, String phone, String ip) {
        return requestCode(slug, phone, ip, null);
    }

    /** channel null: sin el campo (contrato anterior). */
    WebTestClient.ResponseSpec requestCode(String slug, String phone, String ip, String channel) {
        var body = new java.util.HashMap<String, Object>();
        body.put("slug", slug);
        body.put("phone", phone);
        if (channel != null) {
            body.put("channel", channel);
        }
        return client.post().uri("/api/public/enroll/verification")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    WebTestClient.ResponseSpec requestCode(String slug, String phone) {
        return requestCode(slug, phone, freshIp());
    }

    /** verificationId de una solicitud correcta. */
    String verificationId(String slug, String phone) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = requestCode(slug, phone).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("verificationId");
    }

    WebTestClient.ResponseSpec enroll(String slug, String phone, String name, String verificationId, String code,
                                      String ip) {
        var body = new java.util.HashMap<String, Object>();
        body.put("slug", slug);
        body.put("phone", phone);
        body.put("name", name);
        body.put("verificationId", verificationId);
        body.put("code", code);
        return client.post().uri("/api/public/enroll/card")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    WebTestClient.ResponseSpec enroll(String slug, String phone, String name, String verificationId, String code) {
        return enroll(slug, phone, name, verificationId, code, freshIp());
    }

    int attempts(String verificationId) {
        return db.sql("SELECT attempts FROM loyalty.phone_verifications WHERE id = :id")
                .bind("id", UUID.fromString(verificationId)).map(row -> row.get(0, Integer.class)).one().block();
    }

    long count(String sql, UUID tenantId) {
        return db.sql(sql).bind("t", tenantId).map(row -> row.get(0, Long.class)).one().block();
    }
}
