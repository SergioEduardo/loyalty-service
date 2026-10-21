package com.axioma.loyalty.adapter.in.web.enroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.out.wallet.ServiceIdTokenSource;
import com.axioma.loyalty.support.FakeWalletService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * VERIFICATION_SENDER=log con APP_ENV=dev y solo sms habilitado: el log reemplaza al canal y el código
 * sale en un log INFO con el canal (y sirve para dar la tarjeta), pero ni el teléfono, ni el nombre, ni el
 * hash, ni la IP. Un canal no habilitado responde CHANNEL_NOT_AVAILABLE. Se omite sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.enroll.otp-hash-salt=" + EnrollmentItSupport.SALT,
                "app.enroll.verification-sender=log",
                "app.enroll.verification-enabled-channels=sms",
                "app.enroll.app-env=dev"
        })
class EnrollmentLogSenderIT extends EnrollmentItSupport {

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
    ServiceIdTokenSource idTokens;

    @Test
    void codeGoesToTheLogAndNothingElse(CapturedOutput output) throws Exception {
        when(idTokens.idToken(anyString())).thenReturn("id-token");
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        String phone = freshNationalPhone();
        String name = "Leonora Bitácora";
        String ip = "192.0.2.201";

        @SuppressWarnings("unchecked")
        Map<String, Object> body = requestCode(slug, phone, ip).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        String id = (String) body.get("verificationId");
        assertThat(body.get("channel")).isEqualTo("sms");
        assertThat(body.get("availableChannels")).isEqualTo(List.of("sms"));

        Matcher m = Pattern.compile("INFO .*Verification code \\(VERIFICATION_SENDER=log\\) tenantId=" + tenantId
                + " verificationId=" + id + " channel=sms code=([0-9]{6})").matcher(output.getAll());
        assertThat(m.find()).isTrue();
        String code = m.group(1);

        enroll(slug, phone, name, id, code, ip).expectStatus().isOk().expectBody().jsonPath("$.isNew").isEqualTo(true);

        String logs = output.getAll();
        assertThat(logs).doesNotContain(phone).doesNotContain(e164(phone)).doesNotContain(name)
                .doesNotContain(hmac(tenantId, e164(phone), code)).doesNotContain(ip);
        // El código aparece solo en la línea del modo log.
        Pattern codePattern = Pattern.compile("(?<![0-9A-Fa-f])" + code + "(?![0-9A-Fa-f])");
        assertThat(logs.lines().filter(line -> codePattern.matcher(line).find()))
                .singleElement().asString().contains("VERIFICATION_SENDER=log");
    }

    @Test
    void channelThatIsNotEnabledIs400(CapturedOutput output) {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        requestCode(slug, freshNationalPhone(), freshIp(), "whatsapp").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CHANNEL_NOT_AVAILABLE")
                .jsonPath("$.message").isEqualTo("Ese medio de envío no está disponible.");
        assertThat(count("SELECT count(*) FROM loyalty.phone_verifications WHERE tenant_id = :t", tenantId)).isZero();
        assertThat(output.getAll()).doesNotContain("Verification code (VERIFICATION_SENDER=log) tenantId=" + tenantId);
    }
}
