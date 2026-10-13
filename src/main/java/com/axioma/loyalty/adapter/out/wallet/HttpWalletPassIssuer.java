package com.axioma.loyalty.adapter.out.wallet;

import com.axioma.loyalty.config.WalletProperties;
import com.axioma.loyalty.domain.model.Card;
import com.axioma.loyalty.domain.model.IssuedPass;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.WalletPassIssuerPort;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * POST {WALLET_SERVICE_URL}/internal/passes con un ID token de Google de la cuenta de runtime
 * (audiencia WALLET_SERVICE_AUDIENCE). Todo, incluido obtener el token, dentro de
 * WALLET_ISSUE_TIMEOUT_MS. 409 TEMPLATE_NOT_READY -> TEMPLATE_NOT_READY; cualquier otro fallo ->
 * PASS_SERVICE_UNAVAILABLE. Los logs nunca llevan el token, el nombre ni el enlace del pase.
 */
@Slf4j
@RequiredArgsConstructor
public class HttpWalletPassIssuer implements WalletPassIssuerPort {

    static final String PATH = "/internal/passes";

    private final WebClient webClient;
    private final ServiceIdTokenSource idTokens;
    private final WalletProperties properties;

    record IssueRequest(UUID cardId, UUID tenantId, String holderName, int points) {

        @Override
        public String toString() {
            return "IssueRequest[cardId=" + cardId + ", tenantId=" + tenantId + ", holderName=***, points=" + points + "]";
        }
    }

    record IssueResponse(AppleLink apple) {
    }

    record AppleLink(String url, Instant expiresAt) {
    }

    record ErrorBody(String code) {
    }

    @Override
    public Mono<IssuedPass> issue(Card card) {
        var request = new IssueRequest(card.id(), card.tenantId(), card.holderName(), card.points());
        return Mono.fromCallable(() -> idTokens.idToken(properties.audience()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> !(e instanceof LoyaltyException), e -> {
                    log.error("Could not obtain an ID token for wallet-service ({})", e.getClass().getSimpleName());
                    return unavailable();
                })
                .flatMap(token -> webClient.post()
                        .uri(PATH)
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .exchangeToMono(response -> handle(response, card.tenantId())))
                .timeout(properties.issueTimeout())
                .onErrorMap(e -> !(e instanceof LoyaltyException), e -> {
                    log.warn("Wallet pass issue failed tenantId={} reason={}", card.tenantId(),
                            e instanceof TimeoutException ? "timeout" : e.getClass().getSimpleName());
                    return unavailable();
                });
    }

    private Mono<IssuedPass> handle(ClientResponse response, UUID tenantId) {
        HttpStatusCode status = response.statusCode();
        if (status.is2xxSuccessful()) {
            return response.bodyToMono(IssueResponse.class)
                    .filter(body -> body.apple() != null && body.apple().url() != null)
                    .map(body -> new IssuedPass(body.apple().url(), body.apple().expiresAt()))
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

    private static LoyaltyException unavailable() {
        return new LoyaltyException(ErrorCode.PASS_SERVICE_UNAVAILABLE);
    }
}
