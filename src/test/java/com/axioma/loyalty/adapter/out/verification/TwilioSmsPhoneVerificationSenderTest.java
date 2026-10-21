package com.axioma.loyalty.adapter.out.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.config.TwilioProperties;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.VerificationDeliveryException;
import com.axioma.loyalty.support.FakeProviderApi;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * Contrato con Twilio Programmable Messaging contra un servidor simulado: ruta, autenticación básica,
 * formulario (To, MessagingServiceSid o From, Body); errores de Twilio; nunca reintenta; logs sin
 * teléfono, código, texto ni token.
 */
@ExtendWith(OutputCaptureExtension.class)
class TwilioSmsPhoneVerificationSenderTest {

    private static final String ACCOUNT_SID = "AC" + "0123456789abcdef0123456789abcdef";
    private static final String AUTH_TOKEN = "auth-token-de-prueba";
    private static final String SERVICE_SID = "MG" + "fedcba9876543210fedcba9876543210";
    private static final String FROM = "+15005550006";
    private static final String PHONE = "+525512345678";
    private static final String CODE = "731904";
    private static final String MESSAGE_SID = "SM" + "11112222333344445555666677778888";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID VERIFICATION_ID = UUID.randomUUID();
    private static final VerificationMessage MESSAGE = new VerificationMessage(TENANT_ID, "Café Ñandú",
            VERIFICATION_ID, new PhoneE164(PHONE), CODE, VerificationChannel.SMS, 10);
    private static final String CREATED = """
            {"sid":"%s","status":"accepted","to":"+525512345678","body":"…"}""".formatted(MESSAGE_SID);
    private static final FakeProviderApi TWILIO = new FakeProviderApi();

    @AfterAll
    static void stop() {
        TWILIO.close();
    }

    @BeforeEach
    void reset() {
        TWILIO.reset();
    }

    @Test
    void postsTheFormWithMessagingServiceAndBasicAuth(CapturedOutput output) {
        TWILIO.reply(201, CREATED);

        StepVerifier.create(sender(TWILIO.baseUrl(), SERVICE_SID, FROM, 2000L).send(MESSAGE)).verifyComplete();

        assertThat(TWILIO.received()).hasSize(1);
        var request = TWILIO.received().get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json");
        assertThat(request.header(HttpHeaders.AUTHORIZATION)).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString((ACCOUNT_SID + ":" + AUTH_TOKEN).getBytes(StandardCharsets.UTF_8)));
        assertThat(request.header(HttpHeaders.CONTENT_TYPE)).startsWith("application/x-www-form-urlencoded");
        // Con Messaging Service no se manda From: Twilio elige el remitente del pool.
        assertThat(form(request.body())).containsExactly(
                Map.entry("To", PHONE),
                Map.entry("MessagingServiceSid", SERVICE_SID),
                Map.entry("Body", "Cafe Nandu: tu codigo es 731904. Vence en 10 min. No lo compartas."));
        assertThat(output.getOut()).containsPattern("INFO .*SMS verification accepted by Twilio tenantId=" + TENANT_ID
                + " verificationId=" + VERIFICATION_ID + " sid=" + MESSAGE_SID + " status=accepted durationMs=\\d+");
        assertNoSecrets(output);
    }

    @Test
    void usesFromWhenThereIsNoMessagingService() {
        TWILIO.reply(201, CREATED.replace("accepted", "queued"));

        StepVerifier.create(sender(TWILIO.baseUrl(), null, FROM, 2000L).send(MESSAGE)).verifyComplete();

        assertThat(form(TWILIO.received().get(0).body()))
                .containsOnlyKeys("To", "From", "Body")
                .containsEntry("From", FROM)
                .containsEntry("To", PHONE);
    }

    @Test
    void invalidToNumberIsRecipientUnreachable(CapturedOutput output) {
        TWILIO.reply(400, """
                {"code":21211,"message":"The 'To' number +525512345678 is not a valid phone number.",
                 "more_info":"https://www.twilio.com/docs/errors/21211","status":400}""");

        StepVerifier.create(sender(TWILIO.baseUrl(), SERVICE_SID, null, 2000L).send(MESSAGE))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(VerificationDeliveryException.class);
                    var delivery = (VerificationDeliveryException) e;
                    assertThat(delivery.getCode()).isEqualTo(ErrorCode.RECIPIENT_UNREACHABLE);
                    assertThat(delivery.getProviderCode()).isEqualTo("21211");
                    assertThat(delivery.getMessage()).doesNotContain("Twilio").doesNotContain(PHONE);
                })
                .verify();
        assertThat(output.getOut()).contains("SMS recipient unreachable tenantId=" + TENANT_ID
                + " verificationId=" + VERIFICATION_ID + " status=400 providerCode=21211");
        assertNoSecrets(output);
    }

    @Test
    void rejectedCredentialsAreUnavailableAndLogError(CapturedOutput output) {
        TWILIO.reply(401, """
                {"code":20003,"message":"Authenticate","more_info":"https://www.twilio.com/docs/errors/20003","status":401}""");

        StepVerifier.create(sender(TWILIO.baseUrl(), SERVICE_SID, null, 2000L).send(MESSAGE))
                .expectErrorSatisfies(unavailable("20003"))
                .verify();
        assertThat(output.getOut())
                .containsPattern("ERROR .*Authentication to Twilio was rejected status=401 providerCode=20003");
        assertNoSecrets(output);
    }

    @Test
    void otherClientErrorsAreUnavailableWithoutProviderDetails(CapturedOutput output) {
        TWILIO.reply(400, """
                {"code":21610,"message":"Attempt to send to unsubscribed recipient +525512345678","status":400}""");

        StepVerifier.create(sender(TWILIO.baseUrl(), SERVICE_SID, null, 2000L).send(MESSAGE))
                .expectErrorSatisfies(unavailable("21610"))
                .verify();
        assertThat(output.getOut()).containsPattern("ERROR .*Twilio rejected the message .* status=400 providerCode=21610")
                .doesNotContain("unsubscribed");
        assertNoSecrets(output);
    }

    @Test
    void serverErrorsAreNeverRetried(CapturedOutput output) {
        for (int status : new int[]{500, 503, 429}) {
            TWILIO.reset();
            TWILIO.reply(status, "{\"code\":20500,\"message\":\"Internal Server Error\",\"status\":" + status + "}");
            StepVerifier.create(sender(TWILIO.baseUrl(), SERVICE_SID, null, 2000L).send(MESSAGE))
                    .expectErrorSatisfies(unavailable("20500"))
                    .verify();
            assertThat(TWILIO.received()).hasSize(1);
        }
        assertThat(output.getOut()).containsPattern("WARN .*Twilio failed .* status=503 providerCode=20500");
        assertNoSecrets(output);
    }

    @Test
    void timeoutIsNotRetried(CapturedOutput output) throws Exception {
        TWILIO.replySlowly(201, CREATED, 1500);

        StepVerifier.create(sender(TWILIO.baseUrl(), SERVICE_SID, null, 300L).send(MESSAGE))
                .expectErrorSatisfies(unavailable(null))
                .verify();
        Thread.sleep(1500);
        assertThat(TWILIO.received()).hasSize(1);
        assertThat(output.getOut()).containsPattern("WARN .*SMS verification send failed tenantId=" + TENANT_ID
                + " verificationId=" + VERIFICATION_ID + " reason=timeout durationMs=\\d+");
        assertNoSecrets(output);
    }

    @Test
    void refusedConnectionIsNotRetried(CapturedOutput output) {
        StepVerifier.create(sender(FakeProviderApi.refusedUrl(), SERVICE_SID, null, 2000L).send(MESSAGE))
                .expectErrorSatisfies(unavailable(null))
                .verify();
        assertThat(output.getOut().lines().filter(line -> line.contains("SMS verification send failed")))
                .singleElement().asString().contains("reason=WebClientRequestException");
        assertNoSecrets(output);
    }

    @Test
    void propertiesToStringHidesTheToken() {
        var properties = new TwilioProperties(ACCOUNT_SID, AUTH_TOKEN, null, FROM, null, null);
        assertThat(properties.toString()).doesNotContain(AUTH_TOKEN).doesNotContain(FROM);
    }

    private static TwilioSmsPhoneVerificationSender sender(String baseUrl, String serviceSid, String from,
                                                           long timeoutMs) {
        var properties = new TwilioProperties(ACCOUNT_SID, AUTH_TOKEN, serviceSid, from, timeoutMs, baseUrl);
        return new TwilioSmsPhoneVerificationSender(WebClient.builder().baseUrl(properties.apiBaseUrl()).build(),
                properties);
    }

    /** Formulario decodificado, en el orden en que llegó. */
    private static Map<String, String> form(String body) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return form;
    }

    private static Consumer<Throwable> unavailable(String providerCode) {
        return e -> {
            assertThat(e).isInstanceOf(VerificationDeliveryException.class);
            var delivery = (VerificationDeliveryException) e;
            assertThat(delivery.getCode()).isEqualTo(ErrorCode.VERIFICATION_UNAVAILABLE);
            assertThat(delivery.getProviderCode()).isEqualTo(providerCode);
            assertThat(delivery.getMessage()).isEqualTo(ErrorCode.VERIFICATION_UNAVAILABLE.defaultMessage());
        };
    }

    private static void assertNoSecrets(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(PHONE).doesNotContain("5512345678").doesNotContain(CODE)
                .doesNotContain(AUTH_TOKEN).doesNotContain("tu codigo");
    }
}
