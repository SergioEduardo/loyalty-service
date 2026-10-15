package com.axioma.loyalty.adapter.out.wallet;

import com.axioma.loyalty.config.WalletProperties;
import com.axioma.loyalty.domain.model.Card;
import com.axioma.loyalty.domain.model.IssuedPass;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.WalletPassIssuerPort;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * POST {WALLET_SERVICE_URL}/internal/passes con un ID token de Google de la cuenta de runtime
 * (audiencia WALLET_SERVICE_AUDIENCE). Obtener el token y cada intento tienen como tope
 * WALLET_ISSUE_TIMEOUT_MS. Un solo reintento, y solo si el intento se agotó o falló la conexión: el
 * wallet-service emite idempotente por cardId, así que repetir no duplica el pase. Una respuesta HTTP
 * (2xx malformado, 4xx o 5xx) nunca se reintenta. 409 TEMPLATE_NOT_READY -> TEMPLATE_NOT_READY;
 * cualquier otro fallo -> PASS_SERVICE_UNAVAILABLE. El enlace de Google es opcional: ausente (respuesta
 * anterior a Google Wallet), null o malformado deja googleUrl en null y nunca hace fallar la emisión.
 * Los logs llevan tenantId, intento, duración y si hubo enlace de Google; nunca el token, el nombre ni
 * los enlaces del pase (el de Google lleva un JWT firmado).
 */
@Slf4j
@RequiredArgsConstructor
public class HttpWalletPassIssuer implements WalletPassIssuerPort {

    static final String PATH = "/internal/passes";
    static final int MAX_ATTEMPTS = 2;

    private final WebClient webClient;
    private final ServiceIdTokenSource idTokens;
    private final WalletProperties properties;

    record IssueRequest(UUID cardId, UUID tenantId, String holderName, int points) {

        @Override
        public String toString() {
            return "IssueRequest[cardId=" + cardId + ", tenantId=" + tenantId + ", holderName=***, points=" + points + "]";
        }
    }

    /** google como JsonNode: una forma inesperada no debe invalidar la respuesta ni el pase de Apple. */
    record IssueResponse(AppleLink apple, JsonNode google) {

        @Override
        public String toString() {
            return "IssueResponse[apple=" + apple + ", google=" + (google == null ? "null" : "***") + "]";
        }
    }

    record AppleLink(String url, Instant expiresAt) {

        @Override
        public String toString() {
            return "AppleLink[url=***, expiresAt=" + expiresAt + "]";
        }
    }

    record ErrorBody(String code) {
    }

    @Override
    public Mono<IssuedPass> issue(Card card) {
        var request = new IssueRequest(card.id(), card.tenantId(), card.holderName(), card.points());
        return Mono.defer(() -> {
            long start = System.nanoTime();
            return idToken()
                    .flatMap(token -> attempt(token, request, 1, start)
                            .onErrorResume(HttpWalletPassIssuer::retryable, e -> attempt(token, request, 2, start)))
                    .onErrorMap(e -> !(e instanceof LoyaltyException), e -> {
                        log.warn("Wallet pass issue failed tenantId={} reason={} durationMs={}",
                                request.tenantId(), reason(e), elapsedMs(start));
                        return unavailable();
                    });
        });
    }

    private Mono<String> idToken() {
        return Mono.fromCallable(() -> idTokens.idToken(properties.audience()))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(properties.issueTimeout())
                .onErrorMap(e -> !(e instanceof LoyaltyException), e -> {
                    log.error("Could not obtain an ID token for wallet-service ({})", reason(e));
                    return unavailable();
                });
    }

    private Mono<IssuedPass> attempt(String token, IssueRequest request, int attempt, long start) {
        return webClient.post()
                .uri(PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchangeToMono(response -> handle(response, request.tenantId()))
                .timeout(properties.issueTimeout())
                .doOnNext(pass -> log.info("Wallet pass issued tenantId={} attempt={} google={} durationMs={}",
                        request.tenantId(), attempt, pass.hasGoogle(), elapsedMs(start)))
                .doOnError(HttpWalletPassIssuer::retryable, e -> log.warn(
                        "Wallet pass issue attempt failed tenantId={} attempt={}/{} reason={} timeoutMs={} durationMs={}",
                        request.tenantId(), attempt, MAX_ATTEMPTS, reason(e), properties.issueTimeoutMs(),
                        elapsedMs(start)));
    }

    /** Se agotó el intento o no hubo respuesta HTTP (conexión rechazada, cortada, DNS). */
    static boolean retryable(Throwable e) {
        return e instanceof TimeoutException || e instanceof WebClientRequestException;
    }

    private static String reason(Throwable e) {
        return e instanceof TimeoutException ? "timeout" : e.getClass().getSimpleName();
    }

    private static long elapsedMs(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    private Mono<IssuedPass> handle(ClientResponse response, UUID tenantId) {
        HttpStatusCode status = response.statusCode();
        if (status.is2xxSuccessful()) {
            return response.bodyToMono(IssueResponse.class)
                    .filter(body -> body.apple() != null && body.apple().url() != null)
                    .map(body -> new IssuedPass(body.apple().url(), body.apple().expiresAt(),
                            googleUrl(body.google(), tenantId)))
                    .switchIfEmpty(Mono.defer(() -> {
                        log.error("Wallet-service returned a pass response without an Apple link tenantId={}", tenantId);
                        return Mono.error(unavailable());
                    }));
        }
        if (status.value() == HttpStatus.UNAUTHORIZED.value() || status.value() == HttpStatus.FORBIDDEN.value()) {
            log.error("Authentication to wallet-service was rejected");
            return response.releaseBody().then(Mono.error(unavailable()));
        }
        if (status.value() == HttpStatus.CONFLICT.value()) {
            return response.bodyToMono(ErrorBody.class)
                    .onErrorResume(e -> Mono.empty())
                    .map(ErrorBody::code)
                    .defaultIfEmpty("")
                    .flatMap(code -> {
                        if ("TEMPLATE_NOT_READY".equals(code)) {
                            log.info("Wallet template not ready tenantId={}", tenantId);
                            return Mono.error(new LoyaltyException(ErrorCode.TEMPLATE_NOT_READY));
                        }
                        log.error("Wallet-service rejected the pass status=409 code={} tenantId={}",
                                code.matches("[A-Z_]{1,40}") ? code : "?", tenantId);
                        return Mono.error(unavailable());
                    });
        }
        if (status.is4xxClientError()) {
            log.error("Wallet-service rejected the pass request status={} tenantId={}", status.value(), tenantId);
        } else {
            log.warn("Wallet-service failed status={} tenantId={}", status.value(), tenantId);
        }
        return response.releaseBody().then(Mono.error(unavailable()));
    }

    /** {url: "https://..."} o null. Ausente, null o {url: null} es "sin Google"; otra forma se avisa sin el valor. */
    static String googleUrl(JsonNode google, UUID tenantId) {
        if (google == null || google.isNull() || google.isMissingNode()) {
            return null;
        }
        JsonNode url = google.path("url");
        if (google.isObject() && (url.isNull() || url.isMissingNode())) {
            return null;
        }
        if (url.isTextual() && url.textValue().startsWith("https://")) {
            return url.textValue();
        }
        log.warn("Wallet-service returned an unusable Google link; continuing without it tenantId={}", tenantId);
        return null;
    }

    private static LoyaltyException unavailable() {
        return new LoyaltyException(ErrorCode.PASS_SERVICE_UNAVAILABLE);
    }
}
