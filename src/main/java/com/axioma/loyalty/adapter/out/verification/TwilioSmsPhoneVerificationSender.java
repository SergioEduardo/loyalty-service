package com.axioma.loyalty.adapter.out.verification;

import com.axioma.loyalty.config.TwilioProperties;
import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.model.error.VerificationDeliveryException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * VERIFICATION_SENDER/VERIFICATION_ENABLED_CHANNELS con sms: Twilio Programmable Messaging,
 * POST /2010-04-01/Accounts/{TWILIO_ACCOUNT_SID}/Messages.json (formulario, autenticación básica
 * AccountSid:AuthToken) con To (E.164), MessagingServiceSid (preferido) o From, y Body (SmsVerificationText).
 * No usa Twilio Verify: el código se genera y se valida aquí. Un solo intento con tope TWILIO_TIMEOUT_MS y
 * sin reintentos, para no duplicar mensajes. Error 21211 de Twilio (número "To" inválido) ->
 * RECIPIENT_UNREACHABLE; cualquier otro fallo -> VERIFICATION_UNAVAILABLE. Un 201 solo significa que
 * Twilio aceptó el mensaje (no que se entregó). Logs con tenantId, verificationId, sid del mensaje,
 * estado HTTP, código de error de Twilio y duración; nunca el teléfono, el código, el texto ni el token.
 */
@Slf4j
@RequiredArgsConstructor
public class TwilioSmsPhoneVerificationSender implements PhoneVerificationSenderPort {

    static final String PATH = "/2010-04-01/Accounts/{accountSid}/Messages.json";
    static final String RECIPIENT_UNREACHABLE_CODE = "21211";
    static final String AUTHENTICATION_ERROR_CODE = "20003";

    private final WebClient webClient;
    private final TwilioProperties properties;

    /** Respuesta 201: solo el sid y el estado (queued/accepted). */
    record CreatedMessage(String sid, String status) {
    }

    /** {"code", "message", "more_info", "status"}: solo se lee el código. */
    record TwilioError(Long code) {
    }

    @Override
    public Mono<Void> ensureReady() {
        return Mono.empty();
    }

    @Override
    public Mono<Void> send(VerificationMessage message) {
        return Mono.defer(() -> {
            long start = System.nanoTime();
            return webClient.post()
                    .uri(PATH, properties.accountSid())
                    .headers(headers -> headers.setBasicAuth(properties.accountSid(), properties.authToken()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData(form(message)))
                    .exchangeToMono(response -> handle(response, message, start))
                    .timeout(properties.timeout())
                    .onErrorMap(e -> !(e instanceof LoyaltyException), e -> {
                        log.warn("SMS verification send failed tenantId={} verificationId={} reason={} durationMs={}",
                                message.tenantId(), message.verificationId(), reason(e), elapsedMs(start));
                        return VerificationDeliveryException.unavailable(null);
                    });
        });
    }

    private MultiValueMap<String, String> form(VerificationMessage message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", message.phone().value());
        if (properties.messagingServiceSid() != null) {
            form.add("MessagingServiceSid", properties.messagingServiceSid());
        } else {
            form.add("From", properties.from());
        }
        form.add("Body", SmsVerificationText.of(message.businessName(), message.code(), message.validMinutes()));
        return form;
    }

    private Mono<Void> handle(ClientResponse response, VerificationMessage message, long start) {
        HttpStatusCode status = response.statusCode();
        if (status.is2xxSuccessful()) {
            return response.bodyToMono(CreatedMessage.class)
                    .onErrorResume(e -> Mono.empty())
                    .defaultIfEmpty(new CreatedMessage(null, null))
                    .doOnNext(created -> log.info(
                            "SMS verification accepted by Twilio tenantId={} verificationId={} sid={} status={} durationMs={}",
                            message.tenantId(), message.verificationId(), safe(created.sid(), "SM[0-9a-fA-F]{32}"),
                            safe(created.status(), "[a-z_]{1,20}"), elapsedMs(start)))
                    .then();
        }
        return response.bodyToMono(TwilioError.class)
                .onErrorResume(e -> Mono.empty())
                .mapNotNull(body -> body.code() == null ? null : String.valueOf(body.code()))
                .defaultIfEmpty("")
                .flatMap(code -> Mono.error(failure(status.value(), code, message)));
    }

    private static LoyaltyException failure(int status, String providerCode, VerificationMessage message) {
        String code = providerCode.matches("[0-9]{1,10}") ? providerCode : null;
        String shown = code == null ? "-" : code;
        if (RECIPIENT_UNREACHABLE_CODE.equals(code)) {
            log.info("SMS recipient unreachable tenantId={} verificationId={} status={} providerCode={}",
                    message.tenantId(), message.verificationId(), status, shown);
            return VerificationDeliveryException.recipientUnreachable(code);
        }
        if (status == 401 || status == 403 || AUTHENTICATION_ERROR_CODE.equals(code)) {
            log.error("Authentication to Twilio was rejected status={} providerCode={}", status, shown);
        } else if (status >= 400 && status < 500 && status != 429) {
            log.error("Twilio rejected the message tenantId={} verificationId={} status={} providerCode={}",
                    message.tenantId(), message.verificationId(), status, shown);
        } else {
            log.warn("Twilio failed tenantId={} verificationId={} status={} providerCode={}",
                    message.tenantId(), message.verificationId(), status, shown);
        }
        return VerificationDeliveryException.unavailable(code);
    }

    private static String safe(String value, String pattern) {
        return value != null && value.matches(pattern) ? value : "?";
    }

    private static String reason(Throwable e) {
        return e instanceof TimeoutException ? "timeout" : e.getClass().getSimpleName();
    }

    private static long elapsedMs(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }
}
