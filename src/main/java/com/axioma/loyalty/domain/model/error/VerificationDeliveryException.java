package com.axioma.loyalty.domain.model.error;

import com.axioma.loyalty.domain.model.VerificationChannel;
import java.util.List;

/**
 * El proveedor no entregó el código. RECIPIENT_UNREACHABLE: el número no puede recibir por ese canal
 * (400, la persona puede probar otro); VERIFICATION_UNAVAILABLE: cualquier otro fallo (503).
 * providerCode es el código numérico de error del proveedor (o null) para correlacionar en los logs;
 * nunca llega a la respuesta. El canal intentado y los habilitados solo los agrega withChannels.
 */
public class VerificationDeliveryException extends LoyaltyException implements ChannelAlternatives {

    private final String providerCode;
    private final VerificationChannel attemptedChannel;
    private final List<VerificationChannel> availableChannels;

    private VerificationDeliveryException(ErrorCode code, String providerCode, VerificationChannel attemptedChannel,
                                          List<VerificationChannel> availableChannels) {
        super(code);
        this.providerCode = providerCode;
        this.attemptedChannel = attemptedChannel;
        this.availableChannels = availableChannels == null ? null : List.copyOf(availableChannels);
    }

    public static VerificationDeliveryException recipientUnreachable(String providerCode) {
        return new VerificationDeliveryException(ErrorCode.RECIPIENT_UNREACHABLE, providerCode, null, null);
    }

    public static VerificationDeliveryException unavailable(String providerCode) {
        return new VerificationDeliveryException(ErrorCode.VERIFICATION_UNAVAILABLE, providerCode, null, null);
    }

    /** El mismo error (código y providerCode) con el canal intentado y los habilitados. */
    public VerificationDeliveryException withChannels(VerificationChannel attempted,
                                                      List<VerificationChannel> available) {
        return new VerificationDeliveryException(getCode(), providerCode, attempted, available);
    }

    public String getProviderCode() {
        return providerCode;
    }

    @Override
    public VerificationChannel getAttemptedChannel() {
        return attemptedChannel;
    }

    @Override
    public List<VerificationChannel> getAvailableChannels() {
        return availableChannels;
    }
}
