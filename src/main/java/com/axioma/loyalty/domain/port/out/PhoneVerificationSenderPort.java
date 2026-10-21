package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.VerificationMessage;
import reactor.core.publisher.Mono;

/**
 * Envío del código al teléfono por el canal del mensaje (WhatsApp o SMS). El adaptador se elige con
 * VERIFICATION_SENDER y VERIFICATION_ENABLED_CHANNELS (EnrollConfig).
 */
public interface PhoneVerificationSenderPort {

    /** VERIFICATION_UNAVAILABLE si este adaptador no puede enviar (antes de crear la verificación). */
    Mono<Void> ensureReady();

    /**
     * Falla con VerificationDeliveryException si no se pudo enviar (RECIPIENT_UNREACHABLE si el número no
     * puede recibir por ese canal). Nunca registra el teléfono, los tokens ni (fuera del modo log) el código.
     */
    Mono<Void> send(VerificationMessage message);
}
