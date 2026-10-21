package com.axioma.loyalty.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Código enviado: lo que se responde al pedirlo, con el canal usado y los disponibles. */
public record SentVerification(UUID id, Instant expiresAt, VerificationChannel channel,
                               List<VerificationChannel> availableChannels) {

    public SentVerification {
        availableChannels = List.copyOf(availableChannels);
    }
}
