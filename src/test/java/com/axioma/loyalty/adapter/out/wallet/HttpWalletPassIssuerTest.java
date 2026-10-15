package com.axioma.loyalty.adapter.out.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.config.WalletProperties;
import com.axioma.loyalty.domain.model.Card;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.support.FakeWalletService;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/** Tope por intento, un reintento solo por tiempo agotado o conexión, y logs sin datos personales. */
@ExtendWith(OutputCaptureExtension.class)
class HttpWalletPassIssuerTest {

    private static final String HOLDER = "Ana Pérez";
    private static final String TOKEN = "id-token-de-prueba";
    private static final Card CARD = new Card(UUID.randomUUID(), UUID.randomUUID(), HOLDER, 0);
    private static final FakeWalletService WALLET = new FakeWalletService();

    @AfterAll
    static void stopWallet() {
        WALLET.close();
    }

    @BeforeEach
    void reset() {
        WALLET.mode(FakeWalletService.Mode.OK);
        WALLET.google(FakeWalletService.Google.NULL);
        WALLET.received().clear();
    }

    @Test
    void successLogsAttemptAndDuration(CapturedOutput output) {
        StepVerifier.create(issuer(WALLET.baseUrl(), 2000L).issue(CARD))
                .assertNext(pass -> assertThat(pass.appleUrl()).isEqualTo(FakeWalletService.linkFor(CARD.id().toString())))
                .verifyComplete();
        assertThat(WALLET.received()).hasSize(1);
        assertThat(output.getOut())
                .containsPattern("INFO .*Wallet pass issued tenantId=" + CARD.tenantId()
                        + " attempt=1 google=false durationMs=\\d+");
        assertNoPersonalData(output);
    }

    @Test
    void googleLinkIsPropagatedWhenPresent(CapturedOutput output) {
        WALLET.google(FakeWalletService.Google.PRESENT);
        StepVerifier.create(issuer(WALLET.baseUrl(), 2000L).issue(CARD))
                .assertNext(pass -> {
                    assertThat(pass.appleUrl()).isEqualTo(FakeWalletService.linkFor(CARD.id().toString()));
                    assertThat(pass.googleUrl()).isEqualTo(FakeWalletService.googleLinkFor(CARD.id().toString()));
                    assertThat(pass.toString()).doesNotContain("pay.google.com", "wallet.test");
                })
                .verifyComplete();
        assertThat(output.getOut()).containsPattern("INFO .*Wallet pass issued tenantId=" + CARD.tenantId()
                + " attempt=1 google=true durationMs=\\d+");
        assertNoPersonalData(output);
    }

    @Test
    void googleNullAbsentOrMalformedStillIssuesTheApplePass(CapturedOutput output) {
        for (var google : new FakeWalletService.Google[] {
                FakeWalletService.Google.NULL, FakeWalletService.Google.ABSENT, FakeWalletService.Google.MALFORMED}) {
            reset();
            WALLET.google(google);
            StepVerifier.create(issuer(WALLET.baseUrl(), 2000L).issue(CARD))
                    .assertNext(pass -> {
                        assertThat(pass.appleUrl()).as(google.name())
                                .isEqualTo(FakeWalletService.linkFor(CARD.id().toString()));
                        assertThat(pass.googleUrl()).as(google.name()).isNull();
                        assertThat(pass.hasGoogle()).as(google.name()).isFalse();
                    })
                    .verifyComplete();
            assertThat(WALLET.received()).as(google.name()).hasSize(1);
        }
        assertThat(output.getOut())
                .contains("Wallet-service returned an unusable Google link; continuing without it tenantId=" + CARD.tenantId())
                .doesNotContain("PASS_SERVICE_UNAVAILABLE");
        assertNoPersonalData(output);
    }

    @Test
    void timedOutAttemptIsRetriedOnceAndRecovers(CapturedOutput output) {
        WALLET.slowMillis(1500);
        WALLET.mode(FakeWalletService.Mode.SLOW_ONCE);
        StepVerifier.create(issuer(WALLET.baseUrl(), 500L).issue(CARD))
                .assertNext(pass -> assertThat(pass.appleUrl()).isEqualTo(FakeWalletService.linkFor(CARD.id().toString())))
                .verifyComplete();
        assertThat(WALLET.received()).hasSize(2);
        assertThat(WALLET.received().get(1).body()).isEqualTo(WALLET.received().get(0).body());
        assertThat(output.getOut())
                .containsPattern("WARN .*Wallet pass issue attempt failed tenantId=" + CARD.tenantId()
                        + " attempt=1/2 reason=timeout timeoutMs=500 durationMs=\\d+")
                .containsPattern("INFO .*Wallet pass issued tenantId=" + CARD.tenantId()
                        + " attempt=2 google=false durationMs=\\d+");
        assertNoPersonalData(output);
    }

    @Test
    void twoTimeoutsArePassServiceUnavailable(CapturedOutput output) {
        WALLET.slowMillis(1500);
        WALLET.mode(FakeWalletService.Mode.SLOW);
        long start = System.nanoTime();
        StepVerifier.create(issuer(WALLET.baseUrl(), 400L).issue(CARD))
                .expectErrorSatisfies(HttpWalletPassIssuerTest::assertUnavailable)
                .verify();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1400));
        assertThat(WALLET.received()).hasSize(2);
        assertThat(output.getOut())
                .contains("attempt=1/2 reason=timeout", "attempt=2/2 reason=timeout")
                .containsPattern("WARN .*Wallet pass issue failed tenantId=" + CARD.tenantId()
                        + " reason=timeout durationMs=\\d+");
        assertNoPersonalData(output);
    }

    @Test
    void connectionErrorIsRetriedOnceThenPassServiceUnavailable(CapturedOutput output) throws IOException {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        StepVerifier.create(issuer("http://127.0.0.1:" + port, 2000L).issue(CARD))
                .expectErrorSatisfies(HttpWalletPassIssuerTest::assertUnavailable)
                .verify();
        assertThat(output.getOut())
                .contains("attempt=1/2 reason=WebClientRequestException", "attempt=2/2 reason=WebClientRequestException")
                .contains("Wallet pass issue failed tenantId=" + CARD.tenantId() + " reason=WebClientRequestException");
    }

    @Test
    void httpErrorResponsesAreNeverRetried() {
        for (var mode : new FakeWalletService.Mode[] {
                FakeWalletService.Mode.UNAUTHORIZED, FakeWalletService.Mode.DOWN}) {
            reset();
            WALLET.mode(mode);
            StepVerifier.create(issuer(WALLET.baseUrl(), 2000L).issue(CARD))
                    .expectErrorSatisfies(HttpWalletPassIssuerTest::assertUnavailable)
                    .verify();
            assertThat(WALLET.received()).as(mode.name()).hasSize(1);
        }
        reset();
        WALLET.mode(FakeWalletService.Mode.TEMPLATE_NOT_READY);
        StepVerifier.create(issuer(WALLET.baseUrl(), 2000L).issue(CARD))
                .expectErrorSatisfies(e -> assertThat(((LoyaltyException) e).getCode())
                        .isEqualTo(ErrorCode.TEMPLATE_NOT_READY))
                .verify();
        assertThat(WALLET.received()).hasSize(1);
    }

    @Test
    void tokenFailureIsPassServiceUnavailableWithoutCallingTheService() {
        var issuer = new HttpWalletPassIssuer(WebClient.builder().baseUrl(WALLET.baseUrl()).build(), audience -> {
            throw new IOException("sin ADC");
        }, new WalletProperties(WALLET.baseUrl(), null, 2000L));
        StepVerifier.create(issuer.issue(CARD))
                .expectErrorSatisfies(HttpWalletPassIssuerTest::assertUnavailable)
                .verify();
        assertThat(WALLET.received()).isEmpty();
    }

    private static HttpWalletPassIssuer issuer(String url, long timeoutMs) {
        return new HttpWalletPassIssuer(WebClient.builder().baseUrl(url).build(), audience -> TOKEN,
                new WalletProperties(url, null, timeoutMs));
    }

    private static void assertUnavailable(Throwable e) {
        assertThat(((LoyaltyException) e).getCode()).isEqualTo(ErrorCode.PASS_SERVICE_UNAVAILABLE);
    }

    private static void assertNoPersonalData(CapturedOutput output) {
        assertThat(output.getOut())
                .doesNotContain(HOLDER, TOKEN, "wallet.test/passes", "pay.google.com", "jwt-de-prueba", CARD.id().toString());
    }
}
