package com.axioma.loyalty.adapter.out.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.config.WhatsAppProperties;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.VerificationDeliveryException;
import com.axioma.loyalty.support.FakeProviderApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.test.StepVerifier;

/**
 * Contrato con WhatsApp Cloud API contra un servidor simulado: ruta, encabezados y payload de la
 * plantilla de autenticación; errores de Meta; un reintento solo si no hubo conexión; logs sin teléfono,
 * código ni token.
 */
@ExtendWith(OutputCaptureExtension.class)
class WhatsAppPhoneVerificationSenderTest {

    private static final String TOKEN = "EAAG-token-de-prueba";
    private static final String PHONE_NUMBER_ID = "106540352242922";
    private static final String PHONE = "+525512345678";
    private static final String CODE = "482915";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID VERIFICATION_ID = UUID.randomUUID();
    private static final VerificationMessage MESSAGE = new VerificationMessage(TENANT_ID, "Café Luna",
            VERIFICATION_ID, new PhoneE164(PHONE), CODE, VerificationChannel.WHATSAPP, 10);
    private static final FakeProviderApi META = new FakeProviderApi();
    private static final ObjectMapper JSON = new ObjectMapper();

    @AfterAll
    static void stop() {
        META.close();
    }

    @BeforeEach
    void reset() {
        META.reset();
    }

    @Test
    void sendsTheAuthenticationTemplateWithTheCodeInBodyAndCopyCodeButton(CapturedOutput output) throws Exception {
        META.reply(200, """
                {"messaging_product":"whatsapp","contacts":[{"input":"+525512345678","wa_id":"5215512345678"}],
                 "messages":[{"id":"wamid.HBgM"}]}""");

        StepVerifier.create(sender(META.baseUrl(), 2000L).send(MESSAGE)).verifyComplete();

        assertThat(META.received()).hasSize(1);
        var request = META.received().get(0);
        assertThat(request.method()).isEqualTo(HttpMethod.POST.name());
        assertThat(request.path()).isEqualTo("/v26.0/" + PHONE_NUMBER_ID + "/messages");
        assertThat(request.header(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + TOKEN);
        assertThat(request.header(HttpHeaders.CONTENT_TYPE)).startsWith("application/json");
        assertThat(JSON.readTree(request.body())).isEqualTo(JSON.readTree("""
                {
                  "messaging_product": "whatsapp",
                  "recipient_type": "individual",
                  "to": "+525512345678",
                  "type": "template",
                  "template": {
                    "name": "yiupi_codigo_verificacion",
                    "language": {"code": "es_MX"},
                    "components": [
                      {"type": "body", "parameters": [{"type": "text", "text": "482915"}]},
                      {"type": "button", "sub_type": "url", "index": "0",
                       "parameters": [{"type": "text", "text": "482915"}]}
                    ]
                  }
                }"""));
        assertThat(output.getOut()).containsPattern("INFO .*WhatsApp verification accepted tenantId=" + TENANT_ID
                + " verificationId=" + VERIFICATION_ID + " attempt=1 durationMs=\\d+");
        assertNoSecrets(output);
    }

    @Test
    void templateNameLanguageAndVersionComeFromConfiguration() throws Exception {
        var properties = new WhatsAppProperties(TOKEN, PHONE_NUMBER_ID, "otra_plantilla", "es", "v25.0", 2000L,
                META.baseUrl() + "/");
        StepVerifier.create(new WhatsAppPhoneVerificationSender(WebClient.builder().baseUrl(properties.apiBaseUrl())
                .build(), properties).send(MESSAGE)).verifyComplete();

        var request = META.received().get(0);
        assertThat(request.path()).isEqualTo("/v25.0/" + PHONE_NUMBER_ID + "/messages");
        var template = JSON.readTree(request.body()).get("template");
        assertThat(template.get("name").asText()).isEqualTo("otra_plantilla");
        assertThat(template.get("language").get("code").asText()).isEqualTo("es");
    }

    @Test
    void recipientThatCannotReceiveIsRecipientUnreachable(CapturedOutput output) {
        META.reply(400, """
                {"error":{"message":"(#131026) Message undeliverable","type":"OAuthException","code":131026,
                 "error_data":{"messaging_product":"whatsapp","details":"Message Undeliverable."},
                 "fbtrace_id":"AbCdEf"}}""");

        StepVerifier.create(sender(META.baseUrl(), 2000L).send(MESSAGE))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(VerificationDeliveryException.class);
                    var delivery = (VerificationDeliveryException) e;
                    assertThat(delivery.getCode()).isEqualTo(ErrorCode.RECIPIENT_UNREACHABLE);
                    assertThat(delivery.getProviderCode()).isEqualTo("131026");
                })
                .verify();
        assertThat(META.received()).hasSize(1);
        assertThat(output.getOut()).contains("WhatsApp recipient unreachable tenantId=" + TENANT_ID
                + " verificationId=" + VERIFICATION_ID + " status=400 providerCode=131026");
        assertNoSecrets(output);
    }

    @Test
    void rejectedTokenIsUnavailableAndLogsError(CapturedOutput output) {
        META.reply(401, """
                {"error":{"message":"Error validating access token","type":"OAuthException","code":190,
                 "fbtrace_id":"AbCdEf"}}""");

        StepVerifier.create(sender(META.baseUrl(), 2000L).send(MESSAGE))
                .expectErrorSatisfies(unavailable("190"))
                .verify();
        assertThat(META.received()).hasSize(1);
        assertThat(output.getOut())
                .containsPattern("ERROR .*Authentication to WhatsApp Cloud API was rejected status=401 providerCode=190");
        assertNoSecrets(output);
    }

    @Test
    void otherClientErrorsAreUnavailableAndLogErrorWithoutProviderDetails(CapturedOutput output) {
        META.reply(404, """
                {"error":{"message":"(#132001) Template name does not exist in the translation +525512345678",
                 "type":"OAuthException","code":132001,"fbtrace_id":"AbCdEf"}}""");

        StepVerifier.create(sender(META.baseUrl(), 2000L).send(MESSAGE))
                .expectErrorSatisfies(unavailable("132001"))
                .verify();
        assertThat(output.getOut()).containsPattern("ERROR .*WhatsApp Cloud API rejected the message tenantId="
                + TENANT_ID + " verificationId=" + VERIFICATION_ID + " status=404 providerCode=132001")
                .doesNotContain("Template name does not exist");
        assertNoSecrets(output);
    }

    @Test
    void serverErrorsAndThrottlingAreNotRetried(CapturedOutput output) {
        for (int status : new int[]{500, 503, 429}) {
            META.reset();
            META.reply(status, "{\"error\":{\"code\":131000,\"message\":\"Something went wrong\"}}");
            StepVerifier.create(sender(META.baseUrl(), 2000L).send(MESSAGE))
                    .expectErrorSatisfies(unavailable("131000"))
                    .verify();
            assertThat(META.received()).hasSize(1);
        }
        assertThat(output.getOut()).containsPattern("WARN .*WhatsApp Cloud API failed .* status=429 providerCode=131000");
        assertNoSecrets(output);
    }

    @Test
    void unreadableErrorBodyIsUnavailableWithoutProviderCode(CapturedOutput output) {
        META.reply(502, "<html>Bad gateway</html>");
        StepVerifier.create(sender(META.baseUrl(), 2000L).send(MESSAGE))
                .expectErrorSatisfies(unavailable(null))
                .verify();
        assertThat(output.getOut()).contains("status=502 providerCode=-");
    }

    @Test
    void timeoutAfterConnectingIsNotRetried(CapturedOutput output) throws Exception {
        META.replySlowly(200, "{}", 1500);

        StepVerifier.create(sender(META.baseUrl(), 300L).send(MESSAGE))
                .expectErrorSatisfies(unavailable(null))
                .verify();
        Thread.sleep(1500);
        assertThat(META.received()).hasSize(1);
        assertThat(output.getOut()).containsPattern("WARN .*WhatsApp verification send failed tenantId=" + TENANT_ID
                + " verificationId=" + VERIFICATION_ID + " reason=timeout durationMs=\\d+");
        assertNoSecrets(output);
    }

    @Test
    void refusedConnectionIsRetriedOnceThenUnavailable(CapturedOutput output) {
        StepVerifier.create(sender(FakeProviderApi.refusedUrl(), 2000L).send(MESSAGE))
                .expectErrorSatisfies(unavailable(null))
                .verify();
        String logs = output.getOut();
        assertThat(logs).contains("attempt=1/2").contains("attempt=2/2").doesNotContain("attempt=3");
        assertThat(logs).containsPattern("WARN .*WhatsApp verification send failed .* reason=WebClientRequestException");
        assertNoSecrets(output);
    }

    @Test
    void onlyConnectionFailuresAreRetryable() {
        var request = new HttpHeaders();
        var uri = URI.create("https://graph.facebook.com/v26.0/1/messages");
        assertThat(WhatsAppPhoneVerificationSender.connectionFailed(new WebClientRequestException(
                new ConnectException("Connection refused"), HttpMethod.POST, uri, request))).isTrue();
        assertThat(WhatsAppPhoneVerificationSender.connectionFailed(new WebClientRequestException(
                new UnknownHostException("graph.facebook.com"), HttpMethod.POST, uri, request))).isTrue();
        assertThat(WhatsAppPhoneVerificationSender.connectionFailed(new WebClientRequestException(
                new IOException("Connection reset by peer"), HttpMethod.POST, uri, request))).isFalse();
        assertThat(WhatsAppPhoneVerificationSender.connectionFailed(new TimeoutException())).isFalse();
        assertThat(WhatsAppPhoneVerificationSender.connectionFailed(new ConnectException())).isFalse();
    }

    @Test
    void payloadToStringHidesPhoneAndCode() {
        var payload = WhatsAppPhoneVerificationSender.TemplateMessage.authentication(PHONE, "plantilla", "es_MX", CODE);
        assertThat(payload.toString()).doesNotContain(PHONE).doesNotContain(CODE);
        assertThat(payload.template().toString()).doesNotContain(CODE);
        assertThat(payload.template().components().get(1).toString()).doesNotContain(CODE);
        assertThat(MESSAGE.toString()).doesNotContain(PHONE).doesNotContain(CODE);
        assertThat(new WhatsAppProperties(TOKEN, PHONE_NUMBER_ID, null, null, null, null, null).toString())
                .doesNotContain(TOKEN);
    }

    private static WhatsAppPhoneVerificationSender sender(String baseUrl, long timeoutMs) {
        var properties = new WhatsAppProperties(TOKEN, PHONE_NUMBER_ID, null, null, null, timeoutMs, baseUrl);
        return new WhatsAppPhoneVerificationSender(WebClient.builder().baseUrl(properties.apiBaseUrl()).build(),
                properties);
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
                .doesNotContain(TOKEN);
    }
}
