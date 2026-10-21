package com.axioma.loyalty.application;

import static com.axioma.loyalty.application.AdminFixtures.TENANT_ID;
import static com.axioma.loyalty.application.AdminFixtures.hasCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.application.command.RequestPhoneVerificationCommand;
import com.axioma.loyalty.application.handler.ActiveTenantGuard;
import com.axioma.loyalty.application.handler.EnrollRateLimits;
import com.axioma.loyalty.application.handler.PhoneVerificationIssuer;
import com.axioma.loyalty.application.handler.RequestPhoneVerificationHandler;
import com.axioma.loyalty.application.validator.RequestPhoneVerificationValidator;
import com.axioma.loyalty.config.EnrollProperties;
import com.axioma.loyalty.domain.model.IssuedVerification;
import com.axioma.loyalty.domain.model.LoginThrottle;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.RecentRequests;
import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationChannelPolicy;
import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.VerificationDeliveryException;
import com.axioma.loyalty.domain.port.out.EnrollRateLimiter;
import com.axioma.loyalty.domain.port.out.PhoneVerificationRepository;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import com.axioma.loyalty.domain.port.out.VerificationCodeGenerator;
import com.axioma.loyalty.domain.port.out.VerificationCodeHasher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Canal de la solicitud, límites compartidos entre canales y traducción de los fallos de envío. */
@ExtendWith(OutputCaptureExtension.class)
class RequestPhoneVerificationHandlerTest {

    private static final String PHONE = "5512345678";
    private static final String E164 = "+525512345678";
    private static final String CODE = "654321";
    private static final UUID VERIFICATION_ID = UUID.randomUUID();
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-20T12:10:00Z");
    private static final List<VerificationChannel> BOTH = List.of(VerificationChannel.WHATSAPP, VerificationChannel.SMS);

    private final VerificationCodeHasher hasher = mock(VerificationCodeHasher.class);
    private final PhoneVerificationSenderPort sender = mock(PhoneVerificationSenderPort.class);
    private final EnrollRateLimiter limiter = mock(EnrollRateLimiter.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final VerificationCodeGenerator codes = mock(VerificationCodeGenerator.class);
    private final PhoneVerificationRepository verifications = mock(PhoneVerificationRepository.class);
    private final EnrollProperties properties =
            new EnrollProperties(3, 10, 60, 30, 10, 5, null, "whatsapp", "whatsapp,sms", null);

    @BeforeEach
    void stubs() {
        when(hasher.ensureReady()).thenReturn(Mono.empty());
        when(hasher.hash(any(), any(), anyString())).thenReturn("hash");
        when(sender.ensureReady()).thenReturn(Mono.empty());
        when(sender.send(any())).thenReturn(Mono.empty());
        when(limiter.hit(anyString(), anyInt())).thenReturn(Mono.just(new LoginThrottle(1, 3600)));
        when(tenants.findBySlug(new Slug("mi-cafe"))).thenReturn(Mono.just(AdminFixtures.tenant(true)));
        when(codes.generate()).thenReturn(CODE);
        when(verifications.lockPhone(any(), any())).thenReturn(Mono.empty());
        when(verifications.countRecent(any(), any(), anyInt())).thenReturn(Mono.just(new RecentRequests(0, 0)));
        when(verifications.create(any(), any(), anyString(), any(), anyInt()))
                .thenReturn(Mono.just(new IssuedVerification(VERIFICATION_ID, EXPIRES_AT)));
    }

    @Test
    void missingChannelUsesTheDefaultAndAnswersTheAvailableOnes() {
        StepVerifier.create(handler(new VerificationChannelPolicy(VerificationChannel.WHATSAPP, BOTH))
                        .handle(command(null)))
                .assertNext(sent -> {
                    assertThat(sent.id()).isEqualTo(VERIFICATION_ID);
                    assertThat(sent.expiresAt()).isEqualTo(EXPIRES_AT);
                    assertThat(sent.channel()).isEqualTo(VerificationChannel.WHATSAPP);
                    assertThat(sent.availableChannels()).containsExactly(VerificationChannel.WHATSAPP,
                            VerificationChannel.SMS);
                })
                .verifyComplete();

        var message = ArgumentCaptor.forClass(VerificationMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().channel()).isEqualTo(VerificationChannel.WHATSAPP);
        assertThat(message.getValue().validMinutes()).isEqualTo(10);
        assertThat(message.getValue().businessName()).isEqualTo("Mi Café");
        assertThat(message.getValue().phone()).isEqualTo(new PhoneE164(E164));
        assertThat(message.getValue().code()).isEqualTo(CODE);
        verify(verifications).create(eq(TENANT_ID), eq(new PhoneE164(E164)), eq("hash"),
                eq(VerificationChannel.WHATSAPP), eq(10));
    }

    @Test
    void requestedChannelIsUsed() {
        StepVerifier.create(handler(new VerificationChannelPolicy(VerificationChannel.WHATSAPP, BOTH))
                        .handle(command(" SMS ")))
                .assertNext(sent -> assertThat(sent.channel()).isEqualTo(VerificationChannel.SMS))
                .verifyComplete();
        var message = ArgumentCaptor.forClass(VerificationMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().channel()).isEqualTo(VerificationChannel.SMS);
        verify(verifications).create(any(), any(), anyString(), eq(VerificationChannel.SMS), anyInt());
    }

    @Test
    void channelNotAvailableIsRejectedBeforeAnyLimitOrLookup() {
        var onlyWhatsApp = new VerificationChannelPolicy(VerificationChannel.WHATSAPP,
                List.of(VerificationChannel.WHATSAPP));
        for (String channel : new String[]{"sms", "telegram"}) {
            StepVerifier.create(handler(onlyWhatsApp).handle(command(channel)))
                    .expectErrorSatisfies(hasCode(ErrorCode.CHANNEL_NOT_AVAILABLE))
                    .verify();
        }
        verifyNoInteractions(limiter, tenants, verifications, codes);
        verify(sender, never()).send(any());
    }

    @Test
    void invalidPhoneWinsOverInvalidChannel() {
        StepVerifier.create(handler(new VerificationChannelPolicy(VerificationChannel.WHATSAPP, BOTH))
                        .handle(new RequestPhoneVerificationCommand("mi-cafe", "123", "telegram", "203.0.113.9")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_PHONE))
                .verify();
    }

    @Test
    void phoneLimitIsSharedAcrossChannels() {
        // La base cuenta todas las verificaciones del teléfono, sin importar el canal.
        when(verifications.countRecent(any(), any(), anyInt())).thenReturn(
                Mono.just(new RecentRequests(0, 0)), Mono.just(new RecentRequests(1, 0)),
                Mono.just(new RecentRequests(2, 0)), Mono.just(new RecentRequests(3, 1800)));
        var handler = handler(new VerificationChannelPolicy(VerificationChannel.WHATSAPP, BOTH));

        StepVerifier.create(handler.handle(command("whatsapp"))).expectNextCount(1).verifyComplete();
        StepVerifier.create(handler.handle(command("sms"))).expectNextCount(1).verifyComplete();
        StepVerifier.create(handler.handle(command("whatsapp"))).expectNextCount(1).verifyComplete();
        StepVerifier.create(handler.handle(command("sms")))
                .expectErrorSatisfies(hasCode(ErrorCode.RATE_LIMITED))
                .verify();
        verify(verifications, times(4)).countRecent(TENANT_ID, new PhoneE164(E164), EnrollProperties.WINDOW_MINUTES);
        verify(verifications, times(3)).create(any(), any(), anyString(), any(), anyInt());
    }

    @Test
    void recipientUnreachablePassesThroughAndTheVerificationStillCounts(CapturedOutput output) {
        when(sender.send(any())).thenReturn(Mono.error(VerificationDeliveryException.recipientUnreachable("131026")));

        StepVerifier.create(handler(new VerificationChannelPolicy(VerificationChannel.WHATSAPP, BOTH))
                        .handle(command(null)))
                .expectErrorSatisfies(hasCode(ErrorCode.RECIPIENT_UNREACHABLE))
                .verify();
        verify(verifications).create(any(), any(), anyString(), any(), anyInt());
        assertThat(output.getOut()).contains("Verification code not sent tenantId=" + TENANT_ID + " verificationId="
                + VERIFICATION_ID + " channel=whatsapp error=VerificationDeliveryException providerCode=131026");
        assertThat(output.getAll()).doesNotContain(E164).doesNotContain(PHONE).doesNotContain(CODE);
    }

    @Test
    void anyOtherSendFailureIsUnavailable(CapturedOutput output) {
        when(sender.send(any())).thenReturn(Mono.error(new IllegalStateException("caído " + E164)));

        StepVerifier.create(handler(new VerificationChannelPolicy(VerificationChannel.WHATSAPP, BOTH))
                        .handle(command("sms")))
                .expectErrorSatisfies(hasCode(ErrorCode.VERIFICATION_UNAVAILABLE))
                .verify();
        assertThat(output.getOut()).contains("channel=sms error=IllegalStateException providerCode=-");
        assertThat(output.getAll()).doesNotContain(E164).doesNotContain(CODE);
    }

    private RequestPhoneVerificationHandler handler(VerificationChannelPolicy channels) {
        return new RequestPhoneVerificationHandler(new RequestPhoneVerificationValidator(), hasher, sender, channels,
                new EnrollRateLimits(limiter, properties), new ActiveTenantGuard(tenants), codes,
                new PhoneVerificationIssuer(verifications, properties), properties);
    }

    private static RequestPhoneVerificationCommand command(String channel) {
        return new RequestPhoneVerificationCommand("mi-cafe", PHONE, channel, "203.0.113.9");
    }
}
