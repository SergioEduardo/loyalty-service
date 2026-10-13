package com.axioma.loyalty.adapter.out.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.config.WalletProperties;
import com.axioma.loyalty.domain.model.Card;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/** Fallos que no necesitan servidor: puerto cerrado (red) y token que no se puede obtener. */
class HttpWalletPassIssuerTest {

    private static final Card CARD = new Card(UUID.randomUUID(), UUID.randomUUID(), "Ana", 0);

    @Test
    void networkErrorIsPassServiceUnavailable() throws IOException {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String url = "http://127.0.0.1:" + port;
        var issuer = new HttpWalletPassIssuer(WebClient.builder().baseUrl(url).build(), audience -> "token",
                new WalletProperties(url, null, 2000L));
        StepVerifier.create(issuer.issue(CARD))
                .expectErrorSatisfies(e -> assertThat(((LoyaltyException) e).getCode())
                        .isEqualTo(ErrorCode.PASS_SERVICE_UNAVAILABLE))
                .verify();
    }

    @Test
    void tokenFailureIsPassServiceUnavailable() {
        String url = "http://127.0.0.1:1";
        var issuer = new HttpWalletPassIssuer(WebClient.builder().baseUrl(url).build(), audience -> {
            throw new IOException("sin ADC");
        }, new WalletProperties(url, null, 2000L));
        StepVerifier.create(issuer.issue(CARD))
                .expectErrorSatisfies(e -> assertThat(((LoyaltyException) e).getCode())
                        .isEqualTo(ErrorCode.PASS_SERVICE_UNAVAILABLE))
                .verify();
    }
}
