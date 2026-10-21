package com.axioma.loyalty.adapter.out.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ChannelRoutingPhoneVerificationSenderTest {

    private final PhoneVerificationSenderPort whatsApp = mock(PhoneVerificationSenderPort.class);
    private final PhoneVerificationSenderPort sms = mock(PhoneVerificationSenderPort.class);

    @Test
    void eachMessageGoesToTheAdapterOfItsChannel() {
        when(sms.send(any())).thenReturn(Mono.empty());
        var router = new ChannelRoutingPhoneVerificationSender(
                Map.of(VerificationChannel.WHATSAPP, whatsApp, VerificationChannel.SMS, sms));

        StepVerifier.create(router.ensureReady()).verifyComplete();
        var message = message(VerificationChannel.SMS);
        StepVerifier.create(router.send(message)).verifyComplete();
        verify(sms).send(message);
        verifyNoInteractions(whatsApp);
    }

    @Test
    void channelWithoutAdapterIsUnavailable() {
        var router = new ChannelRoutingPhoneVerificationSender(Map.of(VerificationChannel.WHATSAPP, whatsApp));
        StepVerifier.create(router.send(message(VerificationChannel.SMS)))
                .expectErrorSatisfies(e -> assertThat(((LoyaltyException) e).getCode())
                        .isEqualTo(ErrorCode.VERIFICATION_UNAVAILABLE))
                .verify();
        verifyNoInteractions(whatsApp);
    }

    private static VerificationMessage message(VerificationChannel channel) {
        return new VerificationMessage(UUID.randomUUID(), "Café", UUID.randomUUID(), new PhoneE164("+525512345678"),
                "123456", channel, 10);
    }
}
