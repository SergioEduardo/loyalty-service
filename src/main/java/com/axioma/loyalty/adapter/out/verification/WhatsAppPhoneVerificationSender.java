package com.axioma.loyalty.adapter.out.verification;

import com.axioma.loyalty.config.WhatsAppProperties;
import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.model.error.VerificationDeliveryException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

/**
 * VERIFICATION_SENDER/VERIFICATION_ENABLED_CHANNELS con whatsapp: WhatsApp Cloud API de Meta,
 * POST /{graphApiVersion}/{phoneNumberId}/messages con Bearer WHATSAPP_ACCESS_TOKEN y la plantilla de
 * autenticación (botón de copiar código): el código va en el parámetro del body y en el del botón
 * (sub_type "url", index "0"), como pide la documentación de Meta. Tope de WHATSAPP_TIMEOUT_MS por
 * intento. Un solo reintento, y solo si no se pudo conectar (conexión rechazada, tiempo de conexión
 * agotado, DNS): ahí el mensaje no salió. Un tiempo agotado después de conectar o cualquier respuesta HTTP
 * nunca se reintenta, porque el mensaje pudo haberse enviado. Error 131026 de Meta (el número no puede
 * recibir el mensaje) -> RECIPIENT_UNREACHABLE; cualquier otro fallo -> VERIFICATION_UNAVAILABLE. Una
 * respuesta 2xx solo significa que Meta aceptó el mensaje. Logs con tenantId, verificationId, intento,
 * estado HTTP, código de error de Meta y duración; nunca el teléfono, el código ni el token.
 */
@Slf4j
@RequiredArgsConstructor
public class WhatsAppPhoneVerificationSender implements PhoneVerificationSenderPort {

    static final int MAX_ATTEMPTS = 2;
    static final String RECIPIENT_UNREACHABLE_CODE = "131026";
    static final String INVALID_TOKEN_CODE = "190";

    private final WebClient webClient;
    private final WhatsAppProperties properties;

    record TemplateMessage(@JsonProperty("messaging_product") String messagingProduct,
                           @JsonProperty("recipient_type") String recipientType,
                           String to, String type, Template template) {

        static TemplateMessage authentication(String to, String templateName, String language, String code) {
            var parameters = List.of(new Parameter("text", code));
            return new TemplateMessage("whatsapp", "individual", to, "template",
                    new Template(templateName, new Language(language), List.of(
                            new Component("body", null, null, parameters),
                            new Component("button", "url", "0", parameters))));
        }

        @Override
        public String toString() {
            return "TemplateMessage[to=***, template=" + template.name() + "]";
        }
    }

    record Template(String name, Language language, List<Component> components) {

        @Override
        public String toString() {
            return "Template[name=" + name + ", components=***]";
        }
    }

    record Language(String code) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Component(String type, @JsonProperty("sub_type") String subType, String index,
                     List<Parameter> parameters) {

        @Override
        public String toString() {
            return "Component[type=" + type + ", parameters=***]";
        }
    }

    record Parameter(String type, String text) {

        @Override
        public String toString() {
            return "Parameter[type=" + type + ", text=***]";
        }
    }

    /** {"error": {"message", "type", "code", "error_subcode", "fbtrace_id"}}: solo se lee el código. */
    record GraphErrorBody(GraphError error) {
    }

    record GraphError(Long code) {
    }

    @Override
    public Mono<Void> ensureReady() {
        return Mono.empty();
    }

    @Override
    public Mono<Void> send(VerificationMessage message) {
        var payload = TemplateMessage.authentication(message.phone().value(), properties.templateName(),
                properties.templateLanguage(), message.code());
        return Mono.defer(() -> {
            long start = System.nanoTime();
            return attempt(payload, message, 1, start)
                    .onErrorResume(WhatsAppPhoneVerificationSender::connectionFailed,
                            e -> attempt(payload, message, 2, start))
                    .onErrorMap(e -> !(e instanceof LoyaltyException), e -> {
                        log.warn("WhatsApp verification send failed tenantId={} verificationId={} reason={} durationMs={}",
                                message.tenantId(), message.verificationId(), reason(e), elapsedMs(start));
                        return VerificationDeliveryException.unavailable(null);
                    });
        });
    }

    private Mono<Void> attempt(TemplateMessage payload, VerificationMessage message, int attempt, long start) {
        return webClient.post()
                .uri("/{version}/{phoneNumberId}/messages", properties.graphApiVersion(), properties.phoneNumberId())
                .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchangeToMono(response -> handle(response, message, attempt, start))
                .timeout(properties.timeout())
                .doOnError(WhatsAppPhoneVerificationSender::connectionFailed, e -> log.warn(
                        "WhatsApp connection failed tenantId={} verificationId={} attempt={}/{} reason={} durationMs={}",
                        message.tenantId(), message.verificationId(), attempt, MAX_ATTEMPTS, reason(e),
                        elapsedMs(start)));
    }

    private Mono<Void> handle(ClientResponse response, VerificationMessage message, int attempt, long start) {
        HttpStatusCode status = response.statusCode();
        if (status.is2xxSuccessful()) {
            return response.releaseBody().then(Mono.fromRunnable(() -> log.info(
                    "WhatsApp verification accepted tenantId={} verificationId={} attempt={} durationMs={}",
                    message.tenantId(), message.verificationId(), attempt, elapsedMs(start))));
        }
        return response.bodyToMono(GraphErrorBody.class)
                .onErrorResume(e -> Mono.empty())
                .mapNotNull(body -> body.error() == null || body.error().code() == null
                        ? null : String.valueOf(body.error().code()))
                .defaultIfEmpty("")
                .flatMap(code -> Mono.error(failure(status.value(), code, message)));
    }

    private static LoyaltyException failure(int status, String providerCode, VerificationMessage message) {
        String code = providerCode.matches("[0-9]{1,10}") ? providerCode : null;
        String shown = code == null ? "-" : code;
        if (RECIPIENT_UNREACHABLE_CODE.equals(code)) {
            log.info("WhatsApp recipient unreachable tenantId={} verificationId={} status={} providerCode={}",
                    message.tenantId(), message.verificationId(), status, shown);
            return VerificationDeliveryException.recipientUnreachable(code);
        }
        if (status == 401 || status == 403 || INVALID_TOKEN_CODE.equals(code)) {
            log.error("Authentication to WhatsApp Cloud API was rejected status={} providerCode={}", status, shown);
        } else if (status >= 400 && status < 500 && status != 429) {
            log.error("WhatsApp Cloud API rejected the message tenantId={} verificationId={} status={} providerCode={}",
                    message.tenantId(), message.verificationId(), status, shown);
        } else {
            log.warn("WhatsApp Cloud API failed tenantId={} verificationId={} status={} providerCode={}",
                    message.tenantId(), message.verificationId(), status, shown);
        }
        return VerificationDeliveryException.unavailable(code);
    }

    /** No hubo conexión: el mensaje no salió y reintentar no lo duplica. */
    static boolean connectionFailed(Throwable e) {
        if (!(e instanceof WebClientRequestException)) {
            return false;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    private static String reason(Throwable e) {
        return e instanceof TimeoutException ? "timeout" : e.getClass().getSimpleName();
    }

    private static long elapsedMs(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }
}
