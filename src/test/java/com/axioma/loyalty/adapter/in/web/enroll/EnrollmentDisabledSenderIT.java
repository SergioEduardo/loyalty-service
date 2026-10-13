package com.axioma.loyalty.adapter.in.web.enroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Configuración por defecto: VERIFICATION_SENDER sin definir (= disabled) y sin WALLET_SERVICE_URL.
 * Pedir un código responde 503 VERIFICATION_UNAVAILABLE sin crear nada. Se omite sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.enroll.otp-hash-salt=" + EnrollmentItSupport.SALT)
class EnrollmentDisabledSenderIT extends EnrollmentItSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        database(registry);
    }

    @Test
    void disabledSenderAnswers503() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        requestCode(slug, freshNationalPhone()).expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("VERIFICATION_UNAVAILABLE");
        assertThat(count("SELECT count(*) FROM loyalty.phone_verifications WHERE tenant_id = :t", tenantId)).isZero();
    }

    @Test
    void enrollWithoutWalletUrlIs503AfterCreatingTheCard() {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        String code = "123456";
        // Verificación creada a mano: con el envío desactivado no hay otra forma de obtener un código.
        UUID id = db.sql("""
                        INSERT INTO loyalty.phone_verifications (tenant_id, phone_e164, code_hash, expires_at)
                        VALUES (:t, :p, :h, NOW() + INTERVAL '10 minutes') RETURNING id""")
                .bind("t", tenantId).bind("p", e164(phone)).bind("h", hmac(tenantId, e164(phone), code))
                .map(row -> row.get(0, UUID.class)).one().block();
        enroll(slug, phone, "Ana", id.toString(), code).expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("PASS_SERVICE_UNAVAILABLE");
        assertThat(count("SELECT count(*) FROM loyalty.cards WHERE tenant_id = :t", tenantId)).isEqualTo(1);
    }
}
