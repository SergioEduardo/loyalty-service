package com.axioma.loyalty.domain.model;

import com.axioma.loyalty.domain.model.error.ChannelNotAvailableException;
import java.util.List;

/**
 * Canales habilitados (en el orden configurado) y el canal por defecto, que siempre es uno de ellos.
 * Sin canales (VERIFICATION_SENDER=disabled) ningún canal se resuelve.
 */
public record VerificationChannelPolicy(VerificationChannel defaultChannel, List<VerificationChannel> enabled) {

    public VerificationChannelPolicy {
        enabled = List.copyOf(enabled);
        if (defaultChannel == null ? !enabled.isEmpty() : !enabled.contains(defaultChannel)) {
            throw new IllegalArgumentException("El canal por defecto debe estar entre los canales habilitados");
        }
    }

    public static VerificationChannelPolicy none() {
        return new VerificationChannelPolicy(null, List.of());
    }

    /**
     * Canal para una solicitud: sin valor (null o vacío), el canal por defecto. Un canal desconocido o no
     * habilitado es CHANNEL_NOT_AVAILABLE, sin distinguir entre los dos casos; lleva los canales habilitados.
     */
    public VerificationChannel resolve(String requested) {
        if (requested == null || requested.isBlank()) {
            if (defaultChannel == null) {
                throw new ChannelNotAvailableException(enabled);
            }
            return defaultChannel;
        }
        return VerificationChannel.parse(requested)
                .filter(enabled::contains)
                .orElseThrow(() -> new ChannelNotAvailableException(enabled));
    }
}
