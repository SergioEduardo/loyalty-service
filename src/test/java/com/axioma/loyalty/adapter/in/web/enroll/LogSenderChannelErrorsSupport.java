package com.axioma.loyalty.adapter.in.web.enroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationChannelPolicy;
import com.axioma.loyalty.domain.model.error.VerificationDeliveryException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import reactor.core.publisher.Mono;

/**
 * Con VERIFICATION_SENDER=log (APP_ENV=dev), CHANNEL_NOT_AVAILABLE y RECIPIENT_UNREACHABLE responden
 * availableChannels igual a la lista efectiva de canales (la de la política que arma EnrollConfig). El
 * adaptador de log nunca falla: RECIPIENT_UNREACHABLE se simula con un spy sobre el adaptador real.
 */
@ExtendWith(OutputCaptureExtension.class)
abstract class LogSenderChannelErrorsSupport extends EnrollmentItSupport {

    @MockitoSpyBean
    PhoneVerificationSenderPort sender;

    @Autowired
    VerificationChannelPolicy channels;

    /** La lista efectiva esperada para la configuración de la subclase, en el orden configurado. */
    abstract List<String> expectedChannels();

    /** Un canal que esta configuración no tiene habilitado. */
    abstract String channelNotEnabled();

    @Test
    void effectiveChannelsAreTheExpectedOnes() {
        assertThat(channels.enabled().stream().map(VerificationChannel::value).toList()).isEqualTo(expectedChannels());
    }

    @Test
    void channelNotAvailableListsTheEffectiveChannelsWithoutAChannel(CapturedOutput output) {
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        requestCode(slug, freshNationalPhone(), freshIp(), channelNotEnabled()).expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CHANNEL_NOT_AVAILABLE")
                .jsonPath("$.message").isEqualTo("Ese medio de envío no está disponible.")
                .jsonPath("$.availableChannels").isEqualTo(expectedChannels())
                .jsonPath("$.channel").doesNotExist();
        assertThat(count("SELECT count(*) FROM loyalty.phone_verifications WHERE tenant_id = :t", tenantId)).isZero();
        assertThat(output.getAll()).doesNotContain("Verification code (VERIFICATION_SENDER=log) tenantId=" + tenantId);
    }

    @Test
    void recipientUnreachableNamesTheAttemptedChannelAndListsTheEffectiveOnes() {
        doReturn(Mono.error(VerificationDeliveryException.recipientUnreachable("21211"))).when(sender).send(any());
        String slug = newSlug();
        UUID tenantId = createTenant(slug);
        for (String channel : expectedChannels()) {
            requestCode(slug, freshNationalPhone(), freshIp(), channel).expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("RECIPIENT_UNREACHABLE")
                    .jsonPath("$.channel").isEqualTo(channel)
                    .jsonPath("$.availableChannels").isEqualTo(expectedChannels());
        }
        // Sin canal: el de por defecto, que en modo log es el primero de la lista efectiva.
        requestCode(slug, freshNationalPhone(), freshIp(), null).expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.channel").isEqualTo(expectedChannels().get(0))
                .jsonPath("$.availableChannels").isEqualTo(expectedChannels());
        // Cada intento fallido creó su verificación (cuenta para los límites).
        assertThat(count("SELECT count(*) FROM loyalty.phone_verifications WHERE tenant_id = :t", tenantId))
                .isEqualTo(expectedChannels().size() + 1L);
    }
}
